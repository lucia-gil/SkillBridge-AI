package com.skillbridge.ai.service;

import com.skillbridge.ai.dto.EventoFila;
import com.skillbridge.ai.dto.ProyectoOpcion;
import com.skillbridge.ai.model.Asignacion;
import com.skillbridge.ai.model.EventoProyecto;
import com.skillbridge.ai.model.TipoAudiencia;
import com.skillbridge.ai.model.TipoEvento;
import com.skillbridge.ai.repository.AsignacionRepository;
import com.skillbridge.ai.repository.EventoProyectoRepository;
import com.skillbridge.ai.repository.TipoAudienciaRepository;
import com.skillbridge.ai.repository.TipoEventoRepository;
import com.skillbridge.ai.util.OperacionInvalidaException;
import com.skillbridge.ai.util.Roles;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Eventos del calendario (RF calendario) sobre "eventos_proyecto". Muestra los
 * eventos de los proyectos donde el perfil tiene una asignacion activa,
 * respetando la audiencia (todos / solo_pm / solo_colaboradores). "Nuevo evento"
 * los guarda en la tabla real. Los tipos (reunion/entregable/hito) y audiencias
 * vienen sembrados en skillbridge_db_v4.sql.
 */
@Service
public class EventoService {

    private static final String[] MESES = {"ENE", "FEB", "MAR", "ABR", "MAY", "JUN", "JUL", "AGO", "SEP", "OCT", "NOV", "DIC"};
    private static final String[] DIAS = {"LUN", "MAR", "MIE", "JUE", "VIE", "SAB", "DOM"};
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    // Sin segundos, a proposito: coincide con el valor que entrega (y espera
    // de vuelta) un <input type="datetime-local">, y con el formato que ya
    // arma "Nuevo evento" (dia + "T" + hora + ":" + minuto) en el front.
    private static final DateTimeFormatter FECHA_ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    private final EventoProyectoRepository eventoRepository;
    private final TipoEventoRepository tipoEventoRepository;
    private final TipoAudienciaRepository tipoAudienciaRepository;
    private final AsignacionRepository asignacionRepository;
    private final NotificacionService notificacionService;

    public EventoService(EventoProyectoRepository eventoRepository, TipoEventoRepository tipoEventoRepository,
                         TipoAudienciaRepository tipoAudienciaRepository, AsignacionRepository asignacionRepository,
                         NotificacionService notificacionService) {
        this.eventoRepository = eventoRepository;
        this.tipoEventoRepository = tipoEventoRepository;
        this.tipoAudienciaRepository = tipoAudienciaRepository;
        this.asignacionRepository = asignacionRepository;
        this.notificacionService = notificacionService;
    }

    private List<Long> proyectoIdsDe(Long perfilId) {
        return asignacionRepository.listarPorPerfilYEstado(perfilId, "activa").stream()
                .map(Asignacion::getProyectoId)
                .distinct()
                .collect(Collectors.toList());
    }

    public List<ProyectoOpcion> misProyectos(Long perfilId) {
        Map<Long, String> nombres = new LinkedHashMap<>();
        for (Asignacion a : asignacionRepository.listarPorPerfilYEstado(perfilId, "activa")) {
            nombres.putIfAbsent(a.getProyectoId(), a.getProyecto().getNombre());
        }
        return nombres.entrySet().stream()
                .map(e -> new ProyectoOpcion(e.getKey(), e.getValue(), 0))
                .collect(Collectors.toList());
    }

    /**
     * Eventos visibles para el perfil segun su rol (PM ve 'todos'+'solo_pm'; colaborador 'todos'+'solo_colaboradores').
     * rolOrganizacional (puede ser null) solo se usa para calcular el permiso
     * de editar/eliminar de CADA fila (ver puedeGestionar), no afecta que
     * eventos se listan.
     */
    public List<EventoFila> listar(Long perfilId, boolean esPm, String rolOrganizacional) {
        List<Long> ids = proyectoIdsDe(perfilId);
        if (ids.isEmpty()) return List.of();
        return eventoRepository.listarPorProyectos(ids).stream()
                .filter(e -> audienciaVisible(e.getAudiencia() != null ? e.getAudiencia().getCodigo() : "todos", esPm))
                .map(e -> aFila(e, perfilId, rolOrganizacional))
                .collect(Collectors.toList());
    }

    /**
     * Regla de permisos para editar/eliminar un evento (mismo criterio de 3
     * niveles que ya se usa en Asignaciones):
     *  1. Quien creo el evento (creado_por_id) - siempre puede, es dueño de
     *     lo que creo, igual que ya aplica crear().
     *  2. El Project Manager ACTIVO del proyecto del evento - puede sobre
     *     CUALQUIER evento de su proyecto, no solo los que el mismo creo,
     *     porque supervisa todo lo que pasa ahi (mismo criterio que ya usa
     *     para gestionar asignaciones de todo su equipo).
     *  3. El Administrador - puede sobre cualquier evento de cualquier
     *     proyecto (mismo criterio de "override" que ya se usa en Resource
     *     Manager / AdminProyectosController).
     * Un colaborador que no cae en ninguno de los 3 casos anteriores NO
     * puede tocar el evento de otro colaborador.
     */
    public boolean puedeGestionar(Long perfilId, String rolOrganizacional, EventoProyecto evento) {
        if (evento.getCreadoPorId().equals(perfilId)) return true;
        if (Roles.ADMINISTRADOR.equals(rolOrganizacional)) return true;
        return asignacionRepository.existsByProyectoIdAndPerfilIdAndRolEnProyectoAndEstado(
                evento.getProyectoId(), perfilId, "project_manager", "activa");
    }

    private boolean audienciaVisible(String codigo, boolean esPm) {
        if ("todos".equals(codigo)) return true;
        if ("solo_pm".equals(codigo)) return esPm;
        if ("solo_colaboradores".equals(codigo)) return !esPm;
        return true;
    }

    private EventoFila aFila(EventoProyecto e, Long perfilId, String rolOrganizacional) {
        LocalDateTime f = e.getFechaInicio();
        int dia = f.getDayOfMonth();
        String mes = MESES[f.getMonthValue() - 1];
        String diaSem = DIAS[(f.getDayOfWeek().getValue() + 6) % 7];
        String fechaLabel = diaSem + " " + dia + " " + mes;
        String hora = f.format(HORA);
        String ubic = e.getEnlaceVirtual() != null && !e.getEnlaceVirtual().isBlank() ? "Virtual"
                : (e.getUbicacion() != null && !e.getUbicacion().isBlank() ? e.getUbicacion() : "Sin ubicacion");
        String tipoCodigo = e.getTipo() != null ? e.getTipo().getCodigo() : "reunion";
        String tipoNombre = e.getTipo() != null ? e.getTipo().getNombre() : "Evento";
        String tipoColor = e.getTipo() != null && e.getTipo().getColor() != null ? e.getTipo().getColor() : "#64748b";
        String audCodigo = e.getAudiencia() != null ? e.getAudiencia().getCodigo() : "todos";
        String audLabel = e.getAudiencia() != null ? e.getAudiencia().getNombre() : "Todos";
        boolean puedeGestionar = puedeGestionar(perfilId, rolOrganizacional, e);
        return new EventoFila(e.getId(), dia, mes, fechaLabel, hora, tipoCodigo, tipoNombre, tipoColor,
                e.getTitulo(), e.getProyecto().getNombre(), ubic, e.getEstado(), audCodigo, audLabel,
                puedeGestionar, e.getProyectoId(), e.getDescripcion(), e.getUbicacion(), e.getEnlaceVirtual(),
                f.format(FECHA_ISO));
    }

    @Transactional
    public EventoProyecto crear(Long perfilId, Long proyectoId, String tipoCodigo, String titulo, String descripcion,
                                String fechaInicioIso, String ubicacion, String enlaceVirtual, String audienciaCodigo) {
        if (titulo == null || titulo.isBlank()) {
            throw new OperacionInvalidaException("Escribe un titulo para el evento.");
        }
        if (fechaInicioIso == null || fechaInicioIso.isBlank()) {
            throw new OperacionInvalidaException("Selecciona la fecha y hora del evento.");
        }
        if (!proyectoIdsDe(perfilId).contains(proyectoId)) {
            throw new OperacionInvalidaException("Solo puedes crear eventos en tus proyectos.");
        }
        TipoEvento tipo = tipoEventoRepository.findByCodigo(tipoCodigo)
                .orElseThrow(() -> new OperacionInvalidaException("Tipo de evento no valido."));
        TipoAudiencia audiencia = tipoAudienciaRepository.findByCodigo(
                        audienciaCodigo == null || audienciaCodigo.isBlank() ? "todos" : audienciaCodigo)
                .orElseGet(() -> tipoAudienciaRepository.findByCodigo("todos").orElse(null));

        LocalDateTime fecha;
        try {
            fecha = LocalDateTime.parse(fechaInicioIso);
        } catch (Exception ex) {
            throw new OperacionInvalidaException("Fecha u hora no valida.");
        }

        EventoProyecto e = new EventoProyecto();
        e.setProyectoId(proyectoId);
        e.setCreadoPorId(perfilId);
        e.setTipoId(tipo.getId());
        e.setTitulo(titulo.trim());
        e.setDescripcion(descripcion);
        e.setFechaInicio(fecha);
        if (ubicacion != null && !ubicacion.isBlank()) e.setUbicacion(ubicacion.trim());
        if (enlaceVirtual != null && !enlaceVirtual.isBlank()) e.setEnlaceVirtual(enlaceVirtual.trim());
        if (audiencia != null) e.setAudienciaId(audiencia.getId());
        e.setEstado("pendiente");
        EventoProyecto guardado = eventoRepository.save(e);
        notificarAlEquipo(guardado, perfilId, audiencia);
        return guardado;
    }

    /**
     * Edita un evento existente. Valida el mismo permiso de 3 niveles que
     * puedeGestionar() antes de tocar nada (defensa en profundidad: la vista
     * ya oculta el boton de Editar si el permiso es falso, pero el endpoint
     * no puede confiar solo en eso).
     */
    @Transactional
    public EventoProyecto editar(Long eventoId, Long perfilId, String rolOrganizacional, String tipoCodigo,
                                 String titulo, String descripcion, String fechaInicioIso, String ubicacion,
                                 String enlaceVirtual, String audienciaCodigo) {
        EventoProyecto e = eventoRepository.findById(eventoId)
                .orElseThrow(() -> new OperacionInvalidaException("El evento ya no existe."));
        if (!puedeGestionar(perfilId, rolOrganizacional, e)) {
            throw new OperacionInvalidaException("No tienes permiso para editar este evento.");
        }
        if (titulo == null || titulo.isBlank()) {
            throw new OperacionInvalidaException("Escribe un titulo para el evento.");
        }
        if (fechaInicioIso == null || fechaInicioIso.isBlank()) {
            throw new OperacionInvalidaException("Selecciona la fecha y hora del evento.");
        }
        TipoEvento tipo = tipoEventoRepository.findByCodigo(tipoCodigo)
                .orElseThrow(() -> new OperacionInvalidaException("Tipo de evento no valido."));
        TipoAudiencia audiencia = tipoAudienciaRepository.findByCodigo(
                        audienciaCodigo == null || audienciaCodigo.isBlank() ? "todos" : audienciaCodigo)
                .orElseGet(() -> tipoAudienciaRepository.findByCodigo("todos").orElse(null));

        LocalDateTime fecha;
        try {
            fecha = LocalDateTime.parse(fechaInicioIso);
        } catch (Exception ex) {
            throw new OperacionInvalidaException("Fecha u hora no valida.");
        }

        e.setTipoId(tipo.getId());
        e.setTitulo(titulo.trim());
        e.setDescripcion(descripcion);
        e.setFechaInicio(fecha);
        e.setUbicacion(ubicacion != null && !ubicacion.isBlank() ? ubicacion.trim() : null);
        e.setEnlaceVirtual(enlaceVirtual != null && !enlaceVirtual.isBlank() ? enlaceVirtual.trim() : null);
        if (audiencia != null) e.setAudienciaId(audiencia.getId());
        return eventoRepository.save(e);
    }

    /**
     * Elimina (fisicamente) un evento, con el mismo permiso de 3 niveles.
     * A diferencia de Proyectos/Asignaciones (que usan borrado logico via
     * estado, porque tienen historial que preservar), un evento de
     * calendario no participa de ningun reporte historico ni calculo de
     * carga - por eso aqui si se hace DELETE real. comentarios_evento
     * referencia eventos_proyecto con ON DELETE CASCADE (ver skillbridge_db_v4.sql),
     * asi que sus comentarios se eliminan junto con el evento, sin dejar
     * huerfanos.
     */
    @Transactional
    public void eliminar(Long eventoId, Long perfilId, String rolOrganizacional) {
        EventoProyecto e = eventoRepository.findById(eventoId)
                .orElseThrow(() -> new OperacionInvalidaException("El evento ya no existe."));
        if (!puedeGestionar(perfilId, rolOrganizacional, e)) {
            throw new OperacionInvalidaException("No tienes permiso para eliminar este evento.");
        }
        eventoRepository.delete(e);
    }

    /**
     * Avisa al equipo del proyecto cuando se crea un evento nuevo, respetando
     * la audiencia elegida (mismo criterio que usa listar() para decidir
     * quien VE el evento). Quien lo crea no se notifica a si mismo.
     */
    private void notificarAlEquipo(EventoProyecto evento, Long creadorPerfilId, TipoAudiencia audiencia) {
        String audienciaCodigo = audiencia != null ? audiencia.getCodigo() : "todos";
        List<Asignacion> equipo = asignacionRepository.listarEquipoDeProyecto(evento.getProyectoId(), "activa");
        if (equipo.isEmpty()) return;
        // "evento" recien se creo con new EventoProyecto() - su relacion
        // .proyecto nunca se cargo (solo se seteo proyectoId), por eso el
        // nombre del proyecto se toma de un miembro del equipo (ese si viene
        // cargado desde la consulta JPQL, dentro de la misma transaccion).
        String nombreProyecto = equipo.get(0).getProyecto().getNombre();
        String enlace = "/colaborador/calendario.html";
        for (Asignacion a : equipo) {
            if (a.getPerfilId().equals(creadorPerfilId)) continue; // no te notificas a ti mismo
            boolean esPmDeEsteMiembro = "project_manager".equals(a.getRolEnProyecto());
            if (!audienciaVisible(audienciaCodigo, esPmDeEsteMiembro)) continue;
            notificacionService.crear(a.getPerfilId(), "info",
                    "Nuevo evento: " + evento.getTitulo(),
                    "Se agregó un evento a " + nombreProyecto + ".",
                    enlace);
        }
    }
}