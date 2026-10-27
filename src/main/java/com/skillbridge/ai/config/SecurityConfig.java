package com.skillbridge.ai.config;

import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.repository.UsuarioRepository;
import com.skillbridge.ai.security.AccesoDenegadoHandler;
import com.skillbridge.ai.security.LoginExitosoHandler;
import com.skillbridge.ai.security.LoginFallidoHandler;
import com.skillbridge.ai.security.SesionSegura;
import com.skillbridge.ai.security.SesionVigenteFilter;
import com.skillbridge.ai.service.AuditoriaService;
import com.skillbridge.ai.service.AuthService;
import com.skillbridge.ai.util.Roles;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

import static org.springframework.security.web.util.matcher.AntPathRequestMatcher.antMatcher;

/**
 * Configuración de Spring Security (Clase 6.1 / 6.2).
 *
 * <pre>
 *  Ruta                                   Quién entra
 *  ─────────────────────────────────────  ─────────────────────────────────────────
 *  /, /bienvenida.html, /auth/**, estáticos  cualquiera (login, registro, 2FA…)
 *  /administrador/**                      hasAuthority("administrador")
 *  /resource-manager/**                   hasAuthority("resource_manager")
 *  /project-manager/**                    hasAuthority("project_manager")  (Miembro que lidera)
 *  /colaborador/**                        hasAuthority("colaborador")      (cualquier Miembro)
 *  todo lo demás (/cuenta, /foros, …)     authenticated()
 * </pre>
 *
 * Además: formLogin propio, successHandler por rol (+2FA), logout por POST
 * que invalida la sesión y borra la cookie, CSRF en todos los POST,
 * protección contra session fixation y cabeceras de seguridad.
 *
 * <p>Se usa {@code antMatcher(...)} en vez de {@code requestMatchers(String)}
 * porque el WAR se despliega en un Tomcat externo que registra más de un
 * servlet (default + jsp) y Spring Security 6 no puede decidir solo si el
 * patrón es de Spring MVC o no.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /** BCrypt para las contraseñas (mismo algoritmo que ya tenían los hashes guardados). */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** Dónde vive el SecurityContext entre requests: en la sesión (y esta, en MySQL vía Spring Session). */
    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new DelegatingSecurityContextRepository(
                new RequestAttributeSecurityContextRepository(),
                new HttpSessionSecurityContextRepository());
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           SecurityContextRepository securityContextRepository,
                                           LoginExitosoHandler loginExitosoHandler,
                                           LoginFallidoHandler loginFallidoHandler,
                                           AccesoDenegadoHandler accesoDenegadoHandler,
                                           SesionSegura sesionSegura,
                                           UsuarioRepository usuarioRepository,
                                           AuthService authService,
                                           AuditoriaService auditoriaService) throws Exception {

        // Token CSRF cargado al inicio de cada request (no diferido): las
        // plantillas Thymeleaf grandes empiezan a enviarse antes de terminar
        // de renderizar y, si el token se generara a mitad de la página, ya no
        // se podría crear la sesión que lo guarda. Xor = protección BREACH.
        XorCsrfTokenRequestAttributeHandler csrfHandler = new XorCsrfTokenRequestAttributeHandler();
        csrfHandler.setCsrfRequestAttributeName(null);

        http
            .securityContext(contexto -> contexto.securityContextRepository(securityContextRepository))

            .authorizeHttpRequests(auth -> auth
                    // Páginas de error y forwards internos
                    .dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.FORWARD).permitAll()
                    // Recursos estáticos
                    .requestMatchers(antMatcher("/css/**"), antMatcher("/js/**"), antMatcher("/img/**"),
                            antMatcher("/fonts/**"), antMatcher("/favicon.ico")).permitAll()
                    // Públicas: landing, login, registro, verificación 2FA, recuperar contraseña
                    .requestMatchers(antMatcher("/"), antMatcher("/bienvenida.html"),
                            antMatcher("/auth/**"), antMatcher("/error")).permitAll()
                    // Por rol (modelo v7, ver SesionSegura.autoridades)
                    .requestMatchers(antMatcher("/administrador/**")).hasAuthority(Roles.ADMINISTRADOR)
                    .requestMatchers(antMatcher("/resource-manager/**")).hasAuthority(Roles.RESOURCE_MANAGER)
                    .requestMatchers(antMatcher("/project-manager/**")).hasAuthority(Roles.PROJECT_MANAGER)
                    .requestMatchers(antMatcher("/colaborador/**")).hasAuthority(Roles.COLABORADOR)
                    // Compartidas por todos los roles (Mi cuenta, notificaciones, foros,
                    // descargas…): basta con haber iniciado sesión; cada controlador
                    // verifica además que el recurso sea suyo o de su proyecto.
                    .anyRequest().authenticated())

            .formLogin(form -> form
                    .loginPage("/auth/login.html")
                    .loginProcessingUrl("/auth/login.html")   // POST del formulario
                    .usernameParameter("email")
                    .passwordParameter("password")
                    .successHandler(loginExitosoHandler)
                    .failureHandler(loginFallidoHandler)
                    .permitAll())

            .logout(logout -> logout
                    .logoutUrl("/auth/logout")                // solo POST (con token CSRF)
                    .addLogoutHandler((request, response, authentication) -> {
                        if (authentication != null && authentication.getPrincipal() instanceof UsuarioSesion u) {
                            auditoriaService.registrar(u.getUsuarioId(), "LOGOUT", "Cierre de sesión");
                        }
                    })
                    .invalidateHttpSession(true)
                    .clearAuthentication(true)
                    .deleteCookies("JSESSIONID", "SESSION")  // Tomcat / Spring Session
                    .logoutSuccessUrl("/auth/login.html?salir")
                    .permitAll())

            .csrf(csrf -> csrf.csrfTokenRequestHandler(csrfHandler))

            .sessionManagement(sesion -> sesion
                    .sessionFixation(fixation -> fixation.changeSessionId()))

            .exceptionHandling(ex -> ex.accessDeniedHandler(accesoDenegadoHandler))

            .headers(headers -> headers
                    .frameOptions(frame -> frame.sameOrigin())
                    .contentSecurityPolicy(csp -> csp.policyDirectives(
                            "frame-ancestors 'self'; object-src 'none'; base-uri 'self'; form-action 'self'"))
                    .referrerPolicy(referrer -> referrer.policy(
                            ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN)))

            .addFilterBefore(new SesionVigenteFilter(usuarioRepository, authService, sesionSegura),
                    AnonymousAuthenticationFilter.class);

        return http.build();
    }
}
