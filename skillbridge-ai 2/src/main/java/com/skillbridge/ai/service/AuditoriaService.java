package com.skillbridge.ai.service;

import com.skillbridge.ai.model.AuditoriaLog;
import com.skillbridge.ai.repository.AuditoriaLogRepository;
import org.springframework.stereotype.Service;

/**
 * Registro de trazabilidad (auditoria_logs), corriente adaptada de los
 * AuditoriaDAO.log(...) de QuintaOla-SGA: cada accion sensible (login,
 * registro, cambios de rol, suspensiones, borrados...) deja una fila aqui.
 */
@Service
public class AuditoriaService {

    private final AuditoriaLogRepository auditoriaLogRepository;

    public AuditoriaService(AuditoriaLogRepository auditoriaLogRepository) {
        this.auditoriaLogRepository = auditoriaLogRepository;
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

    /** Envuelve un valor simple en JSON valido de una sola propiedad, para valor_anterior/valor_nuevo. */
    public static String json(String campo, Object valor) {
        String v = valor == null ? "null" : "\"" + String.valueOf(valor).replace("\"", "\\\"") + "\"";
        return "{\"" + campo + "\":" + v + "}";
    }
}
