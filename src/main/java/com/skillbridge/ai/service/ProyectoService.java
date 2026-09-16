package com.skillbridge.ai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillbridge.ai.dto.MiProyectoFila;
import com.skillbridge.ai.dto.MiembroEquipoFila;
import com.skillbridge.ai.dto.PerfilOpcion;
import com.skillbridge.ai.dto.ProyectoDetalle;
import com.skillbridge.ai.dto.ProyectoFila;
import com.skillbridge.ai.model.Asignacion;
import com.skillbridge.ai.model.Perfil;
import com.skillbridge.ai.model.Proyecto;
import com.skillbridge.ai.repository.AsignacionRepository;
import com.skillbridge.ai.repository.PerfilRepository;
import com.skillbridge.ai.repository.ProyectoRepository;
import com.skillbridge.ai.util.OperacionInvalidaException;
import com.skillbridge.ai.util.Roles;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * CRUD real de Proyectos (RF03) + asignación de colaboradores (RF03/RF04).
 *
 * No existe columna de "% de avance" en el esquema (ver comentario de la
 * tabla proyectos en skillbridge_db_v4.sql): se calcula un heurístico de
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
    private final AuditoriaService auditoriaService;
    private final NotificacionService notificacionService;
    private final ConfiguracionService configuracionService;
    private final ObjectMapper objectMapper;

    public ProyectoService(ProyectoRepository proyectoRepository, AsignacionRepository asignacionRepository,
                            PerfilRepository perfilRepository, AuditoriaService auditoriaService,
                            NotificacionService notificacionService, ConfiguracionService configuracionService,
                            ObjectMapper objectMapper) {
        this.proyectoRepository = proyectoRepository;
        this.asignacionRepository = asignacionRepository;
        this.perfilRepository = perfilRepository;
        this.auditoriaService = auditoriaService;
        this.notificacionService = notificacionService;
        this.configuracionService = configuracionService;
        this.objectMapper = objectMapper;
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
        return new ProyectoDetalle(p.getId(), p.getNombre(), p.getDescripcion(), parseTecnologias(p.getTecnologias()),
                p.getEstado(), formatear(p.getFechaInicio()), formatear(p.getFechaFinEstimada()), avance,
                p.getColaboradoresRequeridos(), pmNombre, equipo);
    }

    private String nombrePmActivo(Long proyectoId) {
        return asignacionRepository.buscarResponsableActivo(proyectoId, Roles.PROJECT_MANAGER)
                .map(a -> a.getPerfil().getUsuario().getNombreCompleto())
                .orElse(null);
    }

    public List<PerfilOpcion> listarPerfilesParaAsignar() {
        return perfilRepository.listarActivosConUsuario().stream()
                .map(p -> new PerfilOpcion(p.getId(), p.getUsuario().getNombreCompleto(), p.getUsuario().getCorreo()))
                .collect(Collectors.toList());
    }

    // ─────────────────── "Mis proyectos" (Colaborador) ───────────────────

    public List<MiProyectoFila> misProyectosActivos(Long perfilId) {
        return asignacionRepository.listarPorPerfilYEstado(perfilId, "activa").stream()
                .map(this::aMiProyectoFila)
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
                           Long actorUsuarioId) {
        if (nombre == null || nombre.isBlank()) {
            throw new OperacionInvalidaException("Ingresa un nombre para el proyecto.");
        }
        if (fechaInicio == null) {
            throw new OperacionInvalidaException("La fecha de inicio es obligatoria.");
        }
        if (fechaFinEstimada != null && fechaFinEstimada.isBefore(fechaInicio)) {
            throw new OperacionInvalidaException("La fecha de fin estimada no puede ser anterior al inicio.");
        }
        Perfil pm = perfilRepository.findById(pmPerfilId)
                .orElseThrow(() -> new OperacionInvalidaException("Selecciona un Project Manager válido."));

        Proyecto p = new Proyecto();
        p.setNombre(nombre.trim());
        p.setDescripcion(descripcion);
        p.setTecnologias(serializarTecnologias(tecnologias));
        p.setEstado("planificacion");
        p.setColaboradoresRequeridos(Math.max(0, colaboradoresRequeridos));
        p.setFechaInicio(fechaInicio);
        p.setFechaFinEstimada(fechaFinEstimada);
        p = proyectoRepository.save(p);

        // Nota de integridad del esquema v4: el PM se registra vía asignaciones,
        // no hay proyectos.creado_por_id (ver skillbridge_db_v4.sql, sección 7).
        Asignacion pmAsig = new Asignacion();
        pmAsig.setProyectoId(p.getId());
        pmAsig.setPerfilId(pm.getId());
        pmAsig.setRolEnProyecto(Roles.PROJECT_MANAGER);
        pmAsig.setCargaPorcentaje(20);
        pmAsig.setEstado("activa");
        pmAsig.setFechaInicio(fechaInicio);
        asignacionRepository.save(pmAsig);

        auditoriaService.registrar(actorUsuarioId, "PROYECTO_CREADO", "proyecto", p.getId(), null, null,
                "Proyecto \"" + p.getNombre() + "\" creado con PM " + pm.getUsuario().getNombreCompleto() + ".");
        notificacionService.crear(pm.getId(), "asignacion", "Nuevo proyecto asignado",
                "Se te asignó como Project Manager de \"" + p.getNombre() + "\".", "proyectos.html");

        return p;
    }

    @Transactional
    public void cambiarEstado(Long proyectoId, String nuevoEstado, Long actorUsuarioId) {
        Proyecto p = proyectoRepository.findById(proyectoId)
                .orElseThrow(() -> new OperacionInvalidaException("El proyecto ya no existe."));
        List<String> validos = List.of("planificacion", "activo", "en_pausa", "completado", "cancelado");
        if (!validos.contains(nuevoEstado)) {
            throw new OperacionInvalidaException("Estado de proyecto no reconocido.");
        }
        String anterior = p.getEstado();
        p.setEstado(nuevoEstado);
        proyectoRepository.save(p);
        auditoriaService.registrar(actorUsuarioId, "PROYECTO_ESTADO_CAMBIADO", "proyecto", proyectoId,
                AuditoriaService.json("estado", anterior), AuditoriaService.json("estado", nuevoEstado), p.getNombre());
    }

    @Transactional
    public void asignarColaborador(Long proyectoId, Long perfilId, String rolEnProyecto, int cargaPorcentaje,
                                    LocalDate fechaInicio, Long actorUsuarioId) {
        Proyecto p = proyectoRepository.findById(proyectoId)
                .orElseThrow(() -> new OperacionInvalidaException("El proyecto ya no existe."));
        Perfil perfil = perfilRepository.findById(perfilId)
                .orElseThrow(() -> new OperacionInvalidaException("Selecciona un colaborador válido."));
        if (!Roles.PROJECT_MANAGER.equals(rolEnProyecto) && !Roles.COLABORADOR.equals(rolEnProyecto)) {
            throw new OperacionInvalidaException("Rol de proyecto no reconocido.");
        }
        // Límites configurables (RF08, configuracion_global) en vez de topes
        // fijos: así "Configuración" deja de ser un panel decorativo y
        // efectivamente restringe estas dos operaciones.
        var configuracion = configuracionService.obtenerMapa();
        int limiteCarga = parseEntero(configuracionService.valor(configuracion, "limite_carga_colaborador", "100"), 100);
        int maximoProyectos = parseEntero(configuracionService.valor(configuracion, "maximo_proyectos_simultaneos", "3"), 3);

        if (cargaPorcentaje < 1 || cargaPorcentaje > limiteCarga) {
            throw new OperacionInvalidaException("La dedicación debe estar entre 1% y " + limiteCarga + "% (límite configurado en Configuración).");
        }
        Optional<Asignacion> existente = asignacionRepository.findByProyectoIdAndPerfilIdAndEstado(proyectoId, perfilId, "activa");
        if (existente.isPresent()) {
            throw new OperacionInvalidaException(perfil.getUsuario().getNombreCompleto() + " ya tiene una asignación activa en este proyecto.");
        }
        int proyectosActivosDelPerfil = asignacionRepository.listarPorPerfilYEstado(perfilId, "activa").size();
        if (proyectosActivosDelPerfil >= maximoProyectos) {
            throw new OperacionInvalidaException(perfil.getUsuario().getNombreCompleto() + " ya está en " + proyectosActivosDelPerfil +
                    " proyecto(s) activo(s), el máximo configurado es " + maximoProyectos + ".");
        }
        int cargaActual = sumarCargaActivaSegura(perfilId);
        if (cargaActual + cargaPorcentaje > limiteCarga) {
            throw new OperacionInvalidaException(perfil.getUsuario().getNombreCompleto() + " ya tiene " + cargaActual +
                    "% de carga activa; esta asignación superaría el límite de " + limiteCarga + "%.");
        }

        Asignacion a = new Asignacion();
        a.setProyectoId(proyectoId);
        a.setPerfilId(perfilId);
        a.setRolEnProyecto(rolEnProyecto);
        a.setCargaPorcentaje(cargaPorcentaje);
        a.setEstado("activa");
        a.setFechaInicio(fechaInicio != null ? fechaInicio : LocalDate.now());
        asignacionRepository.save(a);

        auditoriaService.registrar(actorUsuarioId, "ASIGNACION_CREADA", "asignacion", a.getId(), null, null,
                perfil.getUsuario().getNombreCompleto() + " asignado a \"" + p.getNombre() + "\" (" + cargaPorcentaje + "%).");
        notificacionService.crear(perfilId, "asignacion", "Nueva asignación de proyecto",
                "Fuiste asignado a \"" + p.getNombre() + "\" como " +
                        (Roles.PROJECT_MANAGER.equals(rolEnProyecto) ? "Project Manager" : "colaborador") +
                        " con " + cargaPorcentaje + "% de dedicación.", "proyectos.html");
    }

    @Transactional
    public void finalizarAsignacion(Long asignacionId, Long actorUsuarioId) {
        Asignacion a = asignacionRepository.findById(asignacionId)
                .orElseThrow(() -> new OperacionInvalidaException("La asignación ya no existe."));
        a.setEstado("finalizada");
        a.setFechaFin(LocalDate.now());
        asignacionRepository.save(a);
        auditoriaService.registrar(actorUsuarioId, "ASIGNACION_FINALIZADA", "asignacion", asignacionId, null, null,
                "Asignación finalizada.");
        notificacionService.crear(a.getPerfilId(), "asignacion", "Asignación finalizada",
                "Tu asignación al proyecto fue marcada como finalizada.", "proyectos.html");
    }

    // ───────────────────────── Helpers ─────────────────────────

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
