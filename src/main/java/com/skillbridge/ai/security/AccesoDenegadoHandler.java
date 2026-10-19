package com.skillbridge.ai.security;

import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.util.SesionKeys;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.FlashMapManager;
import org.springframework.web.servlet.support.SessionFlashMapManager;

import java.io.IOException;

/**
 * Qué pasa cuando Spring Security niega un request:
 * <ul>
 *   <li><b>Token CSRF faltante o inválido</b> (formulario de otra página, o la
 *       sesión venció con la página abierta): no se ejecuta la acción; se avisa
 *       y se manda al login (sin sesión) o al inicio (con sesión).</li>
 *   <li><b>Sin la autoridad de esa área</b> (p.ej. un Colaborador abre
 *       /administrador/usuarios.html): GET → se le redirige a SU inicio, como
 *       hacía antes SesionInterceptor; cualquier otro método → 403.</li>
 * </ul>
 */
@Component
public class AccesoDenegadoHandler implements AccessDeniedHandler {

    private final FlashMapManager flashMapManager = new SessionFlashMapManager();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        HttpSession sesion = request.getSession(false);
        UsuarioSesion usuario = sesion != null ? (UsuarioSesion) sesion.getAttribute(SesionKeys.USUARIO) : null;
        String contexto = request.getContextPath();

        if (accessDeniedException instanceof CsrfException) {
            if (esPeticionDeDatos(request)) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Token CSRF inválido o vencido");
                return;
            }
            FlashMap flash = new FlashMap();
            flash.put("error", usuario != null
                    ? "El formulario quedó desactualizado y no se guardó. Vuelve a intentarlo."
                    : "Tu sesión expiró. Inicia sesión de nuevo.");
            flashMapManager.saveOutputFlashMap(flash, request, response);
            response.sendRedirect(contexto + (usuario != null ? SesionSegura.inicioDe(usuario) : "/auth/login.html"));
            return;
        }

        // Decisión del equipo: el acceso negado se muestra explícito con
        // templates/error/403.html, en vez de redirigir en silencio al inicio.
        response.sendError(HttpServletResponse.SC_FORBIDDEN,
                "No tienes permiso para acceder a esta seccion.");
    }

    /** fetch() que espera JSON: no tiene sentido responderle con una redirección a HTML. */
    private static boolean esPeticionDeDatos(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        return accept != null && accept.contains("application/json") && !accept.contains("text/html");
    }
}
