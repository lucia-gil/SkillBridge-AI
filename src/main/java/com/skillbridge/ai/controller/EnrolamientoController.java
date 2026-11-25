package com.skillbridge.ai.controller;

import com.skillbridge.ai.dto.UsuarioSesion;
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
    public String formulario(HttpSession session, Model model) {
        mostrarPendiente(session, model);
        return "administrador/enrolamiento";
    }

    /** Si ya generó una clave y no la activó, se vuelve a mostrar el mismo QR (no se genera otra). */
    private void mostrarPendiente(HttpSession session, Model model) {
        var u = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        if (u == null || model.containsAttribute("secreto")) return;
        servicio.configuracionPendiente(u.getUsuarioId(), u.getCorreo()).ifPresent(d -> {
            model.addAttribute("secreto", d.get("secreto"));
            model.addAttribute("uri", d.get("uri"));
        });
    }

    @PostMapping("/enrolamiento/iniciar")
    public String iniciar(HttpSession session, Model model) {
        var u = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        try {
            var datos = servicio.iniciarConfiguracion(u.getUsuarioId(), u.getCorreo());
            model.addAttribute("secreto", datos.get("secreto"));
            model.addAttribute("uri", datos.get("uri"));   // otpauth://, se dibuja como QR en el navegador
        } catch (IllegalStateException e) {
            model.addAttribute("error", e.getMessage());
        }
        return formulario(session, model);
    }

    @PostMapping("/enrolamiento/activar")
    public String activar(@RequestParam String codigo, HttpSession session, Model model) {
        var u = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        if (servicio.activar(u.getUsuarioId(), codigo)) {
            session.setAttribute(SesionKeys.SEGUNDO_FACTOR, true);
            return "redirect:/administrador/inicio.html";
        }
        model.addAttribute("error", "Código incorrecto. Verifica la hora de tu autenticador.");
        return formulario(session, model);
    }
}
