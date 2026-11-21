package com.skillbridge.ai.controller;

import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.service.PendienteProyectoService;
import com.skillbridge.ai.service.ShellModelBuilder;
import com.skillbridge.ai.util.SesionKeys;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AdminPendientesController {
    private final PendienteProyectoService pendientes;
    private final ShellModelBuilder shell;

    public AdminPendientesController(PendienteProyectoService pendientes, ShellModelBuilder shell) {
        this.pendientes = pendientes;
        this.shell = shell;
    }

    @GetMapping("/administrador/pendientes.html")
    public String bandeja(@RequestParam(defaultValue = "sin-lider") String tipo,
                          @RequestParam(defaultValue = "0") int pagina, HttpSession session, Model model) {
        UsuarioSesion actor = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        shell.aplicar(model, actor, "pendientes.html", "Pendientes de proyectos", "Seguimiento operativo");
        model.addAttribute("bandeja", pendientes.buscar(PendienteProyectoService.Tipo.elegir(tipo), pagina));
        return "administrador/pendientes";
    }
}
