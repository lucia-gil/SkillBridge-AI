package com.skillbridge.ai.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.FlashMapManager;
import org.springframework.web.servlet.support.SessionFlashMapManager;

import java.io.IOException;

/**
 * failureHandler del formLogin: vuelve a /auth/login.html con el mensaje y el
 * correo escrito como flash attributes ("error", "correoIntentado"), que es
 * exactamente lo que ya muestra auth/login.html.
 *
 * <p>Solo se muestran los mensajes de AuthService (BadCredentialsException).
 * Cualquier otro error interno (BD caída, etc.) muestra un texto genérico,
 * para no filtrar detalles técnicos.
 */
@Component
public class LoginFallidoHandler implements AuthenticationFailureHandler {
    @org.springframework.beans.factory.annotation.Autowired
    private com.skillbridge.ai.service.IncidenteService incidentes;


    private final FlashMapManager flashMapManager = new SessionFlashMapManager();

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        incidentes.registrar(com.skillbridge.ai.service.IncidenteService.Regla.AUTENTICACION);
        String mensaje = exception instanceof BadCredentialsException && exception.getMessage() != null
                ? exception.getMessage()
                : "No se pudo iniciar sesión. Inténtalo de nuevo en unos minutos.";

        FlashMap flash = new FlashMap();
        flash.put("error", mensaje);
        String correo = request.getParameter("email");
        if (correo != null) {
            flash.put("correoIntentado", correo.length() > 150 ? correo.substring(0, 150) : correo);
        }
        flashMapManager.saveOutputFlashMap(flash, request, response);
        response.sendRedirect(request.getContextPath() + "/auth/login.html");
    }
}
