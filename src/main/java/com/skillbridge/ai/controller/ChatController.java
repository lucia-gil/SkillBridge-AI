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
 * Página del chat (compartido/chat.html): salas de proyecto (Colaborador y
 * Project Manager) y mensajes directos (todos los roles; el Resource Manager
 * y el Administrador la ven como "Mensajes"). La conversación la maneja
 * js/chat.js contra ChatApiController (REST + jQuery/AJAX).
 * ?sala=ID abre una sala, ?proyecto=ID la del proyecto y ?con=PERFIL la
 * conversación directa con esa persona (la crea la primera vez).
 */
@Controller
public class ChatController {

    private final ShellModelBuilder shell;
    private final ChatService chatService;

    public ChatController(ShellModelBuilder shell, ChatService chatService) {
        this.shell = shell;
        this.chatService = chatService;
    }

    @GetMapping({"/colaborador/chat.html", "/project-manager/chat.html",
            "/resource-manager/chat.html", "/administrador/chat.html"})
    public String chat(@RequestParam(required = false) Long sala, @RequestParam(required = false) Long proyecto,
                       @RequestParam(required = false) Long con, HttpSession session, Model model) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        boolean staff = sesion.getRolOrganizacional() != null;
        shell.aplicar(model, sesion, "chat.html", staff ? "Mensajes" : "Chat",
                staff ? "Mensajes directos con las personas del equipo" : "Conversaciones con el equipo de tus proyectos y con Resource Managers");
        model.addAttribute("puedeEscribirATodos", staff);
        // Abrir directo la sala de un proyecto (p. ej. desde la vista del proyecto)
        Long salaInicial = sala;
        if (salaInicial == null && proyecto != null) {
            salaInicial = chatService.salas(sesion.getPerfilId()).stream()
                    .filter(s -> s.proyectoId().equals(proyecto)).map(ChatService.SalaVista::id).findFirst().orElse(null);
        }
        // ?con=PERFIL (p. ej. "Enviar mensaje" en el perfil de un colaborador)
        if (salaInicial == null && con != null) {
            try {
                salaInicial = chatService.abrirDirecto(sesion.getPerfilId(), con).id();
            } catch (ChatService.AccesoDenegado | com.skillbridge.ai.util.OperacionInvalidaException ex) {
                model.addAttribute("error", ex.getMessage());
            }
        }
        model.addAttribute("salaInicial", salaInicial);
        model.addAttribute("maxCaracteres", ChatService.MAX_CARACTERES);
        return "compartido/chat";
    }
}
