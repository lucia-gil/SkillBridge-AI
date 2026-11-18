package com.skillbridge.ai.controller;

import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.service.ChatService;
import com.skillbridge.ai.util.OperacionInvalidaException;
import com.skillbridge.ai.util.SesionKeys;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Servicio web REST del chat de proyectos (RF07). Lo consume js/chat.js con
 * jQuery/AJAX ($.ajax vía SBAI.api):
 *
 *   GET  /api/chat/salas                          salas del usuario (con no leídos)
 *   GET  /api/chat/salas/{id}/miembros            integrantes de la sala
 *   GET  /api/chat/salas/{id}/mensajes            últimos 40
 *   GET  /api/chat/salas/{id}/mensajes?despues=N  nuevos (sondeo cada 3 s)
 *   GET  /api/chat/salas/{id}/mensajes?antes=N    anteriores (paginación hacia atrás)
 *   POST /api/chat/salas/{id}/mensajes            {contenido}  → mensaje creado
 *   POST /api/chat/salas/{id}/leido               {ultimoId}
 *   GET  /api/chat/no-leidos                      {total} (contador del sidebar)
 *   POST /api/chat/salas/{id}/resumen-ia          {resumen}
 *
 * Seguridad: /api/** exige sesión (SecurityConfig) y ChatService verifica en
 * cada llamada que la persona sea integrante activa del proyecto (si no, 403).
 * Los POST llevan el token CSRF (lo agrega sbai-api.js).
 */
@RestController
@RequestMapping(value = "/api/chat", produces = "application/json;charset=UTF-8")
public class ChatApiController {

    private final ChatService chatService;

    public ChatApiController(ChatService chatService) {
        this.chatService = chatService;
    }

    private static Long perfilId(HttpSession session) {
        UsuarioSesion s = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        return s == null ? null : s.getPerfilId();
    }

    @GetMapping("/salas")
    public List<ChatService.SalaVista> salas(HttpSession session) {
        return chatService.salas(perfilId(session));
    }

    @GetMapping("/salas/{id}/miembros")
    public List<ChatService.MiembroVista> miembros(@PathVariable Long id, HttpSession session) {
        return chatService.miembros(id, perfilId(session));
    }

    @GetMapping("/salas/{id}/mensajes")
    public List<ChatService.MensajeVista> mensajes(@PathVariable Long id,
                                                   @RequestParam(required = false) Long despues,
                                                   @RequestParam(required = false) Long antes,
                                                   HttpSession session) {
        return chatService.mensajes(id, perfilId(session), despues, antes);
    }

    @PostMapping("/salas/{id}/mensajes")
    public ResponseEntity<ChatService.MensajeVista> enviar(@PathVariable Long id, @RequestBody Map<String, String> body,
                                                           HttpSession session) {
        String contenido = body == null ? null : body.get("contenido");
        return ResponseEntity.status(HttpStatus.CREATED).body(chatService.enviar(id, perfilId(session), contenido));
    }

    @PostMapping("/salas/{id}/leido")
    public Map<String, Object> leido(@PathVariable Long id, @RequestBody Map<String, Long> body, HttpSession session) {
        chatService.marcarLeido(id, perfilId(session), body == null ? null : body.get("ultimoId"));
        return Map.of("ok", true);
    }

    @GetMapping("/no-leidos")
    public Map<String, Object> noLeidos(HttpSession session) {
        return Map.of("total", chatService.totalNoLeidos(perfilId(session)));
    }

    @PostMapping("/salas/{id}/resumen-ia")
    public Map<String, Object> resumen(@PathVariable Long id, HttpSession session) {
        return Map.of("resumen", chatService.resumir(id, perfilId(session)));
    }

    // ── Errores en JSON (los muestra el chat con SBAI.api.mensajeError) ──

    @ExceptionHandler(ChatService.AccesoDenegado.class)
    public ResponseEntity<Map<String, String>> sinAcceso(ChatService.AccesoDenegado ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(com.skillbridge.ai.util.RecursoNoEncontradoException.class)
    public ResponseEntity<Map<String, String>> noEncontrado(com.skillbridge.ai.util.RecursoNoEncontradoException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(OperacionInvalidaException.class)
    public ResponseEntity<Map<String, String>> invalida(OperacionInvalidaException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }
}
