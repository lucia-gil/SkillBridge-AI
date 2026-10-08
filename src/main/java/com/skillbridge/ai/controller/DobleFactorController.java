package com.skillbridge.ai.controller;

import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.service.DobleFactorService;
import com.skillbridge.ai.util.SesionKeys;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Gestion del doble factor desde "Mi cuenta". Bajo /cuenta/** => exige sesion (SesionInterceptor). */
@RestController
public class DobleFactorController {

    private final DobleFactorService servicio;

    public DobleFactorController(DobleFactorService servicio) {
        this.servicio = servicio;
    }

    private static UsuarioSesion sesion(HttpSession s) {
        return (UsuarioSesion) s.getAttribute(SesionKeys.USUARIO);
    }

    @GetMapping("/cuenta/2fa/estado")
    public Map<String, Object> estado(HttpSession session) {
        return Map.of("activo", servicio.estaActivo(sesion(session).getUsuarioId()));
    }

    @PostMapping("/cuenta/2fa/iniciar")
    public ResponseEntity<Map<String, String>> iniciar(HttpSession session) {
        UsuarioSesion u = sesion(session);
        try {
            return ResponseEntity.ok(servicio.iniciarConfiguracion(u.getUsuarioId(), u.getCorreo()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("error", "El doble factor no esta disponible en este momento."));
        }
    }

    @PostMapping("/cuenta/2fa/activar")
    public ResponseEntity<Map<String, Object>> activar(@RequestParam String codigo, HttpSession session) {
        boolean ok = servicio.activar(sesion(session).getUsuarioId(), codigo);
        return ok ? ResponseEntity.ok(Map.of("activo", true))
                : ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Codigo incorrecto. Revisa la hora de tu telefono e intenta de nuevo."));
    }

    @PostMapping("/cuenta/2fa/desactivar")
    public ResponseEntity<Map<String, Object>> desactivar(@RequestParam String codigo, HttpSession session) {
        boolean ok = servicio.desactivar(sesion(session).getUsuarioId(), codigo);
        return ok ? ResponseEntity.ok(Map.of("activo", false))
                : ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Codigo incorrecto."));
    }
}
