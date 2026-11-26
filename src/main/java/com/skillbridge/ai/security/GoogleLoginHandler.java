package com.skillbridge.ai.security;

import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.service.GoogleAccessService;
import com.skillbridge.ai.util.SesionKeys;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;

@Component
public class GoogleLoginHandler implements AuthenticationSuccessHandler, AuthenticationFailureHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GoogleLoginHandler.class);
    public static final String ERROR = "No se pudo iniciar sesión con Google. Vuelve a intentarlo o usa tu contraseña.";

    private final GoogleAccessService acceso;
    private final SesionSegura sesiones;
    private final LoginExitosoHandler loginExitoso;
    private final LoginFallidoHandler loginFallido;

    public GoogleLoginHandler(GoogleAccessService acceso, SesionSegura sesiones, LoginExitosoHandler loginExitoso,
                              LoginFallidoHandler loginFallido) {
        this.acceso = acceso;
        this.sesiones = sesiones;
        this.loginExitoso = loginExitoso;
        this.loginFallido = loginFallido;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        boolean cancelado = exception instanceof OAuth2AuthenticationException oauthError
                && "access_denied".equals(oauthError.getError().getErrorCode());
        String mensaje = cancelado ? "Cancelaste el acceso con Google. Puedes volver a intentarlo." : ERROR;
        loginFallido.onAuthenticationFailure(request, response, new BadCredentialsException(mensaje));
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        SecurityContextHolder.clearContext();
        var sesion = request.getSession(true);
        if (sesion.getAttribute(SesionKeys.USUARIO) != null) {
            response.sendRedirect(request.getContextPath() + "/auth/login.html");
            return;
        }
        sesion.removeAttribute(IdentidadGoogle.ATRIBUTO_PENDIENTE);
        sesion.removeAttribute(IdentidadGoogle.ATRIBUTO_SEGUNDO_FACTOR);
        sesion.removeAttribute(SesionKeys.PENDIENTE_2FA);
        sesion.removeAttribute(SesionKeys.INTENTOS_2FA);
        sesion.removeAttribute(SesionKeys.SEGUNDO_FACTOR);

        if (!(authentication.getPrincipal() instanceof OidcUser google)) {
            loginFallido.onAuthenticationFailure(request, response, new BadCredentialsException(ERROR));
            return;
        }

        IdentidadGoogle identidad;
        Optional<UsuarioSesion> usuario;
        try {
            identidad = IdentidadGoogle.desde(google.getIdToken());
            usuario = acceso.iniciar(identidad, request.getRemoteAddr());
        } catch (BadCredentialsException ex) {
            loginFallido.onAuthenticationFailure(request, response, ex);
            return;
        } catch (RuntimeException ex) {
            LOG.error("Falló la resolución de la cuenta local durante el acceso con Google.", ex);
            loginFallido.onAuthenticationFailure(request, response, new BadCredentialsException(ERROR));
            return;
        }

        if (usuario.isEmpty()) {
            sesion.setAttribute(IdentidadGoogle.ATRIBUTO_PENDIENTE, IdentidadGoogle.Pendiente.crear(identidad));
            response.sendRedirect(request.getContextPath() + "/auth/login.html");
            return;
        }

        Authentication local = SesionSegura.autenticacion(usuario.get());
        sesiones.guardar(request, response, local);
        loginExitoso.completar(request, response, local, null);
    }
}
