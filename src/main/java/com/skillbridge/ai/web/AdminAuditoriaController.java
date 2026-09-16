package com.skillbridge.ai.web;

import com.skillbridge.ai.dto.AuditoriaFila;
import com.skillbridge.ai.dto.TopUsuarioAuditoria;
import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.service.AuditoriaService;
import com.skillbridge.ai.service.ShellModelBuilder;
import com.skillbridge.ai.util.SesionKeys;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Auditoría (administrador/auditoria.html). Ver Javadoc de AuditoriaFila: tipo/severidad/origen se derivan del texto de "accion", no son columnas reales. */
@Controller
@RequestMapping("/administrador")
public class AdminAuditoriaController {

    private final AuditoriaService auditoriaService;
    private final ShellModelBuilder shellModelBuilder;

    public AdminAuditoriaController(AuditoriaService auditoriaService, ShellModelBuilder shellModelBuilder) {
        this.auditoriaService = auditoriaService;
        this.shellModelBuilder = shellModelBuilder;
    }

    @GetMapping("/auditoria.html")
    public String auditoria(HttpSession session, Model model) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);

        List<AuditoriaFila> eventos = auditoriaService.listarRecientes();
        List<TopUsuarioAuditoria> topUsuarios = auditoriaService.topUsuarios(5);
        Map<String, Long> porSeveridad = eventos.stream()
                .collect(Collectors.groupingBy(AuditoriaFila::getSeveridadCrudo, Collectors.counting()));

        shellModelBuilder.aplicar(model, sesion, "auditoria.html", "Auditoría",
                "Trazabilidad completa de acciones en la plataforma");

        model.addAttribute("eventos", eventos);
        model.addAttribute("topUsuarios", topUsuarios);
        model.addAttribute("altaCount", porSeveridad.getOrDefault("Alta", 0L));
        model.addAttribute("mediaCount", porSeveridad.getOrDefault("Media", 0L));
        model.addAttribute("infoCount", porSeveridad.getOrDefault("Info", 0L));
        return "administrador/auditoria";
    }
}
