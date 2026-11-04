package com.skillbridge.ai.controller;

import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.service.MatchingIaService;
import com.skillbridge.ai.service.ProyectoVistaService;
import com.skillbridge.ai.util.OperacionInvalidaException;
import com.skillbridge.ai.util.Roles;
import com.skillbridge.ai.util.SesionKeys;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Servicio REST de AI Talent Matching con IA (RF05), consumido con
 * jQuery/AJAX (js/matching-ia.js):
 *  POST /api/matching/proyectos/{id}/analizar  {dedicacion}  → ranking + explicaciones
 *  POST /api/matching/consulta                 {texto}       → petición en lenguaje natural
 * Lo usan el Resource Manager (cualquier proyecto) y el Project Manager
 * (solo los proyectos que lidera), según la vista activa.
 */
@RestController
@RequestMapping("/api/matching")
public class MatchingApiController {

    private final MatchingIaService matchingIaService;
    private final ProyectoVistaService proyectoVistaService;

    public MatchingApiController(MatchingIaService matchingIaService, ProyectoVistaService proyectoVistaService) {
        this.matchingIaService = matchingIaService;
        this.proyectoVistaService = proyectoVistaService;
    }

    public record AnalizarRequest(Integer dedicacion) {}
    public record ConsultaRequest(String texto) {}

    static class AccesoDenegado extends RuntimeException {}

    @PostMapping("/proyectos/{id}/analizar")
    public MatchingIaService.Analisis analizar(@PathVariable Long id, @RequestBody(required = false) AnalizarRequest body,
                                               HttpSession session) {
        UsuarioSesion s = sesion(session);
        if (esRm(s)) {
            // el RM ve todos los proyectos
        } else if (esPm(s) && proyectoVistaService.esPmDe(s.getPerfilId(), id)) {
            // el PM solo los que lidera
        } else {
            throw new AccesoDenegado();
        }
        int dedicacion = body != null && body.dedicacion() != null ? body.dedicacion() : MatchingIaService.DEDICACION_POR_DEFECTO;
        return matchingIaService.analizar(id, s.getPerfilId(), dedicacion);
    }

    @PostMapping("/consulta")
    public MatchingIaService.Analisis consulta(@RequestBody ConsultaRequest body, HttpSession session) {
        UsuarioSesion s = sesion(session);
        if (esRm(s)) return matchingIaService.consultar(body.texto(), s.getPerfilId(), null);
        if (esPm(s)) return matchingIaService.consultar(body.texto(), s.getPerfilId(), s.getPerfilId());
        throw new AccesoDenegado();
    }

    @ExceptionHandler(AccesoDenegado.class)
    public ResponseEntity<Map<String, String>> denegado() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "No tienes acceso a este proyecto."));
    }

    @ExceptionHandler(OperacionInvalidaException.class)
    public ResponseEntity<Map<String, String>> invalida(OperacionInvalidaException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }

    private static UsuarioSesion sesion(HttpSession session) {
        UsuarioSesion s = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        if (s == null) throw new AccesoDenegado();
        return s;
    }

    private static boolean esRm(UsuarioSesion s) {
        return Roles.RESOURCE_MANAGER.equals(s.getRolOrganizacional()) && Roles.RESOURCE_MANAGER.equals(s.getRolEfectivo());
    }

    private static boolean esPm(UsuarioSesion s) {
        return Roles.PROJECT_MANAGER.equals(s.getRolEfectivo());
    }
}
