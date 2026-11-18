package com.skillbridge.ai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillbridge.ai.dto.AsignacionCargaFila;
import com.skillbridge.ai.dto.EventoCalendario;
import com.skillbridge.ai.dto.MiProyectoFila;
import com.skillbridge.ai.dto.MiembroEquipoFila;
import com.skillbridge.ai.dto.PerfilAsignable;
import com.skillbridge.ai.dto.PerfilOpcion;
import com.skillbridge.ai.dto.ProyectoDetalle;
import com.skillbridge.ai.dto.ProyectoFila;
import com.skillbridge.ai.model.Asignacion;
import com.skillbridge.ai.model.Perfil;
import com.skillbridge.ai.model.Proyecto;
import com.skillbridge.ai.repository.AsignacionRepository;
import com.skillbridge.ai.repository.EntregableRepository;
import com.skillbridge.ai.repository.ForoPublicacionRepository;
import com.skillbridge.ai.repository.PerfilRepository;
import com.skillbridge.ai.repository.PropuestaAsignacionRepository;
import com.skillbridge.ai.dto.IntegranteAnterior;
import com.skillbridge.ai.model.PropuestaAsignacion;
import com.skillbridge.ai.repository.ArchivoProyectoRepository;
import com.skillbridge.ai.dto.ArchivoProyectoFila;
import com.skillbridge.ai.repository.ProyectoRepository;
import com.skillbridge.ai.util.OperacionInvalidaException;
import com.skillbridge.ai.util.Roles;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * CRUD real de Proyectos (RF03) + asignación de colaboradores (RF03/RF04).
 *
 * No existe columna de "% de avance" en el esquema (ver comentario de la
 * tabla proyectos en skillbridge_db.sql): se calcula un heurístico de
 * tiempo transcurrido entre fecha_inicio y fecha_fin_estimada
 * (avanceHeuristico), documentado para no aparentar que es progreso real
 * de tareas completadas.
 *
 * crear(...) inserta también la fila de asignaciones del Project Manager en
 * la misma transacción, tal como exige la nota de integridad del esquema
 * v4 (ya no existe proyectos.creado_por_id).
 */
@Service
public class ProyectoService {

    private static final DateTimeFormatter FECHA_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ProyectoRepository proyectoRepository;
    private final AsignacionRepository asignacionRepository;
    private final PerfilRepository perfilRepository;
    private final EntregableRepository entregableRepository;
    private final ForoPublicacionRepository foroPublicacionRepository;
    private final AuditoriaService auditoriaService;
    private final NotificacionService notificacionService;
    private final ConfiguracionService configuracionService;
    private final ObjectMapper objectMapper;
    private final PropuestaAsignacionRepository propuestaRepository;
    private final ArchivoProyectoRepository archivoProyectoRepository;
    private final com.skillbridge.ai.repository.RecomendacionIaLogRepository recomendacionIaLogRepository;
    private final com.skillbridge.ai.repository.HabilidadRepository habilidadRepository;
    private final com.skillbridge.ai.repository.ProyectoHabilidadRequeridaRepository proyectoHabilidadRequeridaRepository;

    public ProyectoService(ProyectoRepository proyectoRepository, AsignacionRepository asignacionRepository,
                            PerfilRepository perfilRepository, EntregableRepository entregableRepository,
                            ForoPublicacionRepository foroPublicacionRepository, AuditoriaService auditoriaService,
                            NotificacionService notificacionService, ConfiguracionService configuracionService,
                            ObjectMapper objectMapper, PropuestaAsignacionRepository propuestaRepository,
                            ArchivoProyectoRepository archivoProyectoRepository,
                            com.skillbridge.ai.repository.RecomendacionIaLogRepository recomendacionIaLogRepository,
                            com.skillbridge.ai.repository.HabilidadRepository habilidadRepository,
                            com.skillbridge.ai.repository.ProyectoHabilidadRequeridaRepository proyectoHabilidadRequeridaRepository) {
        this.proyectoRepository = proyectoRepository;
        this.asignacionRepository = asignacionRepository;
        this.perfilRepository = perfilRepository;
        this.entregableRepository = entregableRepository;
        this.foroPublicacionRepository = foroPublicacionRepository;
        this.auditoriaService = auditoriaService;
        this.notificacionService = notificacionService;
        this.configuracionService = configuracionService;
        this.objectMapper = objectMapper;
        this.propuestaRepository = propuestaRepository;
        this.archivoProyectoRepository = archivoProyectoRepository;
        this.recomendacionIaLogRepository = recomendacionIaLogRepository;
        this.habilidadRepository = habilidadRepository;
        this.proyectoHabilidadRequeridaRepository = proyectoHabilidadRequeridaRepository;
    }

    // ───────────────────────── Lectura ─────────────────────────

    public List<ProyectoFila> listar() {
        return proyectoRepository.findAllByOrderByFechaCreacionDesc().stream()
                .map(this::aFila)
                .collect(Collectors.toList());
    }

    private ProyectoFila aFila(Proyecto p) {
        String pmNombre = nombrePmActivo(p.getId());
        long equipo = asignacionRepository.countByProyectoIdAndEstado(p.getId(), "activa");
        int avance = avanceHeuristico(p.getFechaInicio(), p.getFechaFinEstimada(), p.getEstado());
        return new ProyectoFila(p.getId(), p.getNombre(), p.getEstado(), pmNombre, equipo,
                formatear(p.getFechaInicio()), formatear(p.getFechaFinEstimada()), avance, parseTecnologias(p.getTecnologias()));
    }

    public ProyectoDetalle obtenerDetalle(Long id) {
        Proyecto p = proyectoRepository.findById(id)
                .orElseThrow(() -> new OperacionInvalidaException("El proyecto ya no existe."));
        String pmNombre = nombrePmActivo(id);
        List<MiembroEquipoFila> equipo = asignacionRepository.listarEquipoDeProyecto(id, "activa").stream()
                .map(a -> new MiembroEquipoFila(a.getId(), a.getPerfilId(), a.getPerfil().getUsuario().getNombreCompleto(),
                        a.getPerfil().getUsuario().getCorreo(), a.getRolEnProyecto(), a.getCargaPorcentaje()))
                .collect(Collectors.toList());
        int avance = avanceHeuristico(p.getFechaInicio(), p.getFechaFinEstimada(), p.getEstado());
        ProyectoDetalle detalle = new ProyectoDetalle(p.getId(), p.getNombre(), p.getDescripcion(), parseTecnologias(p.getTecnologias()),
                p.getEstado(), formatear(p.getFechaInicio()), formatear(p.getFechaFinEstimada()),
                p.getFechaInicio() == null ? null : p.getFechaInicio().toString(),
                p.getFechaFinEstimada() == null ? null : p.getFechaFinEstimada().toString(),
                avance, p.getColaboradoresRequeridos(), pmNombre, equipo);
        // Modelo v7 (fase 2): datos de la pausa / cierre y equipo anterior.
        if ("en_pausa".equals(p.getEstado())) {
            detalle.setFechaPausaLabel(p.getFechaPausa() != null ? formatear(p.getFechaPausa()) : null);
            detalle.setMotivoPausa(p.getMotivoPausa());
            detalle.setFechaReanudacionLabel(p.getFechaReanudacionEstimada() != null ? formatear(p.getFechaReanudacionEstimada()) : null);
            detalle.setFechaReanudacionIso(p.getFechaReanudacionEstimada() != null ? p.getFechaReanudacionEstimada().toString() : null);
            detalle.setReanudacionVencida(p.getFechaReanudacionEstimada() != null
                    && p.getFechaReanudacionEstimada().isBefore(LocalDate.now()));
        }
        if ("cancelado".equals(p.getEstado())) {
            detalle.setRazonCancelacion(p.getRazonCancelacion());
        }
        if ("en_pausa".equals(p.getEstado()) || estaCerrado(p)) {
            detalle.setEquipoAnterior(equipoAnterior(p));
        }
        // v8: GitHub URL y archivos de contexto.
        detalle.setGithubUrl(p.getGithubUrl());
        List<ArchivoProyectoFila> archivos = archivoProyectoRepository
                .findByProyectoIdOrderByFechaSubidaDesc(p.getId()).stream()
                .map(a -> new ArchivoProyectoFila(a.getId(), a.getNombre(), a.getTipo(),
                        a.getFechaSubida() != null ? formatear(a.getFechaSubida().toLocalDate()) : "—"))
                .collect(Collectors.toList());
        detalle.setArchivos(archivos);
        return detalle;
    }

    private String nombrePmActivo(Long proyectoId) {
        return asignacionRepository.buscarResponsablesActivos(proyectoId, Roles.PROJECT_MANAGER).stream()
                .findFirst()
                .map(a -> a.getPerfil().getUsuario().getNombreCompleto())
                .orElse(null);
    }

    public List<PerfilOpcion> listarPerfilesParaAsignar() {
        // Admin y Resource Manager "existen por encima" de los proyectos
        // (ver comentario de la tabla asignaciones en el SQL): no deben
        // poder ser asignados como colaborador. Decision confirmada con el
        // equipo (ver PDF de permisos, 2 de octubre).
        return perfilRepository.listarActivosConUsuario().stream()
                .filter(ProyectoService::esMiembroAsignable)
                .map(p -> new PerfilOpcion(p.getId(), p.getUsuario().getNombreCompleto(), p.getUsuario().getCorreo()))
                .collect(Collectors.toList());
    }

    /**
     * Igual que listarPerfilesParaAsignar() (mismo filtro: sin Admin ni RM),
     * pero con la carga activa y el número de proyectos activos de cada
     * persona, para la lista con checkboxes de la asignación en masa.
     */
    @Transactional(readOnly = true)
    public List<PerfilAsignable> listarPerfilesAsignables() {
        return perfilRepository.listarActivosConUsuario().stream()
                .filter(ProyectoService::esMiembroAsignable)
                .map(p -> new PerfilAsignable(p.getId(), p.getUsuario().getNombreCompleto(), p.getUsuario().getCorreo(),
                        p.getCargo() != null ? p.getCargo() : "",
                        sumarCargaActivaSegura(p.getId()),
                        (int) asignacionRepository.contarProyectosVigentesDePerfil(p.getId())))
                .collect(Collectors.toList());
    }

    /** Límite de carga (%) configurado en Configuración (por defecto 100). */
    public int limiteCargaConfigurado() {
        // Fuente única compartida con los reportes (ConfiguracionService.limiteCarga).
        return configuracionService.limiteCarga();
    }

    /** Máximo de proyectos vigentes simultáneos por persona (Configuración, por defecto 3). */
    public int maximoProyectosConfigurado() {
        var configuracion = configuracionService.obtenerMapa();
        return parseEntero(configuracionService.valor(configuracion, "maximo_proyectos_simultaneos", "3"), 3);
    }

    /**
     * Asignaciones activas de un colaborador con su dedicación, para el modal
     * "Rebalancear carga" de Ocupación (RM).
     */
    @Transactional(readOnly = true)
    public List<AsignacionCargaFila> asignacionesActivasDe(Long perfilId) {
        return asignacionRepository.listarPorPerfilYEstado(perfilId, "activa").stream()
                .map(a -> new AsignacionCargaFila(a.getId(),
                        a.getProyecto() != null ? a.getProyecto().getNombre() : "Proyecto",
                        Roles.PROJECT_MANAGER.equals(a.getRolEnProyecto()) ? "Project Manager" : "Colaborador",
                        Roles.PROJECT_MANAGER.equals(a.getRolEnProyecto()),
                        a.getCargaPorcentaje() != null ? a.getCargaPorcentaje() : 0))
                .collect(Collectors.toList());
    }

    /**
     * Cambia la dedicación (%) de una asignación activa. Pensado para
     * "Rebalancear carga" en Ocupación: bajar la dedicación de alguien
     * sobre-asignado sin tener que finalizar y volver a asignar. Si se SUBE,
     * se respeta el mismo límite de carga configurado que al asignar.
     */
    @Transactional
    public void ajustarDedicacion(Long asignacionId, int nuevaCarga, String motivo, Long actorUsuarioId) {
        Asignacion a = asignacionRepository.findById(asignacionId)
                .orElseThrow(() -> new OperacionInvalidaException("La asignación ya no existe."));
        if (!"activa".equals(a.getEstado())) {
            throw new OperacionInvalidaException("Solo se puede ajustar una asignación activa.");
        }
        int limiteCarga = limiteCargaConfigurado();
        if (nuevaCarga < 1 || nuevaCarga > limiteCarga) {
            throw new OperacionInvalidaException("La dedicación debe estar entre 1% y " + limiteCarga + "%.");
        }
        int anterior = a.getCargaPorcentaje() != null ? a.getCargaPorcentaje() : 0;
        if (anterior == nuevaCarga) {
            throw new OperacionInvalidaException("La dedicación ya es " + nuevaCarga + "%.");
        }
        if (nuevaCarga > anterior) {
            int total = sumarCargaActivaSegura(a.getPerfilId()) - anterior + nuevaCarga;
            if (total > limiteCarga) {
                throw new OperacionInvalidaException("Con ese cambio la carga total sería " + total
                        + "%, por encima del límite de " + limiteCarga + "%.");
            }
        }
        a.setCargaPorcentaje(nuevaCarga);
        asignacionRepository.save(a);
        String proyecto = proyectoRepository.findById(a.getProyectoId()).map(Proyecto::getNombre).orElse("el proyecto");
        auditoriaService.registrar(actorUsuarioId, "ASIGNACION_EDITADA", "asignacion", asignacionId,
                AuditoriaService.json("carga_porcentaje", String.valueOf(anterior)),
                AuditoriaService.json("carga_porcentaje", String.valueOf(nuevaCarga)),
                AuditoriaService.conMotivo("Dedicación en \"" + proyecto + "\": " + anterior + "% → " + nuevaCarga + "%", motivo) + ".");
        notificacionService.crear(a.getPerfilId(), "asignacion", "Dedicación actualizada",
                "Tu dedicación en \"" + proyecto + "\" cambió de " + anterior + "% a " + nuevaCarga + "%.", "proyectos.html");
    }

    /** Nombre completo de un perfil (para los mensajes de la asignación en masa). */
    @Transactional(readOnly = true)
    public String nombreDePerfil(Long perfilId) {
        return perfilRepository.findById(perfilId)
                .map(p -> p.getUsuario() != null ? p.getUsuario().getNombreCompleto() : "Colaborador")
                .orElse("Colaborador");
    }

    /**
     * Personas que todavía pueden recibir una asignación. Reutiliza los
     * mismos límites de carga y proyectos simultáneos que asignarColaborador,
     * y excluye al PM porque colaboradores_requeridos no lo cuenta como una
     * vacante adicional del equipo.
     */
    public int contarColaboradoresDisponibles(Long pmPerfilId) {
        int limiteCarga = limiteCargaConfigurado();
        int maximoProyectos = maximoProyectosConfigurado();

        return (int) perfilRepository.listarActivosConUsuario().stream()
                .filter(ProyectoService::esMiembroAsignable)
                .filter(p -> !p.getId().equals(pmPerfilId))
                .filter(p -> p.getDisponibilidadPorcentaje() != null && p.getDisponibilidadPorcentaje() > 0)
                .filter(p -> asignacionRepository.contarProyectosVigentesDePerfil(p.getId()) < maximoProyectos)
                .filter(p -> sumarCargaActivaSegura(p.getId()) < limiteCarga)
                .count();
    }

    /**
     * Eventos para el Calendario (colaborador y PM): las fechas reales de los
     * proyectos donde el perfil tiene una asignacion activa - inicio y entrega
     * estimada de cada proyecto. Fecha en ISO (yyyy-MM-dd) para el JS del calendario.
     */
    public List<EventoCalendario> eventosCalendarioDe(Long perfilId) {
        List<EventoCalendario> eventos = new ArrayList<>();
        for (Asignacion a : asignacionRepository.listarPorPerfilYEstado(perfilId, "activa")) {
            Proyecto p = a.getProyecto();
            if (p.getFechaInicio() != null) {
                eventos.add(new EventoCalendario(p.getFechaInicio().toString(), p.getNombre(), "inicio"));
            }
            if (p.getFechaFinEstimada() != null) {
                eventos.add(new EventoCalendario(p.getFechaFinEstimada().toString(), p.getNombre(), "entrega"));
            }
        }
        return eventos;
    }

    // ─────────────────── "Mis proyectos" (Colaborador) ───────────────────

    public List<MiProyectoFila> misProyectosActivos(Long perfilId) {
        return asignacionRepository.listarPorPerfilYEstado(perfilId, "activa").stream()
                .map(this::aMiProyectoFila)
                .collect(Collectors.toList());
    }

    /**
     * Proyectos actuales donde el perfil COLABORA (vista Colaborador). Si la
     * misma persona lidera otros proyectos, esos se ven en su vista Project
     * Manager, no aquí (modelo v7, caso "Paco").
     */
    public List<MiProyectoFila> misProyectosComoColaborador(Long perfilId) {
        return misProyectosActivos(perfilId).stream()
                .filter(m -> Roles.COLABORADOR.equals(m.getRolEnProyectoCrudo()))
                .collect(Collectors.toList());
    }

    public List<MiProyectoFila> misProyectosHistorial(Long perfilId) {
        return asignacionRepository.listarPorPerfil(perfilId).stream()
                .map(this::aMiProyectoFila)
                .collect(Collectors.toList());
    }

    private MiProyectoFila aMiProyectoFila(Asignacion a) {
        Proyecto p = a.getProyecto();
        int avance = avanceHeuristico(p.getFechaInicio(), p.getFechaFinEstimada(), p.getEstado());
        String periodo = formatear(a.getFechaInicio()) + " → " + (a.getFechaFin() != null ? formatear(a.getFechaFin()) : "actualidad");
        return new MiProyectoFila(a.getId(), p.getId(), p.getNombre(), p.getDescripcion(), parseTecnologias(p.getTecnologias()),
                p.getEstado(), a.getRolEnProyecto(), a.getCargaPorcentaje(), avance, periodo, a.getEstado());
    }

    // ───────────────────────── Escritura ─────────────────────────

    @Transactional
    public Proyecto crear(String nombre, String descripcion, List<String> tecnologias, LocalDate fechaInicio,
                           LocalDate fechaFinEstimada, int colaboradoresRequeridos, Long pmPerfilId,
                           Long actorUsuarioId, String githubUrl) {
        if (nombre == null || nombre.isBlank()) {
            throw new OperacionInvalidaException("Ingresa un nombre para el proyecto.");
        }
        if (proyectoRepository.existsByNombreIgnoreCase(nombre.trim())) {
            throw new OperacionInvalidaException("Ya existe un proyecto con ese nombre.");
        }
        if (fechaInicio == null) {
            throw new OperacionInvalidaException("La fecha de inicio es obligatoria.");
        }
        if (fechaInicio.isBefore(LocalDate.now())) {
            throw new OperacionInvalidaException("La fecha de inicio no puede ser anterior a la fecha actual.");
        }
        if (fechaInicio.isAfter(LocalDate.now().plusDays(INICIO_MAXIMO_DIAS_A_FUTURO))) {
            throw new OperacionInvalidaException("La fecha de inicio no puede planificarse a más de "
                    + (INICIO_MAXIMO_DIAS_A_FUTURO / 365) + " año desde hoy.");
        }
        if (fechaFinEstimada == null) {
            throw new OperacionInvalidaException("La fecha de fin estimada es obligatoria.");
        }
        if (fechaFinEstimada.isBefore(fechaInicio)) {
            throw new OperacionInvalidaException("La fecha de fin estimada no puede ser anterior al inicio.");
        }
        long duracionCrear = ChronoUnit.DAYS.between(fechaInicio, fechaFinEstimada);
        if (duracionCrear < DURACION_MINIMA_DIAS) {
            throw new OperacionInvalidaException("Debe haber al menos " + DURACION_MINIMA_DIAS + " días de diferencia entre la fecha de inicio y la fecha de fin.");
        }
        if (duracionCrear > DURACION_MAXIMA_DIAS) {
            throw new OperacionInvalidaException("La duración no puede superar los " + (DURACION_MAXIMA_DIAS / 365) + " años (" + DURACION_MAXIMA_DIAS + " días). Para proyectos más largos, divídelo en varios proyectos.");
        }
        Perfil pm = perfilRepository.findById(pmPerfilId)
                .orElseThrow(() -> new OperacionInvalidaException("Selecciona un Project Manager válido."));
        // Modelo v7: solo un Miembro activo (sin rol organizacional) puede
        // liderar un proyecto; Administrador y Resource Manager nunca
        // participan en proyectos. Además debe tener capacidad para el 20%.
        if (!esMiembroAsignable(pm)) {
            throw new OperacionInvalidaException(pm.getUsuario().getNombreCompleto()
                    + " no puede liderar proyectos: solo un Miembro activo (sin rol de Administrador o Resource Manager) puede ser Project Manager.");
        }
        exigirCapacidad(pm, CARGA_PM_POR_DEFECTO);
        int disponibles = contarColaboradoresDisponibles(pmPerfilId);
        if (colaboradoresRequeridos < 0) {
            throw new OperacionInvalidaException("La cantidad de colaboradores requeridos no puede ser negativa.");
        }
        if (colaboradoresRequeridos > disponibles) {
            throw new OperacionInvalidaException("No hay colaboradores suficientes. Solo hay " + disponibles
                    + " colaborador(es) disponible(s) en este momento.");
        }

        Proyecto p = new Proyecto();
        p.setNombre(nombre.trim());
        p.setDescripcion(descripcion);
        p.setTecnologias(serializarTecnologias(tecnologias));
        p.setEstado("planificacion");
        p.setColaboradoresRequeridos(colaboradoresRequeridos);
        p.setFechaInicio(fechaInicio);
        p.setFechaFinEstimada(fechaFinEstimada);
        if (githubUrl != null && !githubUrl.isBlank()) {
            p.setGithubUrl(githubUrl.trim());
        }
        p = proyectoRepository.save(p);

        // Nota de integridad del esquema v4: el PM se registra vía asignaciones,
        // no hay proyectos.creado_por_id (ver skillbridge_db.sql, sección 7).
        Asignacion pmAsig = new Asignacion();
        pmAsig.setProyectoId(p.getId());
        pmAsig.setPerfilId(pm.getId());
        pmAsig.setRolEnProyecto(Roles.PROJECT_MANAGER);
        pmAsig.setCargaPorcentaje(CARGA_PM_POR_DEFECTO);
        pmAsig.setEstado("activa");
        pmAsig.setFechaInicio(fechaInicio);
        asignacionRepository.save(pmAsig);

        auditoriaService.registrar(actorUsuarioId, "PROYECTO_CREADO", "proyecto", p.getId(), null, null,
                "Proyecto \"" + p.getNombre() + "\" creado con PM " + pm.getUsuario().getNombreCompleto() + ".");
        notificacionService.crear(pm.getId(), "asignacion", "Nuevo proyecto asignado",
                "Se te asignó como Project Manager de \"" + p.getNombre() + "\".", "proyectos.html");

        return p;
    }

    /**
     * Guarda las habilidades requeridas de un proyecto recien creado
     * (proyecto_habilidad_requerida). Listas paralelas habilidadIds/niveles
     * (igual convencion que "habilidad"/"perfilIds" en otros formularios):
     * la fila i de una corresponde a la fila i de la otra. Filas invalidas
     * (habilidad inexistente, nivel fuera de 1-5, o listas desalineadas en
     * esa posicion) se ignoran en silencio para no bloquear la creacion del
     * proyecto por un detalle de UI; el PM siempre puede corregirlo despues
     * desde "Editar proyecto".
     */
    @Transactional
    public void guardarHabilidadesRequeridas(Long proyectoId, List<Long> habilidadIds, List<Integer> niveles) {
        if (habilidadIds == null || habilidadIds.isEmpty()) return;
        java.util.LinkedHashSet<Long> vistos = new java.util.LinkedHashSet<>();
        for (int i = 0; i < habilidadIds.size(); i++) {
            Long habilidadId = habilidadIds.get(i);
            if (habilidadId == null || !vistos.add(habilidadId)) continue;
            if (!habilidadRepository.existsById(habilidadId)) continue;
            Integer nivelCrudo = (niveles != null && i < niveles.size()) ? niveles.get(i) : null;
            int nivel = nivelCrudo == null ? 3 : Math.max(1, Math.min(5, nivelCrudo));

            com.skillbridge.ai.model.ProyectoHabilidadRequeridaId id = new com.skillbridge.ai.model.ProyectoHabilidadRequeridaId();
            id.setProyectoId(proyectoId);
            id.setHabilidadId(habilidadId);
            com.skillbridge.ai.model.ProyectoHabilidadRequerida fila = new com.skillbridge.ai.model.ProyectoHabilidadRequerida();
            fila.setId(id);
            fila.setNivelRequerido(nivel);
            proyectoHabilidadRequeridaRepository.save(fila);
        }
    }

    /**
     * Elimina un proyecto por completo. Bloqueado si el proyecto ya tiene
     * trabajo real encima (colaboradores asignados, entregables o hilos de
     * foro) - igual que eliminarCategoria() en HabilidadService - porque la
     * mayoria de tablas dependientes (entregables, fases, salas de chat,
     * recursos IA, eventos) tienen ON DELETE CASCADE en el esquema y se
     * perderian sin aviso real. Para un proyecto que ya avanzo, usa
     * cambiarEstado(..., "cancelado") en vez de borrarlo (ver comentario de
     * la tabla proyectos en db/skillbridge_db.sql).
     */
    @Transactional
    public void eliminar(Long proyectoId, String motivo, Long actorUsuarioId) {
        Proyecto p = proyectoRepository.findById(proyectoId)
                .orElseThrow(() -> new OperacionInvalidaException("El proyecto ya no existe."));
        long colaboradores = asignacionRepository.countByProyectoIdAndRolEnProyectoAndEstado(
                proyectoId, Roles.COLABORADOR, "activa");
        long entregables = entregableRepository.countByProyectoId(proyectoId);
        long hilosForo = foroPublicacionRepository.countByProyectoIdAndPublicacionPadreIdIsNull(proyectoId);
        if (colaboradores > 0 || entregables > 0 || hilosForo > 0) {
            throw new OperacionInvalidaException("No se puede eliminar \"" + p.getNombre() + "\": tiene "
                    + colaboradores + " colaborador(es) asignado(s), " + entregables + " entregable(s) y "
                    + hilosForo + " hilo(s) de foro. Usa \"Cancelado\" en el estado en vez de eliminarlo.");
        }
        if (!"cancelado".equals(p.getEstado())) {
            throw new OperacionInvalidaException("No se puede eliminar \"" + p.getNombre()
                    + "\": el proyecto debe estar en estado \"Cancelado\" antes de poder eliminarlo.");
        }
        String motivoLimpio = AuditoriaService.limpiarMotivo(motivo);
        if (motivoLimpio == null) {
            throw new OperacionInvalidaException("Indica el motivo por el que eliminas el proyecto: queda registrado en Auditoría.");
        }
        String nombre = p.getNombre();
        String razon = p.getRazonCancelacion();
        propuestaRepository.deleteByProyectoId(proyectoId);
        asignacionRepository.deleteByProyectoId(proyectoId);
        proyectoRepository.delete(p);
        auditoriaService.registrar(actorUsuarioId, "PROYECTO_ELIMINADO", "proyecto", proyectoId, null, null,
                AuditoriaService.conMotivo("Proyecto \"" + nombre + "\" eliminado (estaba cancelado"
                        + (razon != null && !razon.isBlank() ? " por: " + razon : "") + ")", motivoLimpio));

        // Antes nadie se enteraba de un proyecto eliminado salvo revisando
        // Auditoria a mano. Se avisa a los Resource Manager (visibilidad del
        // area, igual que con una propuesta de asignacion) - no hay equipo
        // al que avisar porque eliminar() ya exige 0 colaboradores activos.
        for (Perfil rm : perfilRepository.listarActivosPorRolOrganizacional(Roles.RESOURCE_MANAGER)) {
            notificacionService.crear(rm.getId(), "info", "Proyecto eliminado: " + nombre,
                    "Motivo: " + motivoLimpio, null);
        }
    }

    /**
     * Transiciones de estado permitidas (modelo v7, fase 2). Completado y
     * Cancelado son estados de cierre: desde ahí solo se puede REABRIR, y
     * reabrir siempre vuelve a Planificación.
     */
    private static final java.util.Map<String, List<String>> TRANSICIONES = java.util.Map.of(
            "planificacion", List.of("activo", "en_pausa", "completado", "cancelado"),
            "activo", List.of("planificacion", "en_pausa", "completado", "cancelado"),
            "en_pausa", List.of("activo", "planificacion", "completado", "cancelado"),
            "completado", List.of("planificacion"),
            "cancelado", List.of("planificacion"));

    /**
     * Cambia el estado de un proyecto aplicando sus efectos (modelo v7):
     *
     *  - PAUSAR (motivo + fecha estimada de reanudación, obligatorios): los
     *    colaboradores SALEN del proyecto (asignación finalizada con fecha de
     *    hoy) y su carga se libera de verdad; el PM se queda (planifica la
     *    reanudación) y su carga sigue contando. Así, al reanudar, nadie
     *    puede quedar sobreasignado.
     *  - REANUDAR (en_pausa -> activo/planificacion): el proyecto vuelve sin
     *    equipo; quien reanuda puede marcar a quién reincorporar y eso genera
     *    propuestas que aprueba el Resource Manager (lo hace el controlador
     *    con TalentMatchingService.proponerReincorporaciones).
     *  - COMPLETAR / CANCELAR: los colaboradores pasan a finalizada /
     *    cancelada; el PM se mantiene (acceso al historial) pero su carga deja
     *    de contar porque el proyecto ya no es vigente.
     *  - REABRIR (completado/cancelado -> planificacion): exige que el PM
     *    siga siendo un Miembro activo con capacidad; si no, el RM debe
     *    cambiar el PM antes.
     *
     * En pausa, completar y cancelar, las propuestas pendientes se rechazan
     * solas con el motivo correspondiente.
     */
    @Transactional
    public void cambiarEstado(Long proyectoId, String nuevoEstado, String motivo, LocalDate fechaReanudacion,
                              Long actorUsuarioId) {
        Proyecto p = proyectoRepository.findById(proyectoId)
                .orElseThrow(() -> new OperacionInvalidaException("El proyecto ya no existe."));
        String anterior = p.getEstado();
        if (!TRANSICIONES.containsKey(nuevoEstado)) {
            throw new OperacionInvalidaException("Estado de proyecto no reconocido.");
        }
        if (nuevoEstado.equals(anterior)) {
            throw new OperacionInvalidaException("El proyecto ya está " + etiquetaEstado(anterior) + ".");
        }
        if (!TRANSICIONES.getOrDefault(anterior, List.of()).contains(nuevoEstado)) {
            throw new OperacionInvalidaException("Un proyecto " + etiquetaEstado(anterior).replace("en ", "")
                    + " no puede pasar a " + etiquetaEstado(nuevoEstado).replace("en ", "")
                    + (estaCerrado(p) ? ". Usa \"Reabrir\": el proyecto vuelve a Planificación." : "."));
        }
        String motivoLimpio = AuditoriaService.limpiarMotivo(motivo);
        LocalDate hoy = LocalDate.now();

        switch (nuevoEstado) {
            case "en_pausa" -> {
                if (motivoLimpio == null) {
                    throw new OperacionInvalidaException("Para pausar el proyecto debes indicar el motivo de la pausa.");
                }
                if (fechaReanudacion == null) {
                    throw new OperacionInvalidaException("Para pausar el proyecto debes indicar la fecha estimada de reanudación.");
                }
                if (!fechaReanudacion.isAfter(hoy)) {
                    throw new OperacionInvalidaException("La fecha estimada de reanudación debe ser posterior a hoy.");
                }
                int salen = sacarColaboradores(p, "finalizada", actorUsuarioId,
                        "El proyecto \"" + p.getNombre() + "\" se pausó hasta el " + formatear(fechaReanudacion)
                                + " (motivo: " + motivoLimpio + "). Tu asignación finalizó y tu carga quedó liberada.");
                int rechazadas = rechazarPropuestasPendientes(p, "El proyecto se pausó.");
                p.setEstado("en_pausa");
                p.setFechaPausa(hoy);
                p.setMotivoPausa(motivoLimpio);
                p.setFechaReanudacionEstimada(fechaReanudacion);
                p.setRazonCancelacion(null);
                proyectoRepository.save(p);
                auditoriaService.registrar(actorUsuarioId, "PROYECTO_PAUSADO", "proyecto", proyectoId,
                        AuditoriaService.json("estado", anterior),
                        "{\"estado\":\"en_pausa\",\"fecha_reanudacion_estimada\":\"" + fechaReanudacion + "\"}",
                        p.getNombre() + " pausado hasta el " + formatear(fechaReanudacion) + " — Motivo: " + motivoLimpio
                                + ". Salen " + salen + " colaborador(es); " + rechazadas + " propuesta(s) pendiente(s) rechazada(s).");
                notificarPm(p, "Proyecto en pausa", "\"" + p.getNombre() + "\" quedó en pausa hasta el " + formatear(fechaReanudacion)
                        + ". El equipo salió del proyecto; al reanudarlo podrás proponer su reincorporación.");
            }
            case "completado", "cancelado" -> {
                boolean cancelar = "cancelado".equals(nuevoEstado);
                if (cancelar && motivoLimpio == null) {
                    throw new OperacionInvalidaException("Para cancelar el proyecto debes indicar una razón.");
                }
                int salen = sacarColaboradores(p, cancelar ? "cancelada" : "finalizada", actorUsuarioId,
                        "El proyecto \"" + p.getNombre() + "\" " + (cancelar ? "fue cancelado" : "se completó")
                                + ". Tu asignación " + (cancelar ? "se canceló" : "finalizó") + " y queda en tu historial.");
                int rechazadas = rechazarPropuestasPendientes(p, cancelar ? "El proyecto fue cancelado." : "El proyecto se completó.");
                p.setEstado(nuevoEstado);
                p.setRazonCancelacion(cancelar ? motivoLimpio : null);
                limpiarPausa(p);
                proyectoRepository.save(p);
                auditoriaService.registrar(actorUsuarioId, cancelar ? "PROYECTO_CANCELADO" : "PROYECTO_COMPLETADO",
                        "proyecto", proyectoId, AuditoriaService.json("estado", anterior), AuditoriaService.json("estado", nuevoEstado),
                        AuditoriaService.conMotivo(p.getNombre() + ". Salen " + salen + " colaborador(es); "
                                + rechazadas + " propuesta(s) pendiente(s) rechazada(s)", motivoLimpio) + ".");
                notificarPm(p, cancelar ? "Proyecto cancelado" : "Proyecto completado",
                        "\"" + p.getNombre() + "\" " + (cancelar ? "fue cancelado" : "se completó")
                                + ". Lo sigues viendo en tu historial como su Project Manager.");
            }
            default -> { // planificacion / activo
                Asignacion pm = pmActivoDe(proyectoId).orElse(null);
                if ("en_pausa".equals(anterior) || estaCerrado(p)) {
                    // Reanudar o reabrir: el proyecto necesita un PM operativo.
                    if (pm == null || !esMiembroAsignable(pm.getPerfil())) {
                        throw new OperacionInvalidaException("\"" + p.getNombre() + "\" no tiene un Project Manager activo. "
                                + "El Resource Manager debe usar \"Cambiar PM\" antes de " + (estaCerrado(p) ? "reabrirlo." : "reanudarlo."));
                    }
                }
                if (estaCerrado(p)) {
                    // Al reabrir, la carga del PM vuelve a contar: debe tener capacidad.
                    int cargaPm = valor(pm.getCargaPorcentaje());
                    int total = sumarCargaActivaSegura(pm.getPerfilId()) + cargaPm;
                    int limite = limiteCargaConfigurado();
                    if (total > limite) {
                        throw new OperacionInvalidaException("No se puede reabrir: el PM " + pm.getPerfil().getUsuario().getNombreCompleto()
                                + " quedaría con " + total + "% de carga (límite " + limite + "%). El Resource Manager debe cambiar el PM o rebalancear su carga.");
                    }
                    if (asignacionRepository.contarProyectosVigentesDePerfil(pm.getPerfilId()) >= maximoProyectosConfigurado()) {
                        throw new OperacionInvalidaException("No se puede reabrir: el PM ya está en el máximo de proyectos vigentes configurado.");
                    }
                }
                String accion = "en_pausa".equals(anterior) ? "PROYECTO_REANUDADO"
                        : estaCerrado(p) ? "PROYECTO_REABIERTO" : "PROYECTO_ESTADO_CAMBIADO";
                p.setEstado(nuevoEstado);
                p.setRazonCancelacion(null);
                limpiarPausa(p);
                proyectoRepository.save(p);
                auditoriaService.registrar(actorUsuarioId, accion, "proyecto", proyectoId,
                        AuditoriaService.json("estado", anterior), AuditoriaService.json("estado", nuevoEstado),
                        AuditoriaService.conMotivo(p.getNombre() + ": " + etiquetaEstado(anterior) + " → " + etiquetaEstado(nuevoEstado), motivoLimpio) + ".");
            }
        }
    }

    /**
     * Saca del proyecto a todos los colaboradores activos (el PM se queda),
     * con el estado indicado y fecha_fin = hoy. No borra filas: el periodo
     * real queda en el historial de cada persona.
     */
    private int sacarColaboradores(Proyecto p, String estadoFinal, Long actorUsuarioId, String aviso) {
        List<Asignacion> colaboradores = asignacionRepository.listarEquipoDeProyecto(p.getId(), "activa").stream()
                .filter(a -> Roles.COLABORADOR.equals(a.getRolEnProyecto()))
                .collect(Collectors.toList());
        for (Asignacion a : colaboradores) {
            a.setEstado(estadoFinal);
            a.setFechaFin(LocalDate.now());
            asignacionRepository.save(a);
            auditoriaService.registrar(actorUsuarioId, "ASIGNACION_FINALIZADA", "asignacion", a.getId(),
                    AuditoriaService.json("estado", "activa"), AuditoriaService.json("estado", estadoFinal),
                    a.getPerfil().getUsuario().getNombreCompleto() + " sale de \"" + p.getNombre() + "\" por cambio de estado del proyecto.");
            notificacionService.crear(a.getPerfilId(), "asignacion", "Saliste de un proyecto", aviso, "proyectos.html");
        }
        return colaboradores.size();
    }

    private int rechazarPropuestasPendientes(Proyecto p, String motivo) {
        List<PropuestaAsignacion> pendientes = propuestaRepository.findByProyectoIdAndEstado(p.getId(), "pendiente");
        for (PropuestaAsignacion prop : pendientes) {
            prop.setEstado("rechazada");
            prop.setMotivoResolucion(motivo + " (rechazo automático)");
            prop.setFechaResolucion(java.time.LocalDateTime.now());
            propuestaRepository.save(prop);
            notificacionService.crear(prop.getSolicitadoPorPerfilId(), "resultado_ia", "Propuesta cerrada automáticamente",
                    "Tu propuesta para \"" + p.getNombre() + "\" se cerró: " + motivo, "ai-talent-matching.html");
        }
        return pendientes.size();
    }

    private void limpiarPausa(Proyecto p) {
        p.setFechaPausa(null);
        p.setMotivoPausa(null);
        p.setFechaReanudacionEstimada(null);
    }

    private void notificarPm(Proyecto p, String titulo, String detalle) {
        pmActivoDe(p.getId()).ifPresent(pm -> notificacionService.crear(pm.getPerfilId(), "asignacion", titulo, detalle, "proyectos.html"));
    }

    /** Historial del equipo de un proyecto (pestaña "Historial", modelo v7 fase 5). */
    @Transactional(readOnly = true)
    public List<com.skillbridge.ai.dto.HistorialEquipoFila> historialEquipo(Long proyectoId) {
        return asignacionRepository.listarHistorialDeProyecto(proyectoId).stream()
                .map(a -> new com.skillbridge.ai.dto.HistorialEquipoFila(
                        a.getPerfil().getUsuario().getNombreCompleto(),
                        com.skillbridge.ai.dto.InicialesUtil.de(a.getPerfil().getUsuario().getNombreCompleto()),
                        Roles.PROJECT_MANAGER.equals(a.getRolEnProyecto()) ? "Project Manager" : "Colaborador",
                        valor(a.getCargaPorcentaje()),
                        formatear(a.getFechaInicio()) + " → " + formatear(a.getFechaFin()),
                        a.getEstado()))
                .collect(Collectors.toList());
    }

    /**
     * Equipo que había justo antes de la última pausa o cierre: los
     * colaboradores que salieron en la fecha más reciente (todos salen el
     * mismo día al pausar/cerrar). Para cada uno se indica si hoy tiene
     * capacidad para volver con la misma dedicación.
     */
    @Transactional(readOnly = true)
    public List<IntegranteAnterior> equipoAnterior(Long proyectoId) {
        Proyecto p = proyectoRepository.findById(proyectoId)
                .orElseThrow(() -> new OperacionInvalidaException("El proyecto ya no existe."));
        return equipoAnterior(p);
    }

    private List<IntegranteAnterior> equipoAnterior(Proyecto p) {
        List<Asignacion> salientes = asignacionRepository.listarColaboradoresSalientes(p.getId());
        if (salientes.isEmpty()) return List.of();
        LocalDate ultimaSalida = salientes.get(0).getFechaFin();
        java.util.Set<Long> activos = asignacionRepository.listarEquipoDeProyecto(p.getId(), "activa").stream()
                .map(Asignacion::getPerfilId).collect(Collectors.toSet());
        int limite = limiteCargaConfigurado();
        int maximo = maximoProyectosConfigurado();
        java.util.Map<Long, IntegranteAnterior> porPerfil = new java.util.LinkedHashMap<>();
        for (Asignacion a : salientes) {
            if (!ultimaSalida.equals(a.getFechaFin()) || activos.contains(a.getPerfilId()) || porPerfil.containsKey(a.getPerfilId())) continue;
            Perfil perfil = a.getPerfil();
            int cargaAnterior = valor(a.getCargaPorcentaje());
            int cargaActual = sumarCargaActivaSegura(a.getPerfilId());
            String motivo = null;
            if (!esMiembroAsignable(perfil)) {
                motivo = "Ya no es un Miembro activo";
            } else if (cargaActual + cargaAnterior > limite) {
                motivo = "Tiene " + cargaActual + "% de carga; con " + cargaAnterior + "% llegaría a " + (cargaActual + cargaAnterior) + "%";
            } else if (asignacionRepository.contarProyectosVigentesDePerfil(a.getPerfilId()) >= maximo) {
                motivo = "Está en el máximo de " + maximo + " proyectos vigentes";
            }
            porPerfil.put(a.getPerfilId(), new IntegranteAnterior(a.getPerfilId(), perfil.getUsuario().getNombreCompleto(),
                    perfil.getCargo() != null ? perfil.getCargo() : "", cargaAnterior, cargaActual, motivo == null, motivo));
        }
        return new ArrayList<>(porPerfil.values());
    }

    /**
     * Edita los datos propios del proyecto (nombre, descripcion, tecnologias,
     * fechas y estado) - antes solo actualizaba nombre/estado y no estaba
     * conectado a ningun boton del frontend; ahora es el "Editar proyecto"
     * de la tabla de Proyectos del PM/Admin.
     */
    @Transactional
    public void editarProyecto(Long proyectoId, String nombre, String descripcion, List<String> tecnologias,
                                LocalDate fechaInicio, LocalDate fechaFinEstimada, String githubUrl, Long actorUsuarioId) {
        Proyecto p = proyectoRepository.findById(proyectoId)
                .orElseThrow(() -> new OperacionInvalidaException("El proyecto ya no existe."));
        if (nombre == null || nombre.trim().isEmpty()) {
            throw new OperacionInvalidaException("El nombre del proyecto no puede estar vacío.");
        }
        if (proyectoRepository.existsByNombreIgnoreCase(nombre.trim())
                && !nombre.trim().equalsIgnoreCase(p.getNombre())) {
            throw new OperacionInvalidaException("Ya existe un proyecto con ese nombre.");
        }
        // Modelo v7 (fase 2): el estado ya NO se edita aquí. Cada transición
        // tiene efectos (sacar al equipo, rechazar propuestas, reincorporar),
        // así que pasa siempre por cambiarEstado(...).
        if (fechaInicio == null) {
            throw new OperacionInvalidaException("La fecha de inicio es obligatoria.");
        }
        // No se exige fechaInicio >= hoy aqui a proposito (editar un proyecto
        // ya en curso debe poder conservar su fecha de inicio pasada), pero
        // el limite a futuro SI aplica igual que en crear() - ver comentario
        // de INICIO_MAXIMO_DIAS_A_FUTURO.
        if (fechaInicio.isAfter(LocalDate.now().plusDays(INICIO_MAXIMO_DIAS_A_FUTURO))) {
            throw new OperacionInvalidaException("La fecha de inicio no puede planificarse a más de "
                    + (INICIO_MAXIMO_DIAS_A_FUTURO / 365) + " año desde hoy.");
        }
        if (fechaFinEstimada == null) {
            throw new OperacionInvalidaException("La fecha de fin estimada es obligatoria.");
        }
        if (fechaFinEstimada.isBefore(fechaInicio)) {
            throw new OperacionInvalidaException("La fecha de fin estimada no puede ser anterior al inicio.");
        }
        long duracionEditar = ChronoUnit.DAYS.between(fechaInicio, fechaFinEstimada);
        if (duracionEditar < DURACION_MINIMA_DIAS) {
            throw new OperacionInvalidaException("Debe haber al menos " + DURACION_MINIMA_DIAS + " días de diferencia entre la fecha de inicio y la fecha de fin.");
        }
        if (duracionEditar > DURACION_MAXIMA_DIAS) {
            throw new OperacionInvalidaException("La duración no puede superar los " + (DURACION_MAXIMA_DIAS / 365) + " años (" + DURACION_MAXIMA_DIAS + " días). Para proyectos más largos, divídelo en varios proyectos.");
        }
        p.setNombre(nombre.trim());
        p.setDescripcion(descripcion != null && !descripcion.isBlank() ? descripcion.trim() : null);
        p.setTecnologias(serializarTecnologias(tecnologias));
        p.setFechaInicio(fechaInicio);
        p.setFechaFinEstimada(fechaFinEstimada);
        p.setGithubUrl(githubUrl != null && !githubUrl.isBlank() ? githubUrl.trim() : null);
        proyectoRepository.save(p);
        auditoriaService.registrar(actorUsuarioId, "PROYECTO_EDITADO", "proyecto", proyectoId, null, null,
                "Datos del proyecto \"" + p.getNombre() + "\" actualizados.");
    }

    /**
     * Asigna a un Miembro como COLABORADOR de un proyecto (asignación
     * directa del Resource Manager, o aprobación de una propuesta del PM).
     *
     * Modelo v7: el rol Project Manager NUNCA se otorga por aquí; solo al
     * crear el proyecto (crear) o con cambiar/transferir el PM
     * (reasignarProjectManager / transferirLiderazgo).
     */
    @Transactional
    public void asignarColaborador(Long proyectoId, Long perfilId, int cargaPorcentaje,
                                    LocalDate fechaInicio, Long actorUsuarioId) {
        Proyecto p = proyectoRepository.findById(proyectoId)
                .orElseThrow(() -> new OperacionInvalidaException("El proyecto ya no existe."));
        if (!admiteNuevosIntegrantes(p)) {
            throw new OperacionInvalidaException("\"" + p.getNombre() + "\" está " + etiquetaEstado(p.getEstado())
                    + ": solo se puede sumar personas a proyectos en Planificación o Activo.");
        }
        Perfil perfil = perfilRepository.findById(perfilId)
                .orElseThrow(() -> new OperacionInvalidaException("Selecciona un colaborador válido."));
        // Defensa en el backend (no solo en el selector, que ya los oculta).
        if (!esMiembroAsignable(perfil)) {
            throw new OperacionInvalidaException(perfil.getUsuario().getNombreCompleto()
                    + " no puede ser asignado: solo Miembros con cuenta activa participan en proyectos "
                    + "(Administradores y Resource Managers nunca).");
        }
        long colaboradoresActivos = asignacionRepository.countByProyectoIdAndEstadoAndRolEnProyecto(
                proyectoId, "activa", Roles.COLABORADOR);
        if (colaboradoresActivos >= requeridos(p)) {
            throw new OperacionInvalidaException("El proyecto ya tiene cubiertas sus "
                    + requeridos(p) + " vacante(s) de colaborador.");
        }
        int limiteCarga = limiteCargaConfigurado();
        if (cargaPorcentaje < 1 || cargaPorcentaje > limiteCarga) {
            throw new OperacionInvalidaException("La dedicación debe estar entre 1% y " + limiteCarga + "% (límite configurado en Configuración).");
        }
        Optional<Asignacion> existente = asignacionRepository.findByProyectoIdAndPerfilIdAndEstado(proyectoId, perfilId, "activa");
        if (existente.isPresent()) {
            throw new OperacionInvalidaException(perfil.getUsuario().getNombreCompleto() + " ya tiene una asignación activa en este proyecto.");
        }
        exigirCapacidad(perfil, cargaPorcentaje);

        Asignacion a = new Asignacion();
        a.setProyectoId(proyectoId);
        a.setPerfilId(perfilId);
        a.setRolEnProyecto(Roles.COLABORADOR);
        a.setCargaPorcentaje(cargaPorcentaje);
        a.setEstado("activa");
        a.setFechaInicio(fechaInicio != null ? fechaInicio : LocalDate.now());
        asignacionRepository.save(a);
        // RF05: si la IA había recomendado a esta persona, la recomendación fue acertada
        recomendacionIaLogRepository.marcarAsignado(proyectoId, perfilId);

        auditoriaService.registrar(actorUsuarioId, "ASIGNACION_CREADA", "asignacion", a.getId(), null,
                AuditoriaService.json("rol_en_proyecto", Roles.COLABORADOR),
                perfil.getUsuario().getNombreCompleto() + " asignado a \"" + p.getNombre() + "\" como colaborador (" + cargaPorcentaje + "%).");
        notificacionService.crear(perfilId, "asignacion", "Nueva asignación de proyecto",
                "Fuiste asignado a \"" + p.getNombre() + "\" como colaborador con " + cargaPorcentaje + "% de dedicación.",
                "proyectos.html");
    }

    @Transactional
    public void finalizarAsignacion(Long asignacionId, Long actorUsuarioId) {
        finalizarAsignacion(asignacionId, null, actorUsuarioId);
    }

    /** El motivo (opcional) queda en el detalle de Auditoría. */
    @Transactional
    public void finalizarAsignacion(Long asignacionId, String motivo, Long actorUsuarioId) {
        Asignacion a = asignacionRepository.findById(asignacionId)
                .orElseThrow(() -> new OperacionInvalidaException("La asignación ya no existe."));

        // Un proyecto vigente NUNCA queda sin Project Manager. Modelo v7: la
        // fila del PM nunca se finaliza a mano; se reemplaza con "Cambiar PM"
        // (Resource Manager) o "Transferir liderazgo" (el propio PM), que
        // hacen el reemplazo completo en una sola transacción.
        if (Roles.PROJECT_MANAGER.equals(a.getRolEnProyecto()) && "activa".equals(a.getEstado())) {
            throw new OperacionInvalidaException(
                    "No se puede finalizar la asignación del Project Manager. Usa \"Cambiar Project Manager\" o \"Transferir liderazgo\" para reemplazarlo.");
        }
        if (!"activa".equals(a.getEstado())) {
            throw new OperacionInvalidaException("Esa asignación ya no está activa.");
        }

        a.setEstado("finalizada");
        a.setFechaFin(LocalDate.now());
        asignacionRepository.save(a);
        auditoriaService.registrar(actorUsuarioId, "ASIGNACION_FINALIZADA", "asignacion", asignacionId, null, null,
                AuditoriaService.conMotivo("Asignación finalizada", motivo) + ".");
        notificacionService.crear(a.getPerfilId(), "asignacion", "Asignación finalizada",
                "Tu asignación al proyecto fue marcada como finalizada.", "proyectos.html");
    }

    /**
     * "Cambiar PM" del Resource Manager: cualquier proyecto vigente, a favor
     * de cualquier Miembro activo con capacidad (si ya está en el equipo, se
     * asciende su misma fila y conserva su dedicación; si no, entra con 20%).
     *
     * @param mantenerSaliente true = el PM saliente se queda en el proyecto
     *        como colaborador (requiere vacante); false = su asignación se finaliza.
     * @param cargaSaliente dedicación con la que se queda (null = la misma que tenía).
     */
    @Transactional
    public void reasignarProjectManager(Long proyectoId, Long nuevoPmPerfilId, boolean mantenerSaliente,
                                        Integer cargaSaliente, Long actorUsuarioId) {
        reasignarProjectManager(proyectoId, nuevoPmPerfilId, mantenerSaliente, cargaSaliente, null, actorUsuarioId);
    }

    /**
     * Igual, pero si el equipo está completo se puede liberar un cupo en el
     * mismo paso: se finaliza la asignación de colaborador liberarAsignacionId
     * (de este proyecto, que no sea el nuevo PM) para que el PM saliente se
     * quede como colaborador. Todo ocurre en una sola transacción.
     */
    @Transactional
    public void reasignarProjectManager(Long proyectoId, Long nuevoPmPerfilId, boolean mantenerSaliente,
                                        Integer cargaSaliente, Long liberarAsignacionId, Long actorUsuarioId) {
        cambiarLiderazgo(proyectoId, nuevoPmPerfilId, mantenerSaliente, cargaSaliente, liberarAsignacionId, actorUsuarioId, false);
    }

    /**
     * "Transferir liderazgo" del propio PM: solo a un colaborador activo de
     * SU equipo. Es directo (sin aprobación del RM) porque nadie suma carga:
     * cada uno conserva su porcentaje y solo cambia rol_en_proyecto.
     */
    @Transactional
    public void transferirLiderazgo(Long proyectoId, Long pmPerfilId, Long nuevoPmPerfilId, boolean mantenerSaliente,
                                    Integer cargaSaliente, Long actorUsuarioId) {
        Asignacion actual = pmActivoDe(proyectoId)
                .orElseThrow(() -> new OperacionInvalidaException("El proyecto no tiene un Project Manager activo."));
        if (!actual.getPerfilId().equals(pmPerfilId)) {
            throw new OperacionInvalidaException("Solo el Project Manager del proyecto puede transferir su liderazgo.");
        }
        asignacionRepository.findByProyectoIdAndPerfilIdAndEstado(proyectoId, nuevoPmPerfilId, "activa")
                .filter(a -> Roles.COLABORADOR.equals(a.getRolEnProyecto()))
                .orElseThrow(() -> new OperacionInvalidaException(
                        "Solo puedes transferir el liderazgo a un colaborador activo de tu equipo."));
        cambiarLiderazgo(proyectoId, nuevoPmPerfilId, mantenerSaliente, cargaSaliente, null, actorUsuarioId, true);
    }

    /**
     * Núcleo común de cambiar/transferir el PM, en una sola transacción.
     * Todo pasa sobre filas de asignaciones del MISMO proyecto (UPDATE de
     * rol_en_proyecto), así nunca choca con uq_asig_clave_activa y el
     * historial de cada persona queda intacto.
     */
    private void cambiarLiderazgo(Long proyectoId, Long nuevoPmPerfilId, boolean mantenerSaliente,
                                  Integer cargaSaliente, Long liberarAsignacionId, Long actorUsuarioId,
                                  boolean porTransferencia) {
        Proyecto p = proyectoRepository.findById(proyectoId)
                .orElseThrow(() -> new OperacionInvalidaException("El proyecto ya no existe."));
        // En un proyecto cerrado solo el Resource Manager puede cambiar el PM
        // (para poder reabrirlo); el equipo ya salió, así que el PM saliente
        // no puede "quedarse como colaborador".
        boolean cerrado = estaCerrado(p);
        if (cerrado && porTransferencia) {
            throw new OperacionInvalidaException("\"" + p.getNombre() + "\" está " + etiquetaEstado(p.getEstado())
                    + ": no se puede transferir su liderazgo.");
        }
        if (cerrado) mantenerSaliente = false;
        Perfil nuevoPm = perfilRepository.findById(nuevoPmPerfilId)
                .orElseThrow(() -> new OperacionInvalidaException("Selecciona un Project Manager válido."));
        if (!esMiembroAsignable(nuevoPm)) {
            throw new OperacionInvalidaException(nuevoPm.getUsuario().getNombreCompleto()
                    + " no puede liderar proyectos: solo un Miembro activo (sin rol de Administrador o Resource Manager) puede ser Project Manager.");
        }
        Optional<Asignacion> pmActual = pmActivoDe(proyectoId);
        if (pmActual.isPresent() && pmActual.get().getPerfilId().equals(nuevoPmPerfilId)) {
            throw new OperacionInvalidaException(nuevoPm.getUsuario().getNombreCompleto() + " ya es el Project Manager de este proyecto.");
        }
        Optional<Asignacion> filaNuevo = asignacionRepository.findByProyectoIdAndPerfilIdAndEstado(proyectoId, nuevoPmPerfilId, "activa");
        boolean nuevoYaEstaEnElEquipo = filaNuevo.isPresent();
        if (!nuevoYaEstaEnElEquipo && !cerrado) {
            // Entra de fuera con la dedicación estándar de PM: debe tener
            // capacidad (en un proyecto cerrado su carga no cuenta hasta que
            // se reabra, y reabrir vuelve a validarla).
            exigirCapacidad(nuevoPm, CARGA_PM_POR_DEFECTO);
        }

        String nombreNuevo = nuevoPm.getUsuario().getNombreCompleto();
        String nombreSaliente = pmActual.map(a -> a.getPerfil().getUsuario().getNombreCompleto()).orElse(null);
        int limiteCarga = limiteCargaConfigurado();

        // 1) PM saliente: se queda como colaborador (si hay vacante) o sale.
        if (pmActual.isPresent()) {
            Asignacion saliente = pmActual.get();
            if (mantenerSaliente && liberarAsignacionId != null) {
                // Liberar un cupo: finalizar a un colaborador de ESTE proyecto (nunca al nuevo PM).
                Asignacion liberada = asignacionRepository.findById(liberarAsignacionId)
                        .filter(a -> a.getProyectoId().equals(proyectoId) && "activa".equals(a.getEstado())
                                && Roles.COLABORADOR.equals(a.getRolEnProyecto()))
                        .orElseThrow(() -> new OperacionInvalidaException(
                                "La persona elegida para liberar el cupo ya no es colaboradora activa de este proyecto."));
                if (liberada.getPerfilId().equals(nuevoPmPerfilId)) {
                    throw new OperacionInvalidaException("No puedes liberar el cupo del nuevo Project Manager.");
                }
                finalizarAsignacion(liberada.getId(), "Libera un cupo para que " + nombreSaliente
                        + " siga como colaborador al cambiar de Project Manager.", actorUsuarioId);
            }
            if (mantenerSaliente) {
                long colaboradores = asignacionRepository.countByProyectoIdAndEstadoAndRolEnProyecto(
                        proyectoId, "activa", Roles.COLABORADOR);
                // Si el nuevo PM era colaborador, su cupo se libera al ascender.
                long ocupadas = colaboradores - (nuevoYaEstaEnElEquipo ? 1 : 0);
                if (ocupadas >= requeridos(p)) {
                    throw new OperacionInvalidaException("No hay vacante para mantener a " + nombreSaliente
                            + " como colaborador (el proyecto requiere " + requeridos(p)
                            + "). Desmarca \"Mantener como colaborador\", elige a quién liberar el cupo o amplía las vacantes del proyecto.");
                }
                int anterior = valor(saliente.getCargaPorcentaje());
                int nueva = cargaSaliente != null ? cargaSaliente : anterior;
                if (nueva < 1 || nueva > limiteCarga) {
                    throw new OperacionInvalidaException("La dedicación de " + nombreSaliente + " debe estar entre 1% y " + limiteCarga + "%.");
                }
                if (nueva > anterior) {
                    int total = sumarCargaActivaSegura(saliente.getPerfilId()) - anterior + nueva;
                    if (total > limiteCarga) {
                        throw new OperacionInvalidaException("Con " + nueva + "% la carga de " + nombreSaliente
                                + " sería " + total + "%, por encima del límite de " + limiteCarga + "%.");
                    }
                }
                saliente.setRolEnProyecto(Roles.COLABORADOR);
                saliente.setCargaPorcentaje(nueva);
                asignacionRepository.save(saliente);
                auditoriaService.registrar(actorUsuarioId, "ASIGNACION_ROL_CAMBIADO", "asignacion", saliente.getId(),
                        AuditoriaService.json("rol_en_proyecto", Roles.PROJECT_MANAGER),
                        AuditoriaService.json("rol_en_proyecto", Roles.COLABORADOR),
                        nombreSaliente + " deja de liderar \"" + p.getNombre() + "\" y sigue como colaborador (" + nueva + "%).");
                notificacionService.crear(saliente.getPerfilId(), "asignacion", "Cambio de Project Manager",
                        nombreNuevo + " es ahora el Project Manager de \"" + p.getNombre() + "\". Sigues en el equipo como colaborador ("
                                + nueva + "%).", "proyectos.html");
            } else {
                saliente.setEstado("finalizada");
                saliente.setFechaFin(LocalDate.now());
                asignacionRepository.save(saliente);
                auditoriaService.registrar(actorUsuarioId, "ASIGNACION_FINALIZADA", "asignacion", saliente.getId(),
                        AuditoriaService.json("rol_en_proyecto", Roles.PROJECT_MANAGER), null,
                        nombreSaliente + " deja de liderar \"" + p.getNombre() + "\" y sale del proyecto.");
                notificacionService.crear(saliente.getPerfilId(), "asignacion", "Cambio de Project Manager",
                        nombreNuevo + " es ahora el Project Manager de \"" + p.getNombre() + "\". Tu asignación en ese proyecto finalizó.",
                        "proyectos.html");
            }
        }

        // 2) Nuevo PM: se asciende su misma fila (conserva su %) o entra con 20%.
        Asignacion filaPm;
        if (nuevoYaEstaEnElEquipo) {
            filaPm = filaNuevo.get();
            filaPm.setRolEnProyecto(Roles.PROJECT_MANAGER);
            asignacionRepository.save(filaPm);
            auditoriaService.registrar(actorUsuarioId, "ASIGNACION_ROL_CAMBIADO", "asignacion", filaPm.getId(),
                    AuditoriaService.json("rol_en_proyecto", Roles.COLABORADOR),
                    AuditoriaService.json("rol_en_proyecto", Roles.PROJECT_MANAGER),
                    nombreNuevo + " pasa a liderar \"" + p.getNombre() + "\" (conserva su " + valor(filaPm.getCargaPorcentaje()) + "%).");
        } else {
            filaPm = new Asignacion();
            filaPm.setProyectoId(proyectoId);
            filaPm.setPerfilId(nuevoPmPerfilId);
            filaPm.setRolEnProyecto(Roles.PROJECT_MANAGER);
            filaPm.setCargaPorcentaje(CARGA_PM_POR_DEFECTO);
            filaPm.setEstado("activa");
            filaPm.setFechaInicio(LocalDate.now());
            asignacionRepository.save(filaPm);
            auditoriaService.registrar(actorUsuarioId, "ASIGNACION_CREADA", "asignacion", filaPm.getId(), null,
                    AuditoriaService.json("rol_en_proyecto", Roles.PROJECT_MANAGER),
                    nombreNuevo + " entra a \"" + p.getNombre() + "\" como Project Manager (" + CARGA_PM_POR_DEFECTO + "%).");
        }

        auditoriaService.registrar(actorUsuarioId, "PM_REASIGNADO", "proyecto", proyectoId,
                AuditoriaService.json("pm", nombreSaliente), AuditoriaService.json("pm", nombreNuevo),
                (porTransferencia ? "Transferencia de liderazgo" : "Cambio de Project Manager") + " en \"" + p.getNombre() + "\": "
                        + (nombreSaliente != null ? nombreSaliente : "sin PM") + " → " + nombreNuevo + ".");
        notificacionService.crear(nuevoPmPerfilId, "asignacion", "Ahora lideras un proyecto",
                "Eres el Project Manager de \"" + p.getNombre() + "\". Lo encontrarás en tu vista Project Manager.",
                "proyectos.html");
    }

    // ───────────────────────── Helpers ─────────────────────────

    /**
     * Rango de duracion permitido entre fecha de inicio y fecha de fin
     * estimada (crear() y editarProyecto()). El minimo evita proyectos de
     * unos pocos dias que no tiene sentido trackear con entregables/foros/
     * AI Talent Matching; el maximo evita el caso real detectado en la
     * demo (un proyecto de mas de 1000 años) - un proyecto de software que
     * dura mas de esto en SkillBridge AI deberia partirse en varios
     * proyectos en vez de seguir siendo "uno solo".
     */
    public static final long DURACION_MINIMA_DIAS = 7;
    public static final long DURACION_MAXIMA_DIAS = 1095; // 3 años

    /**
     * Cuanto se puede planificar un inicio A FUTURO desde hoy (distinto de
     * DURACION_MAXIMA_DIAS, que es sobre cuanto dura el proyecto UNA VEZ que
     * empieza). Sin este limite alguien puede crear un proyecto que arranca
     * dentro de 1000 años: la fecha de inicio ya no se valida solo contra
     * "no antes de hoy" sino tambien contra un horizonte razonable, porque
     * al crear el proyecto ya se compromete un PM y la disponibilidad de
     * colaboradores que calcula TalentMatchingService EN ESTE MOMENTO - esa
     * disponibilidad no significa nada planificada a mas de un año vista.
     */
    public static final long INICIO_MAXIMO_DIAS_A_FUTURO = 365; // 1 año

    /** Dedicación estándar con la que entra un Project Manager. */
    public static final int CARGA_PM_POR_DEFECTO = 20;
    /** Tope duro de carga (modelo v7): nadie supera el 100%. */
    public static final int LIMITE_CARGA_MAXIMO = ConfiguracionService.LIMITE_CARGA_MAXIMO;
    /** Máximo de proyectos vigentes simultáneos para un PM (valor fijo). */
    public static final int MAXIMO_PROYECTOS_PM = 3;

    /**
     * ¿Puede esta persona participar en proyectos? Cuenta y perfil activos y
     * SIN rol organizacional: Administrador y Resource Manager nunca tienen
     * filas en asignaciones (modelo v7).
     */
    public static boolean esMiembroAsignable(Perfil p) {
        return p != null && p.getUsuario() != null
                && p.getUsuario().isActivo()
                && p.getUsuario().getRolOrganizacional() == null
                && "activo".equalsIgnoreCase(p.getEstado());
    }

    /**
     * Valida que la persona tenga capacidad para sumar un proyecto vigente
     * más con la dedicación indicada (límite de carga y máximo de proyectos
     * simultáneos de Configuración, ambos sobre la carga EFECTIVA).
     */
    private void exigirCapacidad(Perfil perfil, int dedicacion) {
        int limiteCarga = limiteCargaConfigurado();
        int maximoProyectos = maximoProyectosConfigurado();
        String nombre = perfil.getUsuario().getNombreCompleto();
        long vigentes = asignacionRepository.contarProyectosVigentesDePerfil(perfil.getId());
        if (vigentes >= maximoProyectos) {
            throw new OperacionInvalidaException(nombre + " ya está en " + vigentes
                    + " proyecto(s) vigente(s), el máximo configurado es " + maximoProyectos + ".");
        }
        int cargaActual = sumarCargaActivaSegura(perfil.getId());
        if (cargaActual + dedicacion > limiteCarga) {
            throw new OperacionInvalidaException(nombre + " tiene " + cargaActual + "% de carga; con "
                    + dedicacion + "% más llegaría a " + (cargaActual + dedicacion) + "%, por encima del límite de " + limiteCarga + "%.");
        }
    }

    /** PM activo del proyecto (el primero, por si un dato heredado tuviera dos). */
    private Optional<Asignacion> pmActivoDe(Long proyectoId) {
        return asignacionRepository.buscarResponsablesActivos(proyectoId, Roles.PROJECT_MANAGER).stream().findFirst();
    }

    static boolean estaCerrado(Proyecto p) {
        return "completado".equals(p.getEstado()) || "cancelado".equals(p.getEstado());
    }

    static boolean admiteNuevosIntegrantes(Proyecto p) {
        return "planificacion".equals(p.getEstado()) || "activo".equals(p.getEstado());
    }

    static String etiquetaEstado(String estado) {
        if (estado == null) return "sin estado";
        return switch (estado) {
            case "planificacion" -> "en Planificación";
            case "activo" -> "Activo";
            case "en_pausa" -> "En pausa";
            case "completado" -> "Completado";
            case "cancelado" -> "Cancelado";
            default -> estado;
        };
    }

    private static int requeridos(Proyecto p) {
        return p.getColaboradoresRequeridos() != null ? p.getColaboradoresRequeridos() : 0;
    }

    private static int valor(Integer v) {
        return v != null ? v : 0;
    }

    static int avanceHeuristico(LocalDate inicio, LocalDate finEstimada, String estado) {
        if ("completado".equals(estado)) return 100;
        if ("cancelado".equals(estado)) return 0;
        if (inicio == null || finEstimada == null) return 0;
        long total = ChronoUnit.DAYS.between(inicio, finEstimada);
        if (total <= 0) return 100;
        long transcurrido = ChronoUnit.DAYS.between(inicio, LocalDate.now());
        if (transcurrido <= 0) return 0;
        if (transcurrido >= total) return 100;
        return (int) Math.round((transcurrido * 100.0) / total);
    }

    private String formatear(LocalDate fecha) {
        return fecha != null ? fecha.format(FECHA_FMT) : "—";
    }

    private int parseEntero(String texto, int porDefecto) {
        try {
            return Integer.parseInt(texto.trim());
        } catch (Exception ex) {
            return porDefecto;
        }
    }

    private int sumarCargaActivaSegura(Long perfilId) {
        Integer suma = asignacionRepository.sumarCargaActivaDePerfil(perfilId);
        return suma != null ? suma : 0;
    }

    /** Carga activa total (%) de un colaborador - usada en colaborador/inicio.html y perfil.html. */
    public int cargaActivaDe(Long perfilId) {
        return sumarCargaActivaSegura(perfilId);
    }

    private List<String> parseTecnologias(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception ex) {
            return List.of();
        }
    }

    private String serializarTecnologias(List<String> tecnologias) {
        try {
            return objectMapper.writeValueAsString(tecnologias == null ? List.of() : tecnologias);
        } catch (Exception ex) {
            return "[]";
        }
    }
}
