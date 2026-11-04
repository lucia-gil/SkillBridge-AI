package com.skillbridge.ai.controller;

import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.service.ChatService;
import com.skillbridge.ai.service.ShellModelBuilder;
import com.skillbridge.ai.util.SesionKeys;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Página del chat de proyectos (RF07) para Colaborador y Project Manager:
 * compartido/chat.html. La conversación la maneja js/chat.js contra
 * ChatApiController (REST + jQuery/AJAX). ?sala=ID o ?proyecto=ID abre una sala.
 */
@Controller
public class ChatController {

    private final ShellModelBuilder shell;
    private final ChatService chatService;

    public ChatController(ShellModelBuilder shell, ChatService chatService) {
        this.shell = shell;
        this.chatService = chatService;
    }

    @GetMapping({"/colaborador/chat.html", "/project-manager/chat.html"})
    public String chat(@RequestParam(required = false) Long sala, @RequestParam(required = false) Long proyecto,
                       HttpSession session, Model model) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        shell.aplicar(model, sesion, "chat.html", "Chat", "Conversaciones con el equipo de tus proyectos");
        // Abrir directo la sala de un proyecto (p. ej. desde la vista del proyecto)
        Long salaInicial = sala;
        if (salaInicial == null && proyecto != null) {
            salaInicial = chatService.salas(sesion.getPerfilId()).stream()
                    .filter(s -> s.proyectoId().equals(proyecto)).map(ChatService.SalaVista::id).findFirst().orElse(null);
        }
        model.addAttribute("salaInicial", salaInicial);
        model.addAttribute("maxCaracteres", ChatService.MAX_CARACTERES);
        return "compartido/chat";
    }
}
