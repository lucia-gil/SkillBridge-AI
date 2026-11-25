package com.skillbridge.ai.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillbridge.ai.dto.*;
import com.skillbridge.ai.security.SesionSegura;
import com.skillbridge.ai.service.AuthService;
import com.skillbridge.ai.service.DobleFactorService;
import com.skillbridge.ai.service.ConsultaPeruDniService;
import com.skillbridge.ai.service.IdentidadDniService;
import com.skillbridge.ai.util.OperacionInvalidaException;
import com.skillbridge.ai.util.Roles;
import com.skillbridge.ai.util.SesionKeys;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Login / registro / logout. Rutas con ".html" a proposito: preservan
 * exactamente los mismos href relativos que ya usaba el frontend estatico
 * (login.html, registro.html) para que toda la navegacion entre plantillas
 * siga funcionando sin tener que reescribir cada enlace.
 */
@Controller
public class AuthController {

    private final AuthService authService;
    private final ObjectMapper objectMapper;
    private final DobleFactorService dobleFactorService;
    private final SesionSegura sesionSegura;
    private final ConsultaPeruDniService consultaDni;
    private final IdentidadDniService identidadDni;

    private static final String PENDIENTE_2FA = SesionKeys.PENDIENTE_2FA;
    private static final String INTENTOS_2FA = SesionKeys.INTENTOS_2FA;

    public AuthController(AuthService authService, ObjectMapper objectMapper,
                          DobleFactorService dobleFactorService, SesionSegura sesionSegura,
                          ConsultaPeruDniService consultaDni, IdentidadDniService identidadDni) {
        this.authService = authService;
        this.objectMapper = objectMapper;
        this.dobleFactorService = dobleFactorService;
        this.sesionSegura = sesionSegura;
        this.consultaDni = consultaDni;
        this.identidadDni = identidadDni;
    }

    @GetMapping("/")
    public String raiz(HttpSession session) {
        if (session != null && session.getAttribute(SesionKeys.USUARIO) != null) {
            return "redirect:" + homeDe((UsuarioSesion) session.getAttribute(SesionKeys.USUARIO));
        }
        return "redirect:/bienvenida.html";
    }

    // ───────────────────────── Login ─────────────────────────

    /**
     * Solo muestra el formulario. El POST a /auth/login.html ya no llega a este
     * controlador: lo procesa el filtro formLogin de Spring Security
     * (config/SecurityConfig) con SkillBridgeAuthenticationProvider y los
     * handlers de security/ (éxito por rol + 2FA, error con mensaje).
     */
    @GetMapping("/auth/login.html")
    public String loginForm(HttpSession session,
                            @RequestParam(value = "salir", required = false) String salir,
                            Model model) {
        if (session.getAttribute(SesionKeys.USUARIO) != null) {
            return "redirect:" + homeDe((UsuarioSesion) session.getAttribute(SesionKeys.USUARIO));
        }
        if (salir != null && !model.containsAttribute("exito")) {
            model.addAttribute("exito", "Cerraste sesión correctamente.");
        }
        // "error", "correoIntentado" y "exito" llegan solos via flash attributes
        // cuando vienen de un redirect (POST /auth/login.html o /auth/registro.html);
        // en una entrada directa por GET simplemente no existen en el Model.
        return "auth/login";
    }

    /**
     * Token CSRF vigente para los formularios de /auth/. Una pantalla de login
     * abierta desde antes de que venciera la sesión trae un token que ya no
     * sirve: js/csrf-fresco.js pide este justo antes de enviar, para que el
     * primer intento no termine en "Tu sesión expiró".
     */
    @GetMapping(value = "/auth/csrf", produces = "application/json")
    @ResponseBody
    public ResponseEntity<Map<String, String>> csrfVigente(org.springframework.security.web.csrf.CsrfToken token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(Map.of("token", token.getToken(), "param", token.getParameterName()));
    }

    // ───────────────────────── Segundo factor ─────────────────────────

    @GetMapping("/auth/verificar-2fa.html")
    public String verificar2faForm(HttpSession session, Model model) {
        if (session.getAttribute(PENDIENTE_2FA) == null) {
            return "redirect:/auth/login.html";
        }
        return "auth/verificar-2fa";
    }

    @PostMapping("/auth/verificar-2fa.html")
    public String verificar2fa(@RequestParam String codigo, HttpServletRequest request, HttpServletResponse response,
                               RedirectAttributes redirectAttributes) {
        HttpSession session = request.getSession(false);
        UsuarioSesion pendiente = session == null ? null : (UsuarioSesion) session.getAttribute(PENDIENTE_2FA);
        if (pendiente == null) {
            return "redirect:/auth/login.html";
        }
        if (dobleFactorService.verificar(pendiente.getUsuarioId(), codigo)) {
            // Código correcto: recién aquí queda autenticado en Spring Security
            // (sesión nueva + SecurityContext guardado). Ver SesionSegura.
            sesionSegura.iniciarSesion(request, response, pendiente);
            request.getSession().setAttribute(SesionKeys.SEGUNDO_FACTOR, true);
            return "redirect:" + homeDe(pendiente);
        }
        Object n = session.getAttribute(INTENTOS_2FA);
        int intentos = (n instanceof Integer ? (Integer) n : 0) + 1;
        if (intentos >= 5) {
            session.invalidate();
            redirectAttributes.addFlashAttribute("error", "Demasiados codigos incorrectos. Inicia sesion de nuevo.");
            return "redirect:/auth/login.html";
        }
        session.setAttribute(INTENTOS_2FA, intentos);
        redirectAttributes.addFlashAttribute("error", "Codigo incorrecto. Te quedan " + (5 - intentos) + " intentos.");
        return "redirect:/auth/verificar-2fa.html";
    }

    /**
     * El cierre de sesión real es un POST a /auth/logout que procesa Spring
     * Security (invalida la sesión, borra la cookie y exige token CSRF, así
     * otra página no puede desloguearte con un simple enlace). Un GET viejo
     * (marcador, caché) solo vuelve al inicio, sin cerrar nada.
     */
    @GetMapping("/auth/logout")
    public String logoutPorGet() {
        return "redirect:/";
    }

    // ───────────────────────── Registro ─────────────────────────

    @GetMapping("/auth/registro.html")
    public String registroForm(@RequestParam(value = "correo", required = false) String correoInvitacion,
                               @RequestParam(required = false) String firma,
                               HttpSession session, HttpServletResponse response, Model model,
                               RedirectAttributes flash) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Referrer-Policy", "no-referrer");
        if (firma != null) {
            if (authService.invitacionParaConsultarDni(correoInvitacion, firma).isPresent()) {
                session.setAttribute(SesionKeys.FIRMA_INVITACION, firma);
                flash.addFlashAttribute("valCorreo", correoInvitacion);
            } else {
                session.removeAttribute(SesionKeys.FIRMA_INVITACION);
                flash.addFlashAttribute("errores", Map.of("correo", AuthService.ENLACE_REQUERIDO));
            }
            return "redirect:/auth/registro.html";
        }
        if (!model.containsAttribute("errores")) {
            model.addAttribute("errores", Collections.emptyMap());
        }
        boolean desdeInvitacion = correoInvitacion != null && !correoInvitacion.isBlank() && correoInvitacion.length() <= 150;
        if (!model.containsAttribute("valCorreo") && desdeInvitacion) {
            model.addAttribute("valCorreo", correoInvitacion.trim().toLowerCase());
        }
        model.addAttribute("desdeInvitacion", session.getAttribute(SesionKeys.FIRMA_INVITACION) != null);
        return "auth/registro";
    }

    // ───────────────────────── Recuperar contraseña ─────────────────────────

    @GetMapping("/auth/recuperar-password.html")
    public String recuperarPasswordForm() {
        return "auth/recuperar-password";
    }

    // Límite de correos de recuperación: evita llenar un buzón de enlaces.
    private static final int MAX_RECUPERACIONES_POR_IP = 5;
    private static final int MAX_RECUPERACIONES_POR_CORREO = 3;
    private final Map<String, VentanaVerificacion> recuperacionesPorIp = new ConcurrentHashMap<>();
    private final Map<String, VentanaVerificacion> recuperacionesPorCorreo = new ConcurrentHashMap<>();

    @PostMapping("/auth/recuperar-password.html")
    public String recuperarPassword(@RequestParam String correo, HttpServletRequest request,
                                    RedirectAttributes redirectAttributes) {
        if (!permitir(recuperacionesPorIp, obtenerIp(request), MAX_RECUPERACIONES_POR_IP)) {
            redirectAttributes.addFlashAttribute("error", "Hiciste demasiadas solicitudes. Espera unos minutos e inténtalo de nuevo.");
            redirectAttributes.addFlashAttribute("correoIntentado", correo);
            return "redirect:/auth/recuperar-password.html";
        }
        // Por correo el límite no se avisa: la pantalla dice lo mismo exista o
        // no la cuenta, y simplemente no se manda otro enlace.
        String clave = correo == null ? "" : correo.trim().toLowerCase();
        if (permitir(recuperacionesPorCorreo, clave, MAX_RECUPERACIONES_POR_CORREO)) {
            authService.solicitarRecuperacion(correo);
        }
        // Mismo mensaje exista o no el correo (no revelar que correos estan
        // registrados) - ver AuthService.solicitarRecuperacion().
        redirectAttributes.addFlashAttribute("enviado", true);
        redirectAttributes.addFlashAttribute("correoIntentado", correo);
        return "redirect:/auth/recuperar-password.html";
    }

    @GetMapping("/auth/restablecer-password.html")
    public String restablecerPasswordForm(@RequestParam(required = false) String token, Model model) {
        model.addAttribute("token", token);
        model.addAttribute("tokenValido", authService.tokenDeRecuperacionValido(token));
        return "auth/restablecer-password";
    }

    @PostMapping("/auth/restablecer-password.html")
    public String restablecerPassword(@RequestParam String token,
                                      @RequestParam String contrasena,
                                      @RequestParam String contrasena2,
                                      RedirectAttributes redirectAttributes) {
        String error = authService.restablecerContrasena(token, contrasena, contrasena2);
        if (error != null) {
            redirectAttributes.addFlashAttribute("error", error);
            // El token viene del usuario: se codifica para que no altere la URL de destino.
            return "redirect:/auth/restablecer-password.html?token="
                    + java.net.URLEncoder.encode(token, java.nio.charset.StandardCharsets.UTF_8);
        }
        redirectAttributes.addFlashAttribute("exito", "Contraseña actualizada. Ya puedes iniciar sesión.");
        return "redirect:/auth/login.html";
    }

    // Límite de verificaciones por IP: evita que alguien use el endpoint para
    // averiguar en masa qué correos están autorizados.
    private static final int MAX_VERIFICACIONES = 30;
    private static final long VENTANA_VERIFICACION_MS = 10 * 60 * 1000L;
    private final Map<String, VentanaVerificacion> verificacionesPorIp = new ConcurrentHashMap<>();
    private final Map<Long, VentanaVerificacion> verificacionesPorInvitacion = new ConcurrentHashMap<>();

    private record VentanaVerificacion(long inicio, int intentos) {}

    /**
     * Verificación en vivo del paso 1 de registro.html: ¿este correo puede
     * registrarse? Consulta correos_autorizados (y usuarios). Responde
     * El estado "autorizado" también requiere un enlace de invitación vigente en la sesión.
     * El servidor vuelve a validar todo al enviar el formulario (AuthService.registrar),
     * así que esto es solo ayuda visual.
     */
    @GetMapping("/auth/registro/verificar-correo")
    @ResponseBody
    public ResponseEntity<Map<String, String>> verificarCorreo(@RequestParam(value = "correo", required = false) String correo,
                                                               HttpServletRequest request) {
        if (!permitirVerificacion(request)) return estadoVerificacion(429, "limite");
        String estado = authService.estadoCorreoRegistro(correo);
        if (AuthService.CORREO_AUTORIZADO.equals(estado)
                && authService.invitacionParaConsultarDni(correo, firmaDe(request)).isEmpty()) {
            estado = AuthService.CORREO_REQUIERE_ENLACE;
        }
        return estadoVerificacion(200, estado);
    }

    @PostMapping("/auth/registro/verificar-dni")
    @ResponseBody
    public ResponseEntity<Map<String, String>> verificarDni(
            @RequestParam(defaultValue = "") String dni,
            @RequestParam(defaultValue = "") String correo,
            HttpServletRequest request) {
        if (!permitirVerificacion(request)) return estadoVerificacion(429, "limite");
        if (!consultaDni.formatoValido(dni)) return estadoVerificacion(400, "formato");
        var invitacion = authService.invitacionParaConsultarDni(correo, firmaDe(request));
        if (invitacion.isEmpty()) return estadoVerificacion(403, AuthService.CORREO_REQUIERE_ENLACE);
        if (!permitir(verificacionesPorInvitacion, invitacion.get(), 8)) return estadoVerificacion(429, "limite");
        if (!identidadDni.disponible()) return estadoVerificacion(503, "error");
        try {
            var persona = identidadDni.validarRegistro(dni, invitacion.get());
            return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                    .body(Map.of("estado", "encontrado", "nombres", persona.nombres(), "apellidos", persona.apellidos()));
        } catch (ConsultaPeruDniService.ConsultaNoDisponible ex) {
            return ResponseEntity.status(ex.limite() ? 429 : 503).cacheControl(CacheControl.noStore())
                    .body(Map.of("estado", ex.limite() ? "limite" : "error", "mensaje", ex.getMessage()));
        } catch (OperacionInvalidaException ex) {
            String estado = ConsultaPeruDniService.NO_ENCONTRADO.equals(ex.getMessage()) ? "no_encontrado" : "no_coincide";
            return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                    .body(Map.of("estado", estado, "mensaje", ex.getMessage()));
        }
    }

    private String firmaDe(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session == null ? null : (String) session.getAttribute(SesionKeys.FIRMA_INVITACION);
    }

    private boolean permitirVerificacion(HttpServletRequest request) {
        return permitir(verificacionesPorIp, obtenerIp(request), MAX_VERIFICACIONES);
    }

    private <K> boolean permitir(Map<K, VentanaVerificacion> ventanas, K id, int maximo) {
        long ahora = System.currentTimeMillis();
        var uso = ventanas.compute(id, (k, anterior) ->
                anterior == null || ahora - anterior.inicio() >= VENTANA_VERIFICACION_MS
                        ? new VentanaVerificacion(ahora, 1)
                        : new VentanaVerificacion(anterior.inicio(), Math.min(maximo + 1, anterior.intentos() + 1)));
        if (ventanas.size() > 10_000) {
            ventanas.entrySet().removeIf(e -> ahora - e.getValue().inicio() >= VENTANA_VERIFICACION_MS);
        }
        return uso.intentos() <= maximo;
    }

    private ResponseEntity<Map<String, String>> estadoVerificacion(int codigo, String estado) {
        return ResponseEntity.status(codigo).cacheControl(CacheControl.noStore()).body(Map.of("estado", estado));
    }

    @PostMapping("/auth/registro.html")
    public String registro(@RequestParam(required = false) String dni,
                           @RequestParam String correo,
                           @RequestParam(required = false) String cargo,
                           @RequestParam String contrasena,
                           @RequestParam String contrasena2,
                           @RequestParam(required = false, defaultValue = "[]") String habilidadesJson,
                           HttpServletRequest request, RedirectAttributes redirectAttributes) {
        if (!permitirVerificacion(request)) {
            redirectAttributes.addFlashAttribute("errores", Map.of("correo", "Demasiadas solicitudes. Espera unos minutos."));
            return "redirect:/auth/registro.html";
        }

        var invitacion = authService.invitacionParaConsultarDni(correo, firmaDe(request));
        if (invitacion.isPresent() && !permitir(verificacionesPorInvitacion, invitacion.get(), 8)) {
            redirectAttributes.addFlashAttribute("errores", Map.of("correo", "Hay demasiadas consultas para esta invitación. Espera unos minutos."));
            redirectAttributes.addFlashAttribute("valCorreo", correo);
            return "redirect:/auth/registro.html";
        }

        List<HabilidadDeclarada> habilidades;
        try {
            habilidades = objectMapper.readValue(habilidadesJson, new TypeReference<List<HabilidadDeclarada>>() {
            });
        } catch (Exception ex) {
            habilidades = Collections.emptyList();
        }

        RegistroRequest req = new RegistroRequest(dni, correo, cargo, contrasena, contrasena2, habilidades);
        ResultadoRegistro resultado;
        try {
            resultado = authService.registrar(req, firmaDe(request));
        } catch (OperacionInvalidaException ex) {
            resultado = ResultadoRegistro.conErrores(Map.of("dni", ex.getMessage()));
        }

        if (!resultado.isExito()) {
            redirectAttributes.addFlashAttribute("errores", resultado.getErrores());
            redirectAttributes.addFlashAttribute("valCorreo", correo);
            redirectAttributes.addFlashAttribute("valCargo", cargo);
            return "redirect:/auth/registro.html";
        }

        HttpSession session = request.getSession(false);
        if (session != null) session.removeAttribute(SesionKeys.FIRMA_INVITACION);
        redirectAttributes.addFlashAttribute("exito", "Cuenta creada exitosamente. Ya puedes iniciar sesión.");
        redirectAttributes.addFlashAttribute("correoIntentado", correo);
        return "redirect:/auth/login.html";
    }

    // ───────────────────────── Helpers ─────────────────────────

    private String homeDe(UsuarioSesion sesion) {
        String slug = Roles.slug(sesion.getRolEfectivo());
        return "/" + slug + "/inicio.html";
    }

    /**
     * IP real de la conexión. NO se usa X-Forwarded-For: el cliente puede
     * inventarla y saltarse el límite de intentos. Si algún día se pone un
     * proxy (Nginx) delante, activar server.forward-headers-strategy=native
     * y getRemoteAddr() devolverá la IP correcta.
     */
    private String obtenerIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}