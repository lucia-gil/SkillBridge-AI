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

    private final EventoProyectoRepository eventoRepository;
    private final TipoEventoRepository tipoEventoRepository;
    private final TipoAudienciaRepository tipoAudienciaRepository;
    private final AsignacionRepository asignacionRepository;

    public EventoService(EventoProyectoRepository eventoRepository, TipoEventoRepository tipoEventoRepository,
                         TipoAudienciaRepository tipoAudienciaRepository, AsignacionRepository asignacionRepository) {
        this.eventoRepository = eventoRepository;
        this.tipoEventoRepository = tipoEventoRepository;
        this.tipoAudienciaRepository = tipoAudienciaRepository;
        this.asignacionRepository = asignacionRepository;
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

    /** Eventos visibles para el perfil segun su rol (PM ve 'todos'+'solo_pm'; colaborador 'todos'+'solo_colaboradores'). */
    public List<EventoFila> listar(Long perfilId, boolean esPm) {
        List<Long> ids = proyectoIdsDe(perfilId);
        if (ids.isEmpty()) return List.of();
        return eventoRepository.listarPorProyectos(ids).stream()
                .filter(e -> audienciaVisible(e.getAudiencia() != null ? e.getAudiencia().getCodigo() : "todos", esPm))
                .map(this::aFila)
                .collect(Collectors.toList());
    }

    private boolean audienciaVisible(String codigo, boolean esPm) {
        if ("todos".equals(codigo)) return true;
        if ("solo_pm".equals(codigo)) return esPm;
        if ("solo_colaboradores".equals(codigo)) return !esPm;
        return true;
    }

    private EventoFila aFila(EventoProyecto e) {
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
        return new EventoFila(e.getId(), dia, mes, fechaLabel, hora, tipoCodigo, tipoNombre, tipoColor,
                e.getTitulo(), e.getProyecto().getNombre(), ubic, e.getEstado(), audCodigo, audLabel);
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
        return eventoRepository.save(e);
    }
}
