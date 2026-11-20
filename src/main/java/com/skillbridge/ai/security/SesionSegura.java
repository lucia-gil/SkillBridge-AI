package com.skillbridge.ai.security;

import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.service.ConfiguracionService;
import com.skillbridge.ai.service.NotificacionService;
import com.skillbridge.ai.util.Roles;
import com.skillbridge.ai.util.SesionKeys;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Piezas compartidas del inicio de sesión con Spring Security (Clase 6.1).
 *
 * <p>Las AUTORIDADES (lo que evalúa {@code hasAuthority(...)} en
 * {@link com.skillbridge.ai.config.SecurityConfig} y {@code sec:authorize} en
 * las plantillas) salen del modelo de permisos v7:
 * <ul>
 *   <li>rol_organizacional = administrador    → {@code administrador}</li>
 *   <li>rol_organizacional = resource_manager → {@code resource_manager}</li>
 *   <li>Miembro (sin rol fijo)                 → {@code colaborador}</li>
 *   <li>Miembro que lidera ≥ 1 proyecto       → {@code colaborador} + {@code project_manager}</li>
 * </ul>
 * Son "en caliente": {@link SesionVigenteFilter} las recalcula si cambia el
 * rol o el liderazgo mientras la sesión está abierta.
 *
 * <p>Además del SecurityContext (que guarda Spring Security), la aplicación
 * sigue usando el atributo de sesión {@link SesionKeys#USUARIO}
 * ({@link UsuarioSesion}) para la navegación (rolEfectivo, "Ver como"…),
 * así que ningún controlador existente tuvo que cambiar.
 */
@Component
public class SesionSegura {

    private static final Logger LOG = LoggerFactory.getLogger(SesionSegura.class);

    private final ConfiguracionService configuracionService;
    private final SecurityContextRepository securityContextRepository;
    private final NotificacionService notificacionService;

    public SesionSegura(ConfiguracionService configuracionService,
                        SecurityContextRepository securityContextRepository,
                        NotificacionService notificacionService) {
        this.configuracionService = configuracionService;
        this.securityContextRepository = securityContextRepository;
        this.notificacionService = notificacionService;
    }

    /** Autoridades según el modelo v7 (ver javadoc de la clase). */
    public static List<GrantedAuthority> autoridades(String rolOrganizacional, boolean lideraProyectos) {
        if (Roles.ADMINISTRADOR.equals(rolOrganizacional)) {
            return List.of(new SimpleGrantedAuthority(Roles.ADMINISTRADOR));
        }
        if (Roles.RESOURCE_MANAGER.equals(rolOrganizacional)) {
            return List.of(new SimpleGrantedAuthority(Roles.RESOURCE_MANAGER));
        }
        if (lideraProyectos) {
            return List.of(new SimpleGrantedAuthority(Roles.COLABORADOR),
                    new SimpleGrantedAuthority(Roles.PROJECT_MANAGER));
        }
        return List.of(new SimpleGrantedAuthority(Roles.COLABORADOR));
    }

    /** Authentication ya autenticado: principal = UsuarioSesion, sin contraseña. */
    public static Authentication autenticacion(UsuarioSesion usuario) {
        return UsernamePasswordAuthenticationToken.authenticated(usuario, null,
                autoridades(usuario.getRolOrganizacional(), usuario.isLideraProyectos()));
    }

    /** Página de inicio de la vista activa ("/administrador/inicio.html", ...). */
    public static String inicioDe(UsuarioSesion usuario) {
        return "/" + Roles.slug(usuario.getRolEfectivo()) + "/inicio.html";
    }

    /**
     * Login por formulario ya aceptado por Spring Security (que ya rotó el id
     * de sesión y guardó el SecurityContext): solo falta dejar el
     * UsuarioSesion y la duración configurada por el Administrador (RF08).
     */
    public void completarLogin(HttpServletRequest request, UsuarioSesion usuario) {
        HttpSession sesion = request.getSession(true);
        sesion.setMaxInactiveInterval(minutosDeSesion() * 60);
        sesion.setAttribute(SesionKeys.USUARIO, usuario);
        if (Roles.ADMINISTRADOR.equals(usuario.getRolOrganizacional())) {
            try {
                notificacionService.avisarInicioAdministrador(usuario.getPerfilId());
            } catch (RuntimeException ex) {
                LOG.warn("No se pudo guardar el aviso de inicio de sesión del perfil {}.", usuario.getPerfilId(), ex);
            }
        }
    }

    /**
     * Login que termina FUERA del formulario de Spring Security (segundo
     * factor correcto): sesión nueva (anti session-fixation) + SecurityContext
     * guardado a mano en el repositorio, como indica la documentación de
     * Spring Security 6 para autenticaciones programáticas.
     */
    public void iniciarSesion(HttpServletRequest request, HttpServletResponse response, UsuarioSesion usuario) {
        HttpSession anterior = request.getSession(false);
        if (anterior != null) {
            anterior.invalidate();
        }
        completarLogin(request, usuario);
        guardar(request, response, autenticacion(usuario));
    }

    /** Reemplaza la autenticación actual (p.ej. autoridades recalculadas) y la persiste. */
    public void guardar(HttpServletRequest request, HttpServletResponse response, Authentication autenticacion) {
        SecurityContext contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(autenticacion);
        SecurityContextHolder.setContext(contexto);
        securityContextRepository.saveContext(contexto, request, response);
    }

    /** Cierra la sesión completa: SecurityContext + HttpSession. */
    public static void cerrar(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        HttpSession sesion = request.getSession(false);
        if (sesion != null) {
            sesion.invalidate();
        }
    }

    private int minutosDeSesion() {
        String texto = configuracionService.valor(configuracionService.obtenerMapa(), "expiracion_sesion_minutos", "30");
        try {
            return Math.max(1, Integer.parseInt(texto.trim()));
        } catch (NumberFormatException | NullPointerException ex) {
            return 30;
        }
    }
}
