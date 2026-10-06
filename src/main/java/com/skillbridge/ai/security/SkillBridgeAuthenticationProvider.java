package com.skillbridge.ai.security;

import com.skillbridge.ai.dto.ResultadoLogin;
import com.skillbridge.ai.service.AuthService;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Component;

/**
 * Autentica el formulario de login (formLogin de SecurityConfig) contra la
 * tabla usuarios, reutilizando AuthService.intentarLogin:
 * <ul>
 *   <li>contraseña verificada con BCrypt (bean PasswordEncoder),</li>
 *   <li>cuenta suspendida → no entra,</li>
 *   <li>límite de intentos fallidos por IP (anti fuerza bruta),</li>
 *   <li>registro en auditoría de cada intento.</li>
 * </ul>
 * Cumple el mismo papel que el JdbcUserDetailsManager de la clase, pero con
 * nuestras reglas (rol en dos niveles, cuentas suspendidas, rate limit).
 *
 * <p>Es el ÚNICO AuthenticationProvider del contexto: Spring Boot lo usa como
 * AuthenticationManager global y desactiva el usuario "user" con contraseña
 * generada que crea por defecto.
 *
 * <p>La IP sale de {@link WebAuthenticationDetails#getRemoteAddress()}
 * (request.getRemoteAddr()), no de la cabecera X-Forwarded-For, que el
 * cliente puede falsificar para saltarse el bloqueo por intentos.
 */
@Component
public class SkillBridgeAuthenticationProvider implements AuthenticationProvider {

    private final AuthService authService;

    public SkillBridgeAuthenticationProvider(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String correo = authentication.getName();
        Object credenciales = authentication.getCredentials();
        String ip = authentication.getDetails() instanceof WebAuthenticationDetails detalles
                ? detalles.getRemoteAddress()
                : "desconocida";

        ResultadoLogin resultado = authService.intentarLogin(correo,
                credenciales != null ? credenciales.toString() : null, ip);
        if (!resultado.isExito()) {
            // El mensaje (credenciales incorrectas / cuenta inactiva / demasiados
            // intentos) lo muestra LoginFallidoHandler en la pantalla de login.
            throw new BadCredentialsException(resultado.getMensajeError());
        }
        return SesionSegura.autenticacion(resultado.getUsuarioSesion());
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
