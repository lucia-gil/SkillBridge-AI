package com.skillbridge.ai.controller;

import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.service.BuscadorService;
import com.skillbridge.ai.util.SesionKeys;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Endpoint JSON del buscador predictivo del topbar. Vive bajo /cuenta/**
 * a proposito: ese prefijo ya esta protegido por SesionInterceptor para
 * los 4 roles (exige sesion activa), asi que no hace falta tocar WebConfig.
 */
@RestController
public class BuscadorController {

    private final BuscadorService buscadorService;
    private final com.skillbridge.ai.service.BuscadorIaService buscadorIaService;
    private final com.skillbridge.ai.service.BuscadorColaboradorService buscadorColaborador;

    public BuscadorController(BuscadorService buscadorService, com.skillbridge.ai.service.BuscadorIaService buscadorIaService,
                              com.skillbridge.ai.service.BuscadorColaboradorService buscadorColaborador) {
        this.buscadorService = buscadorService;
        this.buscadorIaService = buscadorIaService;
        this.buscadorColaborador = buscadorColaborador;
    }

    public record PreguntaRequest(String q) {}

    /**
     * Buscador inteligente: la pregunta en lenguaje natural se convierte en
     * filtros (IA o palabras clave) y Java busca con el alcance del rol.
     * POST con JSON (jQuery/AJAX + token CSRF, ver js/global-search.js).
     */
    @org.springframework.web.bind.annotation.PostMapping("/cuenta/buscar-ia")
    public org.springframework.http.ResponseEntity<?> preguntar(@org.springframework.web.bind.annotation.RequestBody PreguntaRequest body, HttpSession session) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        if (sesion == null) return org.springframework.http.ResponseEntity.status(401).body(Map.of("error", "Tu sesión expiró."));
        try {
            return org.springframework.http.ResponseEntity.ok(buscadorIaService.preguntar(body.q(), sesion));
        } catch (com.skillbridge.ai.util.OperacionInvalidaException ex) {
            return org.springframework.http.ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping("/cuenta/buscar")
    public List<Map<String, Object>> buscar(@RequestParam(name = "q", defaultValue = "") String q, HttpSession session) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        List<Map<String, Object>> base = buscadorService.buscar(q, sesion);
        if (!com.skillbridge.ai.service.BuscadorColaboradorService.aplica(sesion)) return base;
        // Vista Colaborador: primero las páginas de su menú, luego proyectos y
        // habilidades, y después sus entregables, foros, eventos y cursos.
        List<Map<String, Object>> todo = new java.util.ArrayList<>(buscadorColaborador.paginas(q, sesion));
        todo.addAll(base);
        todo.addAll(buscadorColaborador.contenido(q, sesion));
        return todo;
    }
}
