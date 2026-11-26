package com.skillbridge.ai.controller;

import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.service.AsistenteContextoService;
import com.skillbridge.ai.service.AsistenteConversacionService;
import com.skillbridge.ai.service.ShellModelBuilder;
import com.skillbridge.ai.util.OperacionInvalidaException;
import com.skillbridge.ai.util.Roles;
import com.skillbridge.ai.util.SesionKeys;
import jakarta.servlet.http.HttpSession;
import com.skillbridge.ai.model.AsistenteAdjunto;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Asistente IA (chatbot) de los 4 roles: compartido/asistente-ia.html.
 * La conversación la maneja js/asistente-ia.js con jQuery/AJAX contra el
 * servicio REST /api/asistente/** (JSON). El contexto y el historial
 * dependen del usuario de la sesión y de su vista activa (rol efectivo).
 */
@Controller
public class AsistenteIaController {

    private final com.skillbridge.ai.service.agente.LimiteAgenteService limite;
    private final ShellModelBuilder shell;
    private final AsistenteConversacionService conversacionService;
    private final AsistenteContextoService contextoService;
    private final com.skillbridge.ai.repository.ProyectoRepository proyectos;

    public AsistenteIaController(ShellModelBuilder shell, AsistenteConversacionService conversacionService,
                                 AsistenteContextoService contextoService, com.skillbridge.ai.service.agente.LimiteAgenteService limite,
                                 com.skillbridge.ai.repository.ProyectoRepository proyectos) {
        this.proyectos = proyectos;
        this.limite=limite;
        this.shell = shell;
        this.conversacionService = conversacionService;
        this.contextoService = contextoService;
    }

    public record MensajeRequest(Long conversacionId, String mensaje) {}
    public record CambioRequest(String titulo, Boolean fijada) {}
    public record ArchivoRequest(Boolean archivada) {}
    public record ProyectoRequest(Long proyectoId) {}

    // ───────────────────────── Página ─────────────────────────

    @GetMapping({"/administrador/asistente-ia.html", "/resource-manager/asistente-ia.html",
            "/project-manager/asistente-ia.html", "/colaborador/asistente-ia.html"})
    public String pagina(@RequestParam(name = "c", required = false) Long conversacion, HttpSession session, Model model) {
        UsuarioSesion s = sesion(session);
        String rol = AsistenteConversacionService.rolDe(s);
        shell.aplicar(model, s, "asistente-ia.html", "Asistente IA", subtitulo(rol));
        AsistenteContextoService.Contexto ctx = contextoService.construir(s, rol, null);
        model.addAttribute("tarjetas", ctx.tarjetas());
        model.addAttribute("sugerencias", ctx.sugerencias());
        model.addAttribute("conversacionInicial", conversacion);
        model.addAttribute("maxCaracteres", AsistenteConversacionService.MAX_CARACTERES);
        model.addAttribute("maxAdjuntos", AsistenteConversacionService.MAX_ADJUNTOS);
        model.addAttribute("motivo", conversacionService.motivoIndisponible());
        model.addAttribute("alcance", alcance(rol));
        boolean asistenteAdmin = Roles.ADMINISTRADOR.equals(rol);
        model.addAttribute("asistenteAdmin", asistenteAdmin);
        if (asistenteAdmin) {
            model.addAttribute("proyectosAsistente", proyectos.findAll(org.springframework.data.domain.Sort.by("nombre")));
        }
        return "compartido/asistente-ia";
    }

    // ───────────────────────── REST (JSON) ─────────────────────────

    @GetMapping("/api/asistente/conversaciones")
    @ResponseBody
    public List<AsistenteConversacionService.ConversacionVista> conversaciones(HttpSession session) {
        return conversacionService.conversaciones(sesion(session));
    }

    @PostMapping("/api/asistente/conversaciones")
    @ResponseBody
    public ResponseEntity<AsistenteConversacionService.ConversacionVista> crear(HttpSession session) {
        return ResponseEntity.status(201).body(conversacionService.crearBorrador(sesion(session)));
    }

    @PutMapping("/api/asistente/conversaciones/{id}/archivo")
    @ResponseBody
    public ResponseEntity<Void> archivar(@PathVariable Long id, @RequestBody ArchivoRequest body, HttpSession session) {
        if (body.archivada() == null) throw new OperacionInvalidaException("Indica si quieres archivar la conversación.");
        conversacionService.archivar(id, body.archivada(), sesion(session));
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/api/asistente/conversaciones/{id}/proyecto")
    @ResponseBody
    public ResponseEntity<Void> vincularProyecto(@PathVariable Long id, @RequestBody ProyectoRequest body, HttpSession session) {
        conversacionService.vincularProyecto(id, body.proyectoId(), sesion(session));
        return ResponseEntity.noContent().build();
    }

    /** Conversación cuya respuesta todavía se está generando ({"conversacionId": null} si no hay). */
    @GetMapping("/api/asistente/en-curso")
    @ResponseBody
    public ResponseEntity<Map<String, Long>> enCurso(HttpSession session) {
        return ResponseEntity.ok(java.util.Collections.singletonMap("conversacionId",
                conversacionService.conversacionEnCurso(sesion(session))));
    }

    @GetMapping("/api/asistente/conversaciones/{id}")
    @ResponseBody
    public List<AsistenteConversacionService.MensajeVista> mensajes(@PathVariable Long id, HttpSession session) {
        return conversacionService.mensajes(id, sesion(session));
    }

    @DeleteMapping("/api/asistente/conversaciones/{id}")
    @ResponseBody
    public Map<String, Boolean> eliminar(@PathVariable Long id, HttpSession session) {
        conversacionService.eliminar(id, sesion(session));
        return Map.of("ok", true);
    }

    /** Renombrar ({titulo}) y/o fijar ({fijada}) una conversación. */
    @PutMapping("/api/asistente/conversaciones/{id}")
    @ResponseBody
    public AsistenteConversacionService.ConversacionVista cambiar(@PathVariable Long id, @RequestBody CambioRequest body, HttpSession session) {
        UsuarioSesion s = sesion(session);
        AsistenteConversacionService.ConversacionVista v = null;
        if (body.titulo() != null) v = conversacionService.renombrar(id, body.titulo(), s);
        if (body.fijada() != null) v = conversacionService.fijar(id, body.fijada(), s);
        if (v == null) throw new OperacionInvalidaException("No hay nada que cambiar.");
        return v;
    }

    /** Mensaje con archivos o fotos (multipart/form-data: conversacionId, mensaje, archivos). */
    @PostMapping(value = "/api/asistente/mensaje", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseBody
    public AsistenteConversacionService.Respuesta mensajeConArchivos(@RequestParam(required = false) Long conversacionId,
                                                                    @RequestParam(required = false) String mensaje,
                                                                    @RequestParam(name = "archivos", required = false) List<MultipartFile> archivos,
                                                                    HttpSession session) {
        return enviarLimitado(sesion(session), conversacionId, mensaje, archivos);
    }

    /** Ver (imágenes y PDF, en el navegador) o descargar un adjunto propio. */
    @GetMapping("/api/asistente/adjuntos/{id}")
    public ResponseEntity<byte[]> adjunto(@PathVariable Long id, HttpSession session) {
        AsistenteAdjunto a = conversacionService.adjunto(id, sesion(session));
        boolean enLinea = a.getTipo().startsWith("image/") || a.getTipo().equals("application/pdf");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(enLinea ? a.getTipo() : "application/octet-stream"))
                .header(HttpHeaders.CONTENT_DISPOSITION, (enLinea ? ContentDisposition.inline() : ContentDisposition.attachment())
                        .filename(a.getNombre(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "sandbox")
                .cacheControl(CacheControl.noCache())
                .body(a.getDatos());
    }

    @PostMapping(value = "/api/asistente/mensaje", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public AsistenteConversacionService.Respuesta mensaje(@RequestBody MensajeRequest body, HttpSession session) {
        return enviarLimitado(sesion(session), body.conversacionId(), body.mensaje(), List.of());
    }

    private AsistenteConversacionService.Respuesta enviarLimitado(UsuarioSesion usuario,Long id,String mensaje,List<MultipartFile> archivos) {
        UsuarioSesion actor=new UsuarioSesion();
        actor.setUsuarioId(usuario.getUsuarioId());actor.setPerfilId(usuario.getPerfilId());actor.setRolEfectivo(usuario.getRolEfectivo());
        actor.setNombreCompleto(usuario.getNombreCompleto());   // solo para dirigirse a la persona en las instrucciones de Bridgie
        String lease=limite.adquirir(actor.getUsuarioId());
        try { return conversacionService.enviar(actor,id,mensaje,archivos); }
        finally { limite.liberar(usuario.getUsuarioId(),lease); }
    }

    @ExceptionHandler(com.skillbridge.ai.util.RecursoNoEncontradoException.class)
    @ResponseBody
    public ResponseEntity<Map<String, String>> noEncontrado(com.skillbridge.ai.util.RecursoNoEncontradoException ex) {
        return ResponseEntity.status(org.springframework.http.HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(OperacionInvalidaException.class)
    @ResponseBody
    public ResponseEntity<Map<String, String>> invalida(OperacionInvalidaException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }

    // ───────────────────────── helpers ─────────────────────────

    private static UsuarioSesion sesion(HttpSession session) {
        UsuarioSesion s = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        if (s == null) throw new OperacionInvalidaException("Tu sesión expiró. Vuelve a iniciar sesión.");
        return s;
    }

    private static String subtitulo(String rol) {
        return switch (rol) {
            case Roles.RESOURCE_MANAGER -> "Contexto: capacidad del equipo, banca, solicitudes y vacantes";
            case Roles.PROJECT_MANAGER -> "Contexto: tus proyectos, tu equipo y sus entregables";
            case Roles.ADMINISTRADOR -> "Contexto: usuarios, proyectos y catálogo de la plataforma";
            default -> "Contexto: tus proyectos, tus habilidades y tus entregables";
        };
    }

    private static String alcance(String rol) {
        return switch (rol) {
            case Roles.RESOURCE_MANAGER -> "Como Resource Manager, el asistente consulta la capacidad de todo el equipo y puede pedir candidatos a AI Talent Matching.";
            case Roles.PROJECT_MANAGER -> "Como Project Manager, el asistente consulta solo los proyectos que lideras y su equipo.";
            case Roles.ADMINISTRADOR -> "Como Administrador, el asistente consulta cifras globales de la plataforma.";
            default -> "Como Colaborador, el asistente solo consulta tus propios datos.";
        };
    }
}
