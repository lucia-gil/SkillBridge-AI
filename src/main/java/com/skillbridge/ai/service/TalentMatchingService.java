package com.skillbridge.ai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillbridge.ai.dto.*;
import com.skillbridge.ai.model.*;
import com.skillbridge.ai.repository.*;
import com.skillbridge.ai.util.OperacionInvalidaException;
import com.skillbridge.ai.util.Roles;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TalentMatchingService {
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ProyectoRepository proyectoRepository;
    private final PerfilRepository perfilRepository;
    private final AsignacionRepository asignacionRepository;
    private final PerfilHabilidadRepository perfilHabilidadRepository;
    private final ProyectoHabilidadRequeridaRepository requeridaRepository;
    private final HabilidadRepository habilidadRepository;
    private final PropuestaAsignacionRepository propuestaRepository;
    private final ConfiguracionService configuracionService;
    private final ProyectoService proyectoService;
    private final NotificacionService notificacionService;
    private final AuditoriaService auditoriaService;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<TalentMatchingService> selfProvider;
    private final DemandaHabilidadesService demandaService;

    public TalentMatchingService(ProyectoRepository proyectoRepository, PerfilRepository perfilRepository,
            AsignacionRepository asignacionRepository, PerfilHabilidadRepository perfilHabilidadRepository,
            ProyectoHabilidadRequeridaRepository requeridaRepository, HabilidadRepository habilidadRepository,
            PropuestaAsignacionRepository propuestaRepository, ConfiguracionService configuracionService,
            ProyectoService proyectoService, NotificacionService notificacionService,
            AuditoriaService auditoriaService, ObjectMapper objectMapper,
            ObjectProvider<TalentMatchingService> selfProvider, DemandaHabilidadesService demandaService) {
        this.proyectoRepository = proyectoRepository; this.perfilRepository = perfilRepository;
        this.asignacionRepository = asignacionRepository; this.perfilHabilidadRepository = perfilHabilidadRepository;
        this.requeridaRepository = requeridaRepository; this.habilidadRepository = habilidadRepository;
        this.propuestaRepository = propuestaRepository; this.configuracionService = configuracionService;
        this.proyectoService = proyectoService; this.notificacionService = notificacionService;
        this.auditoriaService = auditoriaService; this.objectMapper = objectMapper;
        this.selfProvider = selfProvider;
        this.demandaService = demandaService;
    }

    @Transactional(readOnly = true)
    public List<MatchingProyectoOpcion> proyectosDelPm(Long pmPerfilId) {
        return asignacionRepository.listarProyectosGestionados(pmPerfilId).stream()
                .filter(ProyectoService::admiteNuevosIntegrantes)
                .map(p -> new MatchingProyectoOpcion(p.getId(), p.getNombre(), vacantes(p)))
                .filter(p -> p.vacantes() > 0)
                .toList();
    }

    public int[] pesosConfigurados() {
        var cfg = configuracionService.obtenerMapa();
        return new int[]{entero(configuracionService.valor(cfg, "peso_matching_habilidades", "50"), 50),
                entero(configuracionService.valor(cfg, "peso_matching_experiencia", "30"), 30),
                entero(configuracionService.valor(cfg, "peso_matching_disponibilidad", "20"), 20)};
    }

    @Transactional(readOnly = true)
    public List<MatchingCandidato> calcular(Long pmPerfilId, Long proyectoId, int dedicacion, int ph, int pe, int pd) {
        return calcularTodos(pmPerfilId, proyectoId, dedicacion, ph, pe, pd).stream().limit(20).toList();
    }

    /** Ranking completo con los pesos configurados (para validar una propuesta manual). */
    private List<MatchingCandidato> calcularSinLimite(Long pmPerfilId, Long proyectoId, int dedicacion, int[] pesos) {
        return calcularTodos(pmPerfilId, proyectoId, dedicacion, pesos[0], pesos[1], pesos[2]);
    }

    private List<MatchingCandidato> calcularTodos(Long pmPerfilId, Long proyectoId, int dedicacion, int ph, int pe, int pd) {
        Proyecto proyecto = exigirProyectoDelPm(pmPerfilId, proyectoId);
        if (dedicacion < 1 || dedicacion > 100) throw new OperacionInvalidaException("La dedicación debe estar entre 1% y 100%.");
        if (vacantes(proyecto) < 1) throw new OperacionInvalidaException("El proyecto ya no tiene vacantes disponibles.");
        validarPesos(ph, pe, pd);
        return rankear(proyecto, dedicacion, ph, pe, pd);
    }

    /**
     * Candidatos sugeridos para la vista de detalle del proyecto (PM o RM):
     * mismo ranking que AI Talent Matching, con los pesos configurados y una
     * dedicación de referencia. Vacío si el proyecto no admite integrantes o
     * ya no tiene vacantes.
     */
    @Transactional(readOnly = true)
    public List<MatchingCandidato> sugeridosPara(Long proyectoId, int dedicacion, int limite) {
        Proyecto proyecto = proyectoRepository.findById(proyectoId).orElse(null);
        if (proyecto == null || !ProyectoService.admiteNuevosIntegrantes(proyecto) || vacantes(proyecto) < 1) return List.of();
        int[] pesos = pesosConfigurados();
        return rankear(proyecto, dedicacion, pesos[0], pesos[1], pesos[2]).stream()
                .filter(MatchingCandidato::isCargaCompatible).limit(limite).toList();
    }

    /**
     * Ranking para AI Talent Matching con IA (MatchingIaService): pesos
     * configurados y, si la petición en lenguaje natural pidió habilidades
     * concretas, esas reemplazan a las del proyecto. Solo candidatos cuya
     * carga quede dentro del límite.
     */
    @Transactional(readOnly = true)
    public List<MatchingCandidato> rankingParaIa(Long proyectoId, int dedicacion, Map<Long, Integer> requisitosPedidos, int limite) {
        Proyecto proyecto = proyectoRepository.findById(proyectoId).orElseThrow(() -> new OperacionInvalidaException("El proyecto no existe."));
        if (!ProyectoService.admiteNuevosIntegrantes(proyecto)) throw new OperacionInvalidaException("\"" + proyecto.getNombre() + "\" no admite nuevos integrantes en su estado actual.");
        if (vacantes(proyecto) < 1) throw new OperacionInvalidaException("\"" + proyecto.getNombre() + "\" ya no tiene vacantes disponibles.");
        if (dedicacion < 1 || dedicacion > 100) throw new OperacionInvalidaException("La dedicación debe estar entre 1% y 100%.");
        int[] pesos = pesosConfigurados();
        return rankear(proyecto, dedicacion, pesos[0], pesos[1], pesos[2], requisitosPedidos).stream()
                .filter(MatchingCandidato::isCargaCompatible).limit(limite).toList();
    }

    /** Vacantes libres del proyecto (colaboradores requeridos menos los activos). */
    @Transactional(readOnly = true)
    public long vacantesDe(Long proyectoId) {
        return proyectoRepository.findById(proyectoId).map(this::vacantes).orElse(0L);
    }

    private List<MatchingCandidato> rankear(Proyecto proyecto, int dedicacion, int ph, int pe, int pd) {
        return rankear(proyecto, dedicacion, ph, pe, pd, null);
    }

    private List<MatchingCandidato> rankear(Proyecto proyecto, int dedicacion, int ph, int pe, int pd, Map<Long, Integer> requisitosPedidos) {
        Long proyectoId = proyecto.getId();
        var cfg = configuracionService.obtenerMapa();
        int limiteCarga = proyectoService.limiteCargaConfigurado();
        int maximoProyectos = entero(configuracionService.valor(cfg, "maximo_proyectos_simultaneos", "3"), 3);

        Map<Long, Habilidad> habilidades = habilidadRepository.findAll().stream()
                .collect(Collectors.toMap(Habilidad::getId, Function.identity()));
        Map<Long, Integer> requisitos = requisitosPedidos != null && !requisitosPedidos.isEmpty()
                ? new HashMap<>(requisitosPedidos) : requisitos(proyecto, habilidades);
        Set<Long> equipo = asignacionRepository.listarEquipoDeProyecto(proyectoId, "activa").stream()
                .map(Asignacion::getPerfilId).collect(Collectors.toSet());

        List<MatchingCandidato> resultado = new ArrayList<>();
        for (Perfil perfil : perfilRepository.listarActivosConUsuario()) {
            if (!ProyectoService.esMiembroAsignable(perfil) || equipo.contains(perfil.getId())) continue;
            if (asignacionRepository.contarProyectosVigentesDePerfil(perfil.getId()) >= maximoProyectos) continue;
            Map<Long, Integer> niveles = perfilHabilidadRepository.findById_PerfilId(perfil.getId()).stream()
                    .collect(Collectors.toMap(phab -> phab.getId().getHabilidadId(), PerfilHabilidad::getNivel));
            List<MatchingHabilidad> detalle = new ArrayList<>();
            double suma = 0;
            for (Map.Entry<Long, Integer> req : requisitos.entrySet()) {
                int tiene = niveles.getOrDefault(req.getKey(), 0);
                suma += Math.min(1d, tiene / (double) req.getValue());
                Habilidad h = habilidades.get(req.getKey());
                detalle.add(new MatchingHabilidad(h != null ? h.getNombre() : "Habilidad", req.getValue(), tiene,
                        nivel(req.getValue()), nivel(tiene)));
            }
            int sHab = requisitos.isEmpty() ? 0 : (int) Math.round(suma * 100 / requisitos.size());
            int experiencia = Optional.ofNullable(perfil.getExperienciaAnios()).orElse(0);
            int sExp = Math.min(100, experiencia * 20);
            int carga = Optional.ofNullable(asignacionRepository.sumarCargaActivaDePerfil(perfil.getId())).orElse(0);
            int declarada = Optional.ofNullable(perfil.getDisponibilidadPorcentaje()).orElse(100);
            int disponible = Math.max(0, Math.min(declarada, limiteCarga - carga));
            int sDisp = limiteCarga > 0 ? Math.min(100, (int) Math.round(disponible * 100.0 / limiteCarga)) : 0;
            int score = (int) Math.round((sHab * ph + sExp * pe + sDisp * pd) / (double) (ph + pe + pd));
            resultado.add(new MatchingCandidato(perfil.getId(), perfil.getUsuario().getNombreCompleto(),
                    InicialesUtil.de(perfil.getUsuario().getNombreCompleto()),
                    Optional.ofNullable(perfil.getCargo()).orElse("Colaborador"), score, sHab, sExp, sDisp,
                    experiencia, carga, disponible, carga + dedicacion, carga + dedicacion <= limiteCarga, detalle));
        }
        return resultado.stream().sorted(Comparator.comparingInt(MatchingCandidato::getScoreTotal).reversed()
                .thenComparing(MatchingCandidato::getNombre)).toList();
    }

    /** Propuesta desde AI Talent Matching (el candidato sale del ranking calculado). */
    @Transactional
    public void proponer(Long pmPerfilId, Long actorUsuarioId, Long proyectoId, Long candidatoId,
                         int dedicacion, int ph, int pe, int pd) {
        Proyecto proyecto = exigirProyectoDelPm(pmPerfilId, proyectoId);
        MatchingCandidato candidato = calcular(pmPerfilId, proyectoId, dedicacion, ph, pe, pd).stream()
                .filter(c -> c.getPerfilId().equals(candidatoId)).findFirst()
                .orElseThrow(() -> new OperacionInvalidaException("El candidato ya no está disponible para este proyecto."));
        registrarPropuesta(proyecto, pmPerfilId, actorUsuarioId, candidato, dedicacion, PropuestaAsignacion.ORIGEN_TALENT_MATCHING);
    }

    /**
     * Propuesta MANUAL del PM (modelo v7: el PM no asigna directo, propone y
     * el Resource Manager aprueba). Se calcula igual el puntaje de matching
     * con los pesos configurados, para que el RM decida con la misma
     * información que en una propuesta de Talent Matching.
     */
    @Transactional
    public void proponerManual(Long pmPerfilId, Long actorUsuarioId, Long proyectoId, Long candidatoId, int dedicacion) {
        Proyecto proyecto = exigirProyectoDelPm(pmPerfilId, proyectoId);
        int[] pesos = pesosConfigurados();
        MatchingCandidato candidato = calcularSinLimite(pmPerfilId, proyectoId, dedicacion, pesos).stream()
                .filter(c -> c.getPerfilId().equals(candidatoId)).findFirst()
                .orElseThrow(() -> new OperacionInvalidaException(nombreDe(candidatoId)
                        + " no está disponible para este proyecto (ya está en el equipo, no es un Miembro activo o alcanzó el máximo de proyectos)."));
        if (!candidato.isCargaCompatible()) {
            throw new OperacionInvalidaException(candidato.getNombre() + " tiene " + candidato.getCargaActual()
                    + "% de carga; con " + dedicacion + "% más superaría el límite configurado.");
        }
        registrarPropuesta(proyecto, pmPerfilId, actorUsuarioId, candidato, dedicacion, PropuestaAsignacion.ORIGEN_MANUAL);
    }

    /**
     * Varias propuestas manuales en un solo envío (checkboxes del PM). Cada
     * persona se valida por separado: si una falla, las demás sí se envían.
     * Igual que en la asignación masiva del RM, no se pueden proponer más
     * personas que vacantes libres (descontando las propuestas pendientes).
     */
    public AsignacionMasivaService.Resultado proponerVarios(Long pmPerfilId, Long actorUsuarioId, Long proyectoId,
                                                           List<Long> perfilIds, int dedicacion) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        if (perfilIds != null) for (Long id : perfilIds) if (id != null) ids.add(id);
        if (ids.isEmpty()) throw new OperacionInvalidaException("Selecciona al menos una persona.");
        Proyecto proyecto = exigirProyectoDelPm(pmPerfilId, proyectoId);
        long libres = vacantes(proyecto) - propuestaRepository.countByProyectoIdAndEstado(proyectoId, "pendiente");
        if (ids.size() > Math.max(0, libres)) {
            throw new OperacionInvalidaException("Seleccionaste " + ids.size() + " persona(s), pero el proyecto solo tiene "
                    + Math.max(0, libres) + " vacante(s) sin propuesta pendiente.");
        }
        List<String> enviados = new ArrayList<>();
        List<String> errores = new ArrayList<>();
        for (Long id : ids) {
            try {
                self().proponerManual(pmPerfilId, actorUsuarioId, proyectoId, id, dedicacion);
                enviados.add(nombreDe(id));
            } catch (OperacionInvalidaException ex) {
                errores.add(ex.getMessage());
            }
        }
        return new AsignacionMasivaService.Resultado(enviados, errores);
    }

    /**
     * Reincorporación tras reanudar o reabrir un proyecto (modelo v7, fase
     * 2): por cada persona marcada del "equipo anterior" se crea una
     * propuesta (origen=reincorporacion) con la MISMA dedicación que tenía,
     * a nombre del PM del proyecto, para que el Resource Manager la apruebe.
     * Solo el RM crea asignaciones de colaborador.
     */
    public AsignacionMasivaService.Resultado proponerReincorporaciones(Long proyectoId, List<Long> perfilIds, Long actorUsuarioId) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        if (perfilIds != null) for (Long id : perfilIds) if (id != null) ids.add(id);
        List<String> enviados = new ArrayList<>();
        List<String> errores = new ArrayList<>();
        if (ids.isEmpty()) return new AsignacionMasivaService.Resultado(enviados, errores);
        Long pmPerfilId = asignacionRepository.buscarResponsablesActivos(proyectoId, Roles.PROJECT_MANAGER).stream()
                .findFirst().map(Asignacion::getPerfilId)
                .orElseThrow(() -> new OperacionInvalidaException("El proyecto no tiene un Project Manager activo."));
        Map<Long, IntegranteAnterior> anteriores = proyectoService.equipoAnterior(proyectoId).stream()
                .collect(Collectors.toMap(IntegranteAnterior::perfilId, Function.identity()));
        for (Long id : ids) {
            IntegranteAnterior ant = anteriores.get(id);
            if (ant == null) { errores.add(nombreDe(id) + " no formaba parte del equipo anterior."); continue; }
            if (!ant.conCapacidad()) { errores.add(ant.nombre() + ": " + ant.motivo() + "."); continue; }
            try {
                self().proponerUnaReincorporacion(pmPerfilId, actorUsuarioId, proyectoId, id, ant.cargaAnterior());
                enviados.add(ant.nombre());
            } catch (OperacionInvalidaException ex) {
                errores.add(ex.getMessage());
            }
        }
        return new AsignacionMasivaService.Resultado(enviados, errores);
    }

    @Transactional
    public void proponerUnaReincorporacion(Long pmPerfilId, Long actorUsuarioId, Long proyectoId, Long candidatoId, int dedicacion) {
        Proyecto proyecto = exigirProyectoDelPm(pmPerfilId, proyectoId);
        MatchingCandidato candidato = calcularSinLimite(pmPerfilId, proyectoId, dedicacion, pesosConfigurados()).stream()
                .filter(c -> c.getPerfilId().equals(candidatoId)).findFirst()
                .orElseThrow(() -> new OperacionInvalidaException(nombreDe(candidatoId) + " ya no está disponible para este proyecto."));
        registrarPropuesta(proyecto, pmPerfilId, actorUsuarioId, candidato, dedicacion, PropuestaAsignacion.ORIGEN_REINCORPORACION);
    }

    private void registrarPropuesta(Proyecto proyecto, Long pmPerfilId, Long actorUsuarioId, MatchingCandidato candidato,
                                    int dedicacion, String origen) {
        Long proyectoId = proyecto.getId();
        Long candidatoId = candidato.getPerfilId();
        if (!ProyectoService.admiteNuevosIntegrantes(proyecto))
            throw new OperacionInvalidaException("Solo se puede proponer personas para proyectos en Planificación o Activo.");
        if (propuestaRepository.existsByProyectoIdAndCandidatoPerfilIdAndEstado(proyectoId, candidatoId, "pendiente"))
            throw new OperacionInvalidaException("Ya existe una propuesta pendiente para " + candidato.getNombre() + " en este proyecto.");

        PropuestaAsignacion p = new PropuestaAsignacion();
        p.setProyectoId(proyectoId); p.setCandidatoPerfilId(candidatoId); p.setSolicitadoPorPerfilId(pmPerfilId);
        p.setDedicacionPorcentaje(dedicacion); p.setScoreTotal(candidato.getScoreTotal());
        p.setScoreHabilidades(candidato.getScoreHabilidades()); p.setScoreExperiencia(candidato.getScoreExperiencia());
        p.setScoreDisponibilidad(candidato.getScoreDisponibilidad());
        p.setOrigen(origen);
        propuestaRepository.save(p);
        String via = PropuestaAsignacion.ORIGEN_MANUAL.equals(origen) ? "manual"
                : PropuestaAsignacion.ORIGEN_REINCORPORACION.equals(origen) ? "de reincorporación" : "de Talent Matching";
        for (Perfil rm : perfilRepository.listarActivosPorRolOrganizacional(Roles.RESOURCE_MANAGER)) {
            notificacionService.crear(rm.getId(), "solicitud", "Nueva propuesta de asignación (" + via + ")",
                    candidato.getNombre() + " fue propuesto para \"" + proyecto.getNombre() + "\" con " + dedicacion + "% de dedicación.",
                    "ai-talent-matching.html");
        }
        auditoriaService.registrar(actorUsuarioId, "MATCHING_PROPUESTO", "propuesta_asignacion", p.getId(), null,
                AuditoriaService.json("origen", origen),
                candidato.getNombre() + " propuesto (" + via + ") para \"" + proyecto.getNombre() + "\" (score " + candidato.getScoreTotal() + "%).");
    }

    @Transactional(readOnly = true)
    public List<PropuestaAsignacionFila> pendientes() { return propuestaRepository.listarPendientes().stream().map(this::fila).toList(); }

    @Transactional(readOnly = true)
    public List<PropuestaAsignacionFila> resueltas() { return propuestaRepository.listarResueltas().stream().limit(20).map(this::fila).toList(); }

    @Transactional
    public void resolver(Long propuestaId, boolean aprobar, String motivo, Long rmPerfilId, Long actorUsuarioId) {
        PropuestaAsignacion p = propuestaRepository.buscarDetalle(propuestaId)
                .orElseThrow(() -> new OperacionInvalidaException("La propuesta ya no existe."));
        if (!"pendiente".equals(p.getEstado())) throw new OperacionInvalidaException("La propuesta ya fue revisada.");
        if (aprobar) {
            proyectoService.asignarColaborador(p.getProyectoId(), p.getCandidatoPerfilId(),
                    p.getDedicacionPorcentaje(), LocalDate.now(), actorUsuarioId);
            p.setEstado("aprobada");
        } else {
            p.setEstado("rechazada");
        }
        p.setRevisadoPorPerfilId(rmPerfilId); p.setMotivoResolucion(limpiarMotivo(motivo));
        p.setFechaResolucion(LocalDateTime.now()); propuestaRepository.save(p);
        notificacionService.crear(p.getSolicitadoPorPerfilId(), "resultado_ia",
                "Propuesta de Talent Matching " + (aprobar ? "aprobada" : "rechazada"),
                p.getCandidato().getUsuario().getNombreCompleto() + " · " + p.getProyecto().getNombre() +
                        (p.getMotivoResolucion() != null ? ". " + p.getMotivoResolucion() : "."), "ai-talent-matching.html");
        auditoriaService.registrar(actorUsuarioId, aprobar ? "MATCHING_APROBADO" : "MATCHING_RECHAZADO",
                "propuesta_asignacion", p.getId(), "{\"estado\":\"pendiente\"}",
                "{\"estado\":\"" + p.getEstado() + "\"}", p.getProyecto().getNombre());
    }

    private PropuestaAsignacionFila fila(PropuestaAsignacion p) {
        int carga = Optional.ofNullable(asignacionRepository.sumarCargaActivaDePerfil(p.getCandidatoPerfilId())).orElse(0);
        var cfg = configuracionService.obtenerMapa();
        int limiteCarga = proyectoService.limiteCargaConfigurado();
        return new PropuestaAsignacionFila(p.getId(), p.getProyecto().getNombre(), p.getCandidato().getUsuario().getNombreCompleto(),
                InicialesUtil.de(p.getCandidato().getUsuario().getNombreCompleto()),
                Optional.ofNullable(p.getCandidato().getCargo()).orElse("Colaborador"), p.getSolicitante().getUsuario().getNombreCompleto(),
                p.getDedicacionPorcentaje(), p.getScoreTotal(), p.getScoreHabilidades(), p.getScoreExperiencia(),
                p.getScoreDisponibilidad(), carga, carga + p.getDedicacionPorcentaje(), carga + p.getDedicacionPorcentaje() <= limiteCarga,
                p.getEstado(), p.getFechaSolicitud() != null ? p.getFechaSolicitud().format(FECHA) : "", p.getMotivoResolucion(),
                p.getOrigen());
    }

    /**
     * Proxy de Spring de este mismo bean: proponerVarios llama a
     * proponerManual a través de él para que cada propuesta corra en su
     * propia transacción (mismo criterio que AsignacionMasivaService).
     */
    private TalentMatchingService self() {
        return selfProvider.getObject();
    }

    private String nombreDe(Long perfilId) {
        // buscarConUsuario() (join fetch), NO findById(): proponerVarios() a
        // proposito no es @Transactional (cada proponerManual corre en su
        // propia transaccion via self(), ver comentario de self()), asi que
        // para cuando se llega aqui ya no hay sesion de Hibernate abierta -
        // con findById(), perfil.getUsuario() (lazy) revienta con
        // LazyInitializationException. Mismo problema/arreglo documentado en
        // PerfilRepository.buscarConUsuario() y en NotificacionService.
        return perfilRepository.buscarConUsuario(perfilId)
                .map(x -> x.getUsuario() != null ? x.getUsuario().getNombreCompleto() : "La persona")
                .orElse("La persona");
    }

    private Proyecto exigirProyectoDelPm(Long pmId, Long proyectoId) {
        Proyecto p = proyectoRepository.findById(proyectoId).orElseThrow(() -> new OperacionInvalidaException("El proyecto no existe."));
        boolean gestiona = asignacionRepository.buscarResponsablesActivos(proyectoId, Roles.PROJECT_MANAGER).stream()
                .anyMatch(a -> a.getPerfilId().equals(pmId));
        if (!gestiona) throw new OperacionInvalidaException("No administras este proyecto.");
        return p;
    }

    private long vacantes(Proyecto p) {
        long asignados = asignacionRepository.countByProyectoIdAndEstadoAndRolEnProyecto(p.getId(), "activa", Roles.COLABORADOR);
        return Math.max(0, Optional.ofNullable(p.getColaboradoresRequeridos()).orElse(0) - asignados);
    }

    /** Habilidades que pide el proyecto: regla compartida con Catálogo y Reportes (DemandaHabilidadesService). */
    private Map<Long, Integer> requisitos(Proyecto p, Map<Long, Habilidad> habilidades) {
        return new HashMap<>(demandaService.requisitos(p, habilidades.values()));
    }

    private void validarPesos(int a, int b, int c) {
        if (a < 0 || b < 0 || c < 0 || a > 100 || b > 100 || c > 100 || a + b + c == 0)
            throw new OperacionInvalidaException("Los pesos deben estar entre 0 y 100 y sumar un valor mayor que cero.");
    }
    private static int entero(String v, int defecto) { try { return Integer.parseInt(v); } catch (Exception e) { return defecto; } }
    private static String nivel(int n) { return switch (n) { case 1,2 -> "Básico"; case 3 -> "Intermedio"; case 4 -> "Avanzado"; case 5 -> "Experto"; default -> "Sin declarar"; }; }
    private static String normalizar(String v) { return Normalizer.normalize(Optional.ofNullable(v).orElse(""), Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase().trim(); }
    private static String limpiarMotivo(String v) { return v == null || v.isBlank() ? null : v.trim().substring(0, Math.min(500, v.trim().length())); }
}
