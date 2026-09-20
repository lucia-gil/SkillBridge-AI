package com.skillbridge.ai.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Landing pública de SkillBridge AI (pre-login). Ruta separada de "/" a
 * propósito: hoy AuthController.raiz() redirige "/" directo a
 * /auth/login.html, y no queremos alterar ese flujo sin que lo decidan.
 *
 * Si más adelante quieren que "/" muestre esta landing en vez de saltar
 * directo al login, basta con cambiar el "redirect:/auth/login.html" de
 * AuthController.raiz() por "redirect:/bienvenida.html" cuando no hay
 * sesión activa.
 */
@Controller
public class PublicoController {

    @GetMapping("/bienvenida.html")
    public String landing() {
        return "publico/landing";
    }
}