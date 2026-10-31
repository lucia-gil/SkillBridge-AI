package com.skillbridge.ai.controller;

import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.service.AsistenteIaService;
import com.skillbridge.ai.util.SesionKeys;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

/**
 * Backend del chat de asistente-ia.html (los 4 roles). Un unico endpoint
 * porque la pregunta/respuesta no depende de la URL del rol, sino de la
 * sesion del usuario que la hace (ver AsistenteIaService.promptSistema).
 *
 * Ruta bajo /api/**: no tiene un prefijo de rol especifico en
 * SecurityConfig, asi que cae en el "anyRequest().authenticated()" -
 * cualquier usuario con sesion iniciada (de cualquier rol) puede usarlo.
 */
@Controller
public class AsistenteIaController {

    private final AsistenteIaService asistenteIaService;

    public AsistenteIaController(AsistenteIaService asistenteIaService) {
        this.asistenteIaService = asistenteIaService;
    }

    @PostMapping("/api/asistente/mensaje")
    @ResponseBody
    public ResponseEntity<Map<String, String>> mensaje(@RequestBody Map<String, String> body, HttpSession session) {
        String mensaje = body == null ? null : body.get("mensaje");
        if (mensaje == null || mensaje.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Escribe un mensaje antes de enviar."));
        }
        if (mensaje.length() > 2000) {
            return ResponseEntity.badRequest().body(Map.of("error", "El mensaje es demasiado largo (máximo 2000 caracteres)."));
        }

        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        String respuesta = asistenteIaService.responder(sesion, mensaje.trim());
        return ResponseEntity.ok(Map.of("respuesta", respuesta));
    }
}
