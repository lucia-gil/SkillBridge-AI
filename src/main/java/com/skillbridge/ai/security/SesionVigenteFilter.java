package com.skillbridge.ai.security;

import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.model.Usuario;
import com.skillbridge.ai.repository.UsuarioRepository;
import com.skillbridge.ai.service.AuthService;
import com.skillbridge.ai.util.Roles;
import com.skillbridge.ai.util.SesionKeys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Filtro de la cadena de Spring Security (va antes de la autorización) que
 * mantiene la sesión al día con la BD en cada request:
 * <ul>
 *   <li>Si el Administrador suspendió o eliminó la cuenta, la sesión se
 *       cierra en ese mismo request (no espera a que expire).</li>
 *   <li>Si le cambiaron el rol, o empezó/dejó de liderar proyectos, se
 *       recalculan sus AUTORIDADES en el SecurityContext: así
 *       {@code hasAuthority('project_manager')} refleja el cambio sin tener
 *       que volver a iniciar sesión.</li>
 * </ul>
 * NO es un @Component a propósito: si lo fuera, Spring Boot también lo
 * registraría como filtro suelto del servlet y correría dos veces. Lo crea
 * SecurityConfig y lo agrega solo a la SecurityFilterChain.
 */
public class SesionVigenteFilter extends OncePerRequestFilter {

    private final UsuarioRepository usuarioRepository;
    private final AuthService authService;
    private final SesionSegura sesionSegura;

    private final com.skillbridge.ai.service.ConfiguracionService configuracion;
    private final com.skillbridge.ai.service.DobleFactorService dobleFactor;
    private final AccesoDenegadoHandler accesoDenegado;

    public SesionVigenteFilter(UsuarioRepository usuarioRepository, AuthService authService, SesionSegura sesionSegura,
                               com.skillbridge.ai.service.ConfiguracionService configuracion,
                               com.skillbridge.ai.service.DobleFactorService dobleFactor,
                               AccesoDenegadoHandler accesoDenegado) {
        this.configuracion = configuracion;
        this.dobleFactor = dobleFactor;
        this.accesoDenegado = accesoDenegado;
        this.usuarioRepository = usuarioRepository;
        this.authService = authService;
        this.sesionSegura = sesionSegura;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String ruta = request.getRequestURI().substring(request.getContextPath().length());
        return ruta.startsWith("/css/") || ruta.startsWith("/js/") || ruta.startsWith("/img/")
                || ruta.startsWith("/fonts/") || ruta.equals("/favicon.ico");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication actual = SecurityContextHolder.getContext().getAuthentication();
        boolean autenticado = actual != null && actual.isAuthenticated()
                && !(actual instanceof AnonymousAuthenticationToken);

        if (autenticado) {
            HttpSession sesion = request.getSession(false);
            UsuarioSesion usuario = sesion != null ? (UsuarioSesion) sesion.getAttribute(SesionKeys.USUARIO) : null;
            Optional<Usuario> enBd = usuario != null ? usuarioRepository.findById(usuario.getUsuarioId()) : Optional.empty();

            if (enBd.isEmpty() || !enBd.get().isActivo()) {
                // Cuenta suspendida/eliminada (o sesión inconsistente): fuera.
                // Al seguir sin autenticación, las rutas protegidas mandan al login.
                SesionSegura.cerrar(request);
            } else {
                refrescar(request, response, sesion, usuario, enBd.get(), actual);
                if (Roles.ADMINISTRADOR.equals(enBd.get().getRolOrganizacional())
                        && configuracion.activo("doble_factor_obligatorio_admin")) {
                    String ruta = request.getServletPath();
                    boolean activo = dobleFactor.estaActivo(usuario.getUsuarioId());
                    if ("/cuenta/2fa/desactivar".equals(ruta)) {
                        accesoDenegado.prohibir(request, response, "El doble factor es obligatorio para administradores.");
                        return;
                    }
                    if (!activo && !ruta.equals("/administrador/enrolamiento.html")
                            && !ruta.equals("/administrador/enrolamiento/iniciar")
                            && !ruta.equals("/administrador/enrolamiento/activar")) {
                        response.sendRedirect(request.getContextPath() + "/administrador/enrolamiento.html");
                        return;
                    }
                    if (activo && !Boolean.TRUE.equals(sesion.getAttribute(SesionKeys.SEGUNDO_FACTOR))) {
                        SesionSegura.cerrar(request);
                        response.sendRedirect(request.getContextPath() + "/auth/login.html");
                        return;
                    }
                }
            }
        }
        chain.doFilter(request, response);
    }

    private void refrescar(HttpServletRequest request, HttpServletResponse response, HttpSession sesion,
                           UsuarioSesion usuario, Usuario enBd, Authentication actual) {
        String rolOrg = enBd.getRolOrganizacional();
        boolean lidera = rolOrg == null && authService.lideraProyectos(usuario.getPerfilId());

        if (!Objects.equals(rolOrg, usuario.getRolOrganizacional()) || lidera != usuario.isLideraProyectos()) {
            usuario.setRolOrganizacional(rolOrg);
            usuario.setLideraProyectos(lidera);
            if (!vistaPermitida(usuario.getRolEfectivo(), rolOrg, lidera)) {
                usuario.setRolEfectivo(authService.calcularRolEfectivo(usuario.getPerfilId(), rolOrg));
            }
            sesion.setAttribute(SesionKeys.USUARIO, usuario);
        }

        List<GrantedAuthority> nuevas = SesionSegura.autoridades(rolOrg, lidera);
        Set<String> antes = new HashSet<>(AuthorityUtils.authorityListToSet(actual.getAuthorities()));
        Set<String> despues = new HashSet<>(AuthorityUtils.authorityListToSet(nuevas));
        if (!antes.equals(despues)) {
            sesionSegura.guardar(request, response, SesionSegura.autenticacion(usuario));
        }
    }

    /** Misma regla que SesionInterceptor.puedeEntrarA (modelo v7). */
    private static boolean vistaPermitida(String vista, String rolOrg, boolean lidera) {
        if (Roles.ADMINISTRADOR.equals(vista)) return Roles.ADMINISTRADOR.equals(rolOrg);
        if (Roles.RESOURCE_MANAGER.equals(vista)) return Roles.RESOURCE_MANAGER.equals(rolOrg);
        if (Roles.PROJECT_MANAGER.equals(vista)) return rolOrg == null && lidera;
        if (Roles.COLABORADOR.equals(vista)) return rolOrg == null;
        return false;
    }
}
