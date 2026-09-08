package com.skillbridge.ai.web;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillbridge.ai.dto.*;
import com.skillbridge.ai.service.AuthService;
import com.skillbridge.ai.service.ConfiguracionService;
import com.skillbridge.ai.util.Roles;
import com.skillbridge.ai.util.SesionKeys;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Login / registro / logout. Rutas con ".html" a proposito: preservan
 * exactamente los mismos href relativos que ya usaba el frontend estatico
 * (login.html, registro.html) para que toda la navegacion entre plantillas
 * siga funcionando sin tener que reescribir cada enlace.
 */
@Controller
public class AuthController {

    private final AuthService authService;
    private final ConfiguracionService configuracionService;
    private final ObjectMapper objectMapper;

    public AuthController(AuthService authService, ConfiguracionService configuracionService, ObjectMapper objectMapper) {
        this.authService = authService;
        this.configuracionService = configuracionService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/")
    public String raiz(HttpSession session) {
        if (session != null && session.getAttribute(SesionKeys.USUARIO) != null) {
            return "redirect:" + homeDe((UsuarioSesion) session.getAttribute(SesionKeys.USUARIO));
        }
        return "redirect:/auth/login.html";
    }

    // ───────────────────────── Login ─────────────────────────

    @GetMapping("/auth/login.html")
    public String loginForm(HttpSession session, Model model) {
        if (session.getAttribute(SesionKeys.USUARIO) != null) {
            return "redirect:" + homeDe((UsuarioSesion) session.getAttribute(SesionKeys.USUARIO));
        }
        // "error", "correoIntentado" y "exito" llegan solos via flash attributes
        // cuando vienen de un redirect (POST /auth/login.html o /auth/registro.html);
        // en una entrada directa por GET simplemente no existen en el Model.
        return "auth/login";
    }

    @PostMapping("/auth/login.html")
    public String login(@RequestParam String email,
                         @RequestParam String password,
                         HttpServletRequest request,
                         HttpServletResponse response,
                         RedirectAttributes redirectAttributes) {

        // Cabeceras de seguridad (mismo criterio que LoginServlet de QuintaOla-SGA)
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Cache-Control", "no-store");

        String ip = obtenerIp(request);
        ResultadoLogin resultado = authService.intentarLogin(email, password, ip);

        if (!resultado.isExito()) {
            redirectAttributes.addFlashAttribute("error", resultado.getMensajeError());
            redirectAttributes.addFlashAttribute("correoIntentado", email);
            return "redirect:/auth/login.html";
        }

        // Prevencion de session fixation: se invalida cualquier sesion previa
        // y se emite una nueva antes de guardar al usuario autenticado.
        HttpSession vieja = request.getSession(false);
        if (vieja != null) {
            vieja.invalidate();
        }
        HttpSession nueva = request.getSession(true);
        // Duración de sesión configurable (RF08): antes 30 min fijos; ahora lee
        // configuracion_global.expiracion_sesion_minutos, para que el valor que
        // el Administrador ajusta en Configuración tenga efecto real.
        String minutosTexto = configuracionService.valor(configuracionService.obtenerMapa(), "expiracion_sesion_minutos", "30");
        int minutos;
        try {
            minutos = Math.max(1, Integer.parseInt(minutosTexto.trim()));
        } catch (NumberFormatException ex) {
            minutos = 30;
        }
        nueva.setMaxInactiveInterval(minutos * 60);
        nueva.setAttribute(SesionKeys.USUARIO, resultado.getUsuarioSesion());

        return "redirect:" + homeDe(resultado.getUsuarioSesion());
    }

    @GetMapping("/auth/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/auth/login.html";
    }

    // ───────────────────────── Registro ─────────────────────────

    @GetMapping("/auth/registro.html")
    public String registroForm(Model model) {
        if (!model.containsAttribute("errores")) {
            model.addAttribute("errores", Collections.emptyMap());
        }
        return "auth/registro";
    }

    @PostMapping("/auth/registro.html")
    public String registro(@RequestParam String nombres,
                            @RequestParam String apellidos,
                            @RequestParam String correo,
                            @RequestParam(required = false) String cargo,
                            @RequestParam String contrasena,
                            @RequestParam String contrasena2,
                            @RequestParam(required = false, defaultValue = "[]") String habilidadesJson,
                            RedirectAttributes redirectAttributes) {

        List<HabilidadDeclarada> habilidades;
        try {
            habilidades = objectMapper.readValue(habilidadesJson, new TypeReference<List<HabilidadDeclarada>>() {
            });
        } catch (Exception ex) {
            habilidades = Collections.emptyList();
        }

        RegistroRequest req = new RegistroRequest(nombres, apellidos, correo, cargo, contrasena, contrasena2, habilidades);
        ResultadoRegistro resultado = authService.registrar(req);

        if (!resultado.isExito()) {
            redirectAttributes.addFlashAttribute("errores", resultado.getErrores());
            redirectAttributes.addFlashAttribute("valNombres", nombres);
            redirectAttributes.addFlashAttribute("valApellidos", apellidos);
            redirectAttributes.addFlashAttribute("valCorreo", correo);
            redirectAttributes.addFlashAttribute("valCargo", cargo);
            return "redirect:/auth/registro.html";
        }

        redirectAttributes.addFlashAttribute("exito", "Cuenta creada exitosamente. Ya puedes iniciar sesión.");
        redirectAttributes.addFlashAttribute("correoIntentado", correo);
        return "redirect:/auth/login.html";
    }

    // ───────────────────────── Helpers ─────────────────────────

    private String homeDe(UsuarioSesion sesion) {
        String slug = Roles.slug(sesion.getRolEfectivo());
        return "/" + slug + "/inicio.html";
    }

    private String obtenerIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank()) {
            return ip.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
