package com.skillbridge.ai.service;

import com.skillbridge.ai.dto.AuditoriaFila;
import com.skillbridge.ai.dto.TopUsuarioAuditoria;
import com.skillbridge.ai.model.AuditoriaLog;
import com.skillbridge.ai.model.Usuario;
import com.skillbridge.ai.repository.AuditoriaLogRepository;
import com.skillbridge.ai.repository.UsuarioRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Registro de trazabilidad (auditoria_logs), corriente adaptada de los
 * AuditoriaDAO.log(...) de QuintaOla-SGA: cada accion sensible (login,
 * registro, cambios de rol, suspensiones, borrados...) deja una fila aqui.
 */
@Service
public class AuditoriaService {

    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final AuditoriaLogRepository auditoriaLogRepository;
    private final UsuarioRepository usuarioRepository;

    public AuditoriaService(AuditoriaLogRepository auditoriaLogRepository, UsuarioRepository usuarioRepository) {
        this.auditoriaLogRepository = auditoriaLogRepository;
        this.usuarioRepository = usuarioRepository;
    }

    public void registrar(Long usuarioId, String accion, String entidadAfectada, Long entidadId,
                           String valorAnterior, String valorNuevo, String detalle) {
        AuditoriaLog log = new AuditoriaLog();
        log.setUsuarioId(usuarioId);
        log.setAccion(accion);
        log.setEntidadAfectada(entidadAfectada);
        log.setEntidadId(entidadId);
        log.setValorAnterior(valorAnterior);
        log.setValorNuevo(valorNuevo);
        log.setDetalle(detalle);
        // No se hace fallar la operacion de negocio si la auditoria falla:
        // se prioriza no bloquear login/registro/CRUD por un problema de
        // trazabilidad, pero se deja constancia en el log de aplicacion.
        try {
            auditoriaLogRepository.save(log);
        } catch (Exception ex) {
            System.err.println("[AuditoriaService] No se pudo registrar auditoria (" + accion + "): " + ex.getMessage());
        }
    }

    public void registrar(Long usuarioId, String accion, String detalle) {
        registrar(usuarioId, accion, null, null, null, null, detalle);
    }

    /** Máximo de caracteres del motivo que se guarda en auditoría. */
    public static final int MAX_MOTIVO = 300;

    /** Normaliza el motivo que escribe la persona: sin espacios sobrantes y recortado; null si quedó vacío. */
    public static String limpiarMotivo(String motivo) {
        if (motivo == null) return null;
        String m = motivo.trim().replaceAll("\\s+", " ");
        if (m.isEmpty()) return null;
        return m.length() > MAX_MOTIVO ? m.substring(0, MAX_MOTIVO) : m;
    }

    /** Agrega el motivo al detalle de auditoría: "Proyecto X — Motivo: ...". */
    public static String conMotivo(String detalle, String motivo) {
        String m = limpiarMotivo(motivo);
        return m == null ? detalle : detalle + " — Motivo: " + m;
    }

    /** Envuelve un valor simple en JSON valido de una sola propiedad, para valor_anterior/valor_nuevo. */
    public static String json(String campo, Object valor) {
        String v = valor == null ? "null" : "\"" + String.valueOf(valor).replace("\"", "\\\"") + "\"";
        return "{\"" + campo + "\":" + v + "}";
    }

    // ───────────────────── Consulta (administrador/auditoria.html) ─────────────────────

    /** Últimos 200 eventos (la pantalla no trae paginación en el mockup original). */
    public List<AuditoriaFila> listarRecientes() {
        Map<Long, String> nombrePorUsuarioId = new HashMap<>();
        return auditoriaLogRepository.findTop200ByOrderByFechaDesc().stream()
                .map(log -> new AuditoriaFila(
                        log.getFecha() != null ? log.getFecha().format(FORMATO) : "",
                        etiquetaUsuario(log.getUsuarioId(), nombrePorUsuarioId),
                        log.getAccion(),
                        log.getDetalle()))
                .collect(Collectors.toList());
    }

    public Page<AuditoriaFila> buscar(LocalDateTime desde, LocalDateTime hasta,
                                      String usuario, String accion, Pageable pageable) {
        Map<Long, String> nombres = new HashMap<>();
        return auditoriaLogRepository.buscar(desde, hasta,
                usuario == null ? "" : usuario.trim(), accion == null ? "" : accion.trim(), pageable)
                .map(log -> new AuditoriaFila(
                        log.getFecha() == null ? "" : log.getFecha().format(FORMATO),
                        etiquetaUsuario(log.getUsuarioId(), nombres), log.getAccion(), log.getDetalle()));
    }

    private String etiquetaUsuario(Long usuarioId, Map<Long, String> cache) {
        if (usuarioId == null) return null; // AuditoriaFila lo convierte en "Sistema"
        return cache.computeIfAbsent(usuarioId, id ->
                usuarioRepository.findById(id).map(Usuario::getNombreCompleto).orElse("Usuario eliminado"));
    }

    public List<TopUsuarioAuditoria> topUsuarios(int limite) {
        return auditoriaLogRepository.topUsuariosPorEventos(PageRequest.of(0, limite)).stream()
                .map(fila -> {
                    Long usuarioId = (Long) fila[0];
                    long eventos = ((Number) fila[1]).longValue();
                    String nombre = usuarioRepository.findById(usuarioId).map(Usuario::getNombreCompleto).orElse("Usuario eliminado");
                    return new TopUsuarioAuditoria(nombre, eventos);
                })
                .collect(Collectors.toList());
    }
}
