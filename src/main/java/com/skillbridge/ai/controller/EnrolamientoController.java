package com.skillbridge.ai.controller;

import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.security.PortalFilter;
import com.skillbridge.ai.service.DobleFactorService;
import com.skillbridge.ai.util.SesionKeys;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/administrador")
public class EnrolamientoController {
    private final DobleFactorService servicio;
    public EnrolamientoController(DobleFactorService servicio) { this.servicio = servicio; }

    @GetMapping("/enrolamiento.html")
    public String formulario() { return "administrador/enrolamiento"; }

    @PostMapping("/enrolamiento/iniciar")
    public String iniciar(HttpSession session, Model model) {
        var u = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        model.addAttribute("secreto", servicio.iniciarConfiguracion(u.getUsuarioId(), u.getCorreo()).get("secreto"));
        return formulario();
    }

    @PostMapping("/enrolamiento/activar")
    public String activar(@RequestParam String codigo, HttpSession session, Model model) {
        var u = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        if (servicio.activar(u.getUsuarioId(), codigo)) {
            session.setAttribute(PortalFilter.SEGUNDO_FACTOR, true);
            return "redirect:/administrador/inicio.html";
        }
        model.addAttribute("error", "Código incorrecto. Verifica la hora de tu autenticador.");
        return formulario();
    }
}
