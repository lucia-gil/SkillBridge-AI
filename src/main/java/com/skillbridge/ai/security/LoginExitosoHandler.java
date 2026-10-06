package com.skillbridge.ai.security;

import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.service.DobleFactorService;
import com.skillbridge.ai.util.SesionKeys;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;

/**
 * successHandler del formLogin (Clase 6.1): decide a dónde va cada usuario.
 * <ol>
 *   <li>Si tiene doble factor activo, la contraseña NO basta: se descarta la
 *       autenticación, se abre una sesión nueva solo con "pendiente de 2FA"
 *       y se le manda a /auth/verificar-2fa.html (5 minutos para el código).</li>
 *   <li>Si intentó abrir una página protegida antes de loguearse
 *       (DefaultSavedRequest), vuelve a esa página.</li>
 *   <li>Si no, va al inicio de su rol: administrador, resource-manager,
 *       project-manager (Miembro que lidera) o colaborador.</li>
 * </ol>
 */
@Component
public class LoginExitosoHandler implements AuthenticationSuccessHandler {

    private final DobleFactorService dobleFactorService;
    private final SesionSegura sesionSegura;
    private final RequestCache requestCache = new HttpSessionRequestCache();

    public LoginExitosoHandler(DobleFactorService dobleFactorService, SesionSegura sesionSegura) {
        this.dobleFactorService = dobleFactorService;
        this.sesionSegura = sesionSegura;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        UsuarioSesion usuario = (UsuarioSesion) authentication.getPrincipal();

        if (dobleFactorService.estaActivo(usuario.getUsuarioId())) {
            SesionSegura.cerrar(request);
            HttpSession pendiente = request.getSession(true);
            pendiente.setMaxInactiveInterval(5 * 60);
            pendiente.setAttribute(SesionKeys.PENDIENTE_2FA, usuario);
            pendiente.setAttribute(SesionKeys.INTENTOS_2FA, 0);
            response.sendRedirect(request.getContextPath() + "/auth/verificar-2fa.html");
            return;
        }

        sesionSegura.completarLogin(request, usuario);

        SavedRequest guardada = requestCache.getRequest(request, response);
        requestCache.removeRequest(request, response);
        if (guardada != null && esPagina(guardada.getRedirectUrl())) {
            response.sendRedirect(guardada.getRedirectUrl());
            return;
        }
        response.sendRedirect(request.getContextPath() + SesionSegura.inicioDe(usuario));
    }

    /** Solo se vuelve a PÁGINAS (.html), no a una imagen o JSON que se pidió de fondo. */
    private static boolean esPagina(String url) {
        try {
            String ruta = URI.create(url).getPath();
            return ruta != null && ruta.endsWith(".html");
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
