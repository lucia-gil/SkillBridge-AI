package com.skillbridge.ai.controller;

import com.skillbridge.ai.dto.AgenteConfirmacionVista;
import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.service.agente.ConfirmacionAgenteService;
import com.skillbridge.ai.service.agente.LimiteAgenteService;
import com.skillbridge.ai.util.OperacionInvalidaException;
import com.skillbridge.ai.util.SesionKeys;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Servicio REST de confirmaciones del agente IA (ver
 * Documentacion_REST/04_confirmaciones_agente.md):
 *   GET  /api/asistente/confirmaciones                  propuesta pendiente
 *   POST /api/asistente/confirmaciones/{id}/confirmar   ejecuta el cambio
 *   POST /api/asistente/confirmaciones/{id}/cancelar    lo descarta
 */
@RestController
@RequestMapping(value = "/api/asistente/confirmaciones", produces = "application/json;charset=UTF-8")
public class ConfirmacionAgenteController {

    private final ConfirmacionAgenteService servicio;
    private final LimiteAgenteService limites;
    private final boolean habilitado;

    public ConfirmacionAgenteController(ConfirmacionAgenteService servicio, LimiteAgenteService limites,
                                        @Value("${agente.enabled:true}") boolean habilitado) {
        this.servicio = servicio;
        this.limites = limites;
        this.habilitado = habilitado;
    }

    @GetMapping
    public ResponseEntity<List<AgenteConfirmacionVista>> pendientes(HttpSession session) {
        return ResponseEntity.ok(servicio.pendientes(actor(session)));
    }

    @PostMapping("/{id}/confirmar")
    public ResponseEntity<Map<String, Boolean>> confirmar(@PathVariable String id, HttpSession session) {
        UsuarioSesion s = actor(session);
        String lease = limites.adquirir(s.getUsuarioId());
        try {
            servicio.resolver(s, id, true);
            return ResponseEntity.ok(Map.of("ok", true));
        } finally {
            limites.liberar(s.getUsuarioId(), lease);
        }
    }

    @PostMapping("/{id}/cancelar")
    public ResponseEntity<Map<String, Boolean>> cancelar(@PathVariable String id, HttpSession session) {
        servicio.resolver(actor(session), id, false);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> denegado() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "Operación no autorizada o confirmación vencida/utilizada."));
    }

    @ExceptionHandler({IllegalArgumentException.class, OperacionInvalidaException.class})
    public ResponseEntity<Map<String, String>> invalido() {
        return ResponseEntity.badRequest().body(Map.of("error",
                "No se pudo aplicar: revisa los datos y las reglas de negocio en la pantalla correspondiente. "
                        + "No se ha confirmado el cambio."));
    }

    private UsuarioSesion actor(HttpSession session) {
        if (!habilitado) throw new AccessDeniedException("Agente deshabilitado.");
        UsuarioSesion s = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        if (s == null) throw new AccessDeniedException("Sesión requerida.");
        return s;
    }
}
