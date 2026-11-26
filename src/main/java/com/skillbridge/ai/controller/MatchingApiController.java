package com.skillbridge.ai.controller;

import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.service.MatchingIaService;
import com.skillbridge.ai.service.ProyectoService;
import com.skillbridge.ai.service.TalentMatchingService;
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
 *  POST /api/matching/proyectos/{id}/proponer  {perfilId, dedicacion} → propuesta al RM (PM)
 *  POST /api/matching/proyectos/{id}/asignar   {perfilId, dedicacion} → asignación directa (RM)
 * Proponer/asignar responden JSON para no recargar la página: así el análisis
 * de la IA queda en pantalla y se puede elegir a más de una persona.
 * Lo usan el Resource Manager (cualquier proyecto) y el Project Manager
 * (solo los proyectos que lidera), según la vista activa.
 */
@RestController
@RequestMapping(value = "/api/matching", produces = "application/json;charset=UTF-8")
public class MatchingApiController {

    private final MatchingIaService matchingIaService;
    private final ProyectoVistaService proyectoVistaService;
    private final TalentMatchingService talentMatchingService;
    private final ProyectoService proyectoService;

    public MatchingApiController(MatchingIaService matchingIaService, ProyectoVistaService proyectoVistaService,
                                 TalentMatchingService talentMatchingService, ProyectoService proyectoService) {
        this.matchingIaService = matchingIaService;
        this.proyectoVistaService = proyectoVistaService;
        this.talentMatchingService = talentMatchingService;
        this.proyectoService = proyectoService;
    }

    public record AnalizarRequest(Integer dedicacion) {}
    public record ConsultaRequest(String texto) {}
    public record SeleccionRequest(Long perfilId, Integer dedicacion) {}

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

    /** PM: propone a la persona al Resource Manager (solo en proyectos que lidera). */
    @PostMapping("/proyectos/{id}/proponer")
    public Map<String, Object> proponer(@PathVariable Long id, @RequestBody SeleccionRequest body, HttpSession session) {
        UsuarioSesion s = sesion(session);
        if (!esPm(s) || !proyectoVistaService.esPmDe(s.getPerfilId(), id)) throw new AccesoDenegado();
        validar(body);
        talentMatchingService.proponerManual(s.getPerfilId(), s.getUsuarioId(), id, body.perfilId(), body.dedicacion());
        return Map.of("ok", true, "mensaje", "Propuesta enviada al Resource Manager.");
    }

    /** RM: asigna directamente a la persona al proyecto (mismas reglas de carga que la pantalla de Asignaciones). */
    @PostMapping("/proyectos/{id}/asignar")
    public Map<String, Object> asignar(@PathVariable Long id, @RequestBody SeleccionRequest body, HttpSession session) {
        UsuarioSesion s = sesion(session);
        if (!esRm(s)) throw new AccesoDenegado();
        validar(body);
        proyectoService.asignarColaborador(id, body.perfilId(), body.dedicacion(), null, s.getUsuarioId());
        return Map.of("ok", true, "mensaje", "Asignado al proyecto.");
    }

    private static void validar(SeleccionRequest body) {
        if (body == null || body.perfilId() == null || body.perfilId() < 1 || body.dedicacion() == null
                || body.dedicacion() < 1 || body.dedicacion() > 100) {
            throw new OperacionInvalidaException("Elige una persona y una dedicación entre 1% y 100%.");
        }
    }

    @ExceptionHandler(AccesoDenegado.class)
    public ResponseEntity<Map<String, String>> denegado() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "No tienes acceso a este proyecto."));
    }

    @ExceptionHandler(com.skillbridge.ai.util.RecursoNoEncontradoException.class)
    public ResponseEntity<Map<String, String>> noEncontrado(com.skillbridge.ai.util.RecursoNoEncontradoException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
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
