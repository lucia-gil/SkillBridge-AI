package com.skillbridge.ai.security;

import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.service.DobleFactorService;
import com.skillbridge.ai.service.GoogleAccessService;
import com.skillbridge.ai.util.SesionKeys;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;

/**
 * Completa el acceso por contraseña o Google y decide a dónde va cada usuario.
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

    private static final Logger LOG = LoggerFactory.getLogger(LoginExitosoHandler.class);

    private final DobleFactorService dobleFactorService;
    private final SesionSegura sesionSegura;
    private final GoogleAccessService googleAccess;
    private final LoginFallidoHandler loginFallido;
    private final boolean googleHabilitado;
    private final RequestCache requestCache = new HttpSessionRequestCache();

    public LoginExitosoHandler(DobleFactorService dobleFactorService, SesionSegura sesionSegura,
                               GoogleAccessService googleAccess, LoginFallidoHandler loginFallido,
                               @Value("${app.auth.google.enabled:false}") boolean googleHabilitado) {
        this.dobleFactorService = dobleFactorService;
        this.sesionSegura = sesionSegura;
        this.googleAccess = googleAccess;
        this.loginFallido = loginFallido;
        this.googleHabilitado = googleHabilitado;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        var sesion = request.getSession(false);
        var vinculo = IdentidadGoogle.Pendiente.leer(sesion, IdentidadGoogle.ATRIBUTO_PENDIENTE);
        if (sesion != null) {
            sesion.removeAttribute(IdentidadGoogle.ATRIBUTO_PENDIENTE);
        }
        if (!"1".equals(request.getParameter("vincularGoogle"))) {
            completar(request, response, authentication, null);
            return;
        }

        var usuario = (UsuarioSesion) authentication.getPrincipal();
        if (!googleHabilitado || vinculo == null || !vinculo.identidad().coincideCon(usuario.getCorreo())) {
            SesionSegura.cerrar(request);
            loginFallido.onAuthenticationFailure(request, response, new BadCredentialsException(
                    "La confirmación de Google expiró o el correo es distinto. Vuelve a elegir tu cuenta de Google."));
            return;
        }
        completar(request, response, authentication, vinculo);
    }

    void completar(HttpServletRequest request, HttpServletResponse response, Authentication authentication,
                   IdentidadGoogle.Pendiente vinculo) throws IOException {
        UsuarioSesion usuario = (UsuarioSesion) authentication.getPrincipal();

        if (dobleFactorService.estaActivo(usuario.getUsuarioId())) {
            SesionSegura.cerrar(request);
            HttpSession pendiente = request.getSession(true);
            pendiente.setMaxInactiveInterval(5 * 60);
            pendiente.setAttribute(SesionKeys.PENDIENTE_2FA, usuario);
            pendiente.setAttribute(SesionKeys.INTENTOS_2FA, 0);
            if (vinculo != null) {
                pendiente.setAttribute(IdentidadGoogle.ATRIBUTO_SEGUNDO_FACTOR, vinculo);
            }
            response.sendRedirect(request.getContextPath() + "/auth/verificar-2fa.html");
            return;
        }

        if (vinculo != null) {
            try {
                googleAccess.confirmarVinculo(vinculo.identidad(), usuario.getUsuarioId());
            } catch (BadCredentialsException ex) {
                SesionSegura.cerrar(request);
                loginFallido.onAuthenticationFailure(request, response, ex);
                return;
            } catch (RuntimeException ex) {
                LOG.error("Falló el vínculo con Google.", ex);
                SesionSegura.cerrar(request);
                loginFallido.onAuthenticationFailure(request, response, new BadCredentialsException(GoogleLoginHandler.ERROR));
                return;
            }
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
