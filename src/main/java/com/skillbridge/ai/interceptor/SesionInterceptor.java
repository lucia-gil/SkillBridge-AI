package com.skillbridge.ai.interceptor;

import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.model.Usuario;
import com.skillbridge.ai.repository.UsuarioRepository;
import com.skillbridge.ai.service.AuthService;
import com.skillbridge.ai.util.Roles;
import com.skillbridge.ai.util.SesionKeys;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Optional;

/**
 * Control de acceso por sesion + rol, adaptado de AuthFilter de
 * QuintaOla-SGA:
 *  - exige sesion activa para /administrador/**, /resource-manager/**,
 *    /project-manager/** y /colaborador/**,
 *  - revalida contra la BD en cada request protegido: si el usuario fue
 *    desactivado mientras tenia la sesion abierta, se cierra su sesion de
 *    inmediato (igual que la referencia),
 *  - si su rol cambio, se refresca el rol en la sesion en caliente en vez
 *    de forzar un logout (mejor experiencia; la autorizacion de ESTE
 *    request igual se decide con el rol recien calculado, que es lo que
 *    importa para la seguridad),
 *  - si esta logueado pero entra a una seccion de un rol que no es el
 *    suyo, se le redirige a su propio inicio (no a login).
 *
 * "/cuenta/**" y "/notificaciones/**" son un caso aparte: son un único
 * controlador (CuentaController, NotificacionesController) que sirven a
 * los 4 roles por igual, así que solo exigen sesion activa - sin el
 * redireccionamiento por rol especifico que si aplica a "/administrador/**"
 * y compania.
 */
@Component
public class SesionInterceptor implements HandlerInterceptor {

    private final UsuarioRepository usuarioRepository;
    private final AuthService authService;

    public SesionInterceptor(UsuarioRepository usuarioRepository, AuthService authService) {
        this.usuarioRepository = usuarioRepository;
        this.authService = authService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
        response.setHeader("Pragma", "no-cache");

        String ruta = request.getRequestURI().substring(request.getContextPath().length());
        boolean rutaCompartidaEntreRoles = ruta.startsWith("/cuenta/") || ruta.startsWith("/notificaciones/");
        String rolRequerido = rolRequeridoPara(ruta);
        if (rolRequerido == null && !rutaCompartidaEntreRoles) {
            return true; // ruta no protegida
        }

        HttpSession session = request.getSession(false);
        UsuarioSesion sesion = session != null ? (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO) : null;

        if (sesion == null) {
            response.sendRedirect(request.getContextPath() + "/auth/login.html");
            return false;
        }

        Optional<Usuario> oActual = usuarioRepository.findById(sesion.getUsuarioId());
        if (oActual.isEmpty() || !oActual.get().isActivo()) {
            session.invalidate();
            response.sendRedirect(request.getContextPath() + "/auth/login.html");
            return false;
        }

        Usuario actual = oActual.get();
        String rolEfectivoActual = authService.calcularRolEfectivo(sesion.getPerfilId(), actual.getRolOrganizacional());
        if (!rolEfectivoActual.equals(sesion.getRolEfectivo())) {
            sesion.setRolEfectivo(rolEfectivoActual);
            sesion.setRolOrganizacional(actual.getRolOrganizacional());
            session.setAttribute(SesionKeys.USUARIO, sesion);
        }

        if (!rutaCompartidaEntreRoles && !rolRequerido.equals(rolEfectivoActual)) {
            response.sendRedirect(request.getContextPath() + "/" + Roles.slug(rolEfectivoActual) + "/inicio.html");
            return false;
        }

        return true;
    }

    private String rolRequeridoPara(String ruta) {
        if (ruta.startsWith("/administrador/")) return Roles.ADMINISTRADOR;
        if (ruta.startsWith("/resource-manager/")) return Roles.RESOURCE_MANAGER;
        if (ruta.startsWith("/project-manager/")) return Roles.PROJECT_MANAGER;
        if (ruta.startsWith("/colaborador/")) return Roles.COLABORADOR;
        return null;
    }
}
