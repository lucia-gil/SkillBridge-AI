package com.skillbridge.ai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillbridge.ai.dto.*;
import com.skillbridge.ai.model.*;
import com.skillbridge.ai.repository.*;
import com.skillbridge.ai.util.OperacionInvalidaException;
import com.skillbridge.ai.util.Roles;
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

    public TalentMatchingService(ProyectoRepository proyectoRepository, PerfilRepository perfilRepository,
            AsignacionRepository asignacionRepository, PerfilHabilidadRepository perfilHabilidadRepository,
            ProyectoHabilidadRequeridaRepository requeridaRepository, HabilidadRepository habilidadRepository,
            PropuestaAsignacionRepository propuestaRepository, ConfiguracionService configuracionService,
            ProyectoService proyectoService, NotificacionService notificacionService,
            AuditoriaService auditoriaService, ObjectMapper objectMapper) {
        this.proyectoRepository = proyectoRepository; this.perfilRepository = perfilRepository;
        this.asignacionRepository = asignacionRepository; this.perfilHabilidadRepository = perfilHabilidadRepository;
        this.requeridaRepository = requeridaRepository; this.habilidadRepository = habilidadRepository;
        this.propuestaRepository = propuestaRepository; this.configuracionService = configuracionService;
        this.proyectoService = proyectoService; this.notificacionService = notificacionService;
        this.auditoriaService = auditoriaService; this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<MatchingProyectoOpcion> proyectosDelPm(Long pmPerfilId) {
        return asignacionRepository.listarProyectosGestionados(pmPerfilId).stream()
                .filter(p -> !"completado".equals(p.getEstado()) && !"cancelado".equals(p.getEstado()))
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
        Proyecto proyecto = exigirProyectoDelPm(pmPerfilId, proyectoId);
        if (dedicacion < 1 || dedicacion > 100) throw new OperacionInvalidaException("La dedicación debe estar entre 1% y 100%.");
        if (vacantes(proyecto) < 1) throw new OperacionInvalidaException("El proyecto ya no tiene vacantes disponibles.");
        validarPesos(ph, pe, pd);
        var cfg = configuracionService.obtenerMapa();
        int limiteCarga = entero(configuracionService.valor(cfg, "limite_carga_colaborador", "100"), 100);
        int maximoProyectos = entero(configuracionService.valor(cfg, "maximo_proyectos_simultaneos", "3"), 3);

        Map<Long, Habilidad> habilidades = habilidadRepository.findAll().stream()
                .collect(Collectors.toMap(Habilidad::getId, Function.identity()));
        Map<Long, Integer> requisitos = requisitos(proyecto, habilidades);
        Set<Long> equipo = asignacionRepository.listarEquipoDeProyecto(proyectoId, "activa").stream()
                .map(Asignacion::getPerfilId).collect(Collectors.toSet());

        List<MatchingCandidato> resultado = new ArrayList<>();
        for (Perfil perfil : perfilRepository.listarActivosConUsuario()) {
            if (perfil.getUsuario().getRolOrganizacional() != null || equipo.contains(perfil.getId())) continue;
            if (asignacionRepository.listarPorPerfilYEstado(perfil.getId(), "activa").size() >= maximoProyectos) continue;
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
                .thenComparing(MatchingCandidato::getNombre)).limit(20).toList();
    }

    @Transactional
    public void proponer(Long pmPerfilId, Long actorUsuarioId, Long proyectoId, Long candidatoId,
                         int dedicacion, int ph, int pe, int pd) {
        Proyecto proyecto = exigirProyectoDelPm(pmPerfilId, proyectoId);
        MatchingCandidato candidato = calcular(pmPerfilId, proyectoId, dedicacion, ph, pe, pd).stream()
                .filter(c -> c.getPerfilId().equals(candidatoId)).findFirst()
                .orElseThrow(() -> new OperacionInvalidaException("El candidato ya no está disponible para este proyecto."));
        if (propuestaRepository.existsByProyectoIdAndCandidatoPerfilIdAndEstado(proyectoId, candidatoId, "pendiente"))
            throw new OperacionInvalidaException("Ya existe una propuesta pendiente para este candidato y proyecto.");

        PropuestaAsignacion p = new PropuestaAsignacion();
        p.setProyectoId(proyectoId); p.setCandidatoPerfilId(candidatoId); p.setSolicitadoPorPerfilId(pmPerfilId);
        p.setDedicacionPorcentaje(dedicacion); p.setScoreTotal(candidato.getScoreTotal());
        p.setScoreHabilidades(candidato.getScoreHabilidades()); p.setScoreExperiencia(candidato.getScoreExperiencia());
        p.setScoreDisponibilidad(candidato.getScoreDisponibilidad());
        propuestaRepository.save(p);
        for (Perfil rm : perfilRepository.listarActivosPorRolOrganizacional(Roles.RESOURCE_MANAGER)) {
            notificacionService.crear(rm.getId(), "solicitud", "Nueva propuesta de Talent Matching",
                    candidato.getNombre() + " fue propuesto para \"" + proyecto.getNombre() + "\" con " + dedicacion + "% de dedicación.",
                    "ai-talent-matching.html");
        }
        auditoriaService.registrar(actorUsuarioId, "MATCHING_PROPUESTO", "propuesta_asignacion", p.getId(), null, null,
                candidato.getNombre() + " propuesto para \"" + proyecto.getNombre() + "\" (score " + candidato.getScoreTotal() + "%).");
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
            proyectoService.asignarColaborador(p.getProyectoId(), p.getCandidatoPerfilId(), Roles.COLABORADOR,
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
        int limiteCarga = entero(configuracionService.valor(cfg, "limite_carga_colaborador", "100"), 100);
        return new PropuestaAsignacionFila(p.getId(), p.getProyecto().getNombre(), p.getCandidato().getUsuario().getNombreCompleto(),
                InicialesUtil.de(p.getCandidato().getUsuario().getNombreCompleto()),
                Optional.ofNullable(p.getCandidato().getCargo()).orElse("Colaborador"), p.getSolicitante().getUsuario().getNombreCompleto(),
                p.getDedicacionPorcentaje(), p.getScoreTotal(), p.getScoreHabilidades(), p.getScoreExperiencia(),
                p.getScoreDisponibilidad(), carga, carga + p.getDedicacionPorcentaje(), carga + p.getDedicacionPorcentaje() <= limiteCarga,
                p.getEstado(), p.getFechaSolicitud() != null ? p.getFechaSolicitud().format(FECHA) : "", p.getMotivoResolucion());
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

    private Map<Long, Integer> requisitos(Proyecto p, Map<Long, Habilidad> habilidades) {
        Map<Long, Integer> r = requeridaRepository.findById_ProyectoId(p.getId()).stream()
                .collect(Collectors.toMap(x -> x.getId().getHabilidadId(), ProyectoHabilidadRequerida::getNivelRequerido));
        if (!r.isEmpty() || p.getTecnologias() == null || p.getTecnologias().isBlank()) return r;
        try {
            List<String> tecnologias = objectMapper.readValue(p.getTecnologias(), new TypeReference<List<String>>() {});
            for (String tecnologia : tecnologias) habilidades.values().stream()
                    .filter(h -> normalizar(h.getNombre()).equals(normalizar(tecnologia)))
                    .findFirst().ifPresent(h -> r.put(h.getId(), 3));
        } catch (Exception ignored) { }
        return r;
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
