package com.skillbridge.ai.interceptor;

import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.service.AuthService;
import com.skillbridge.ai.util.Roles;
import com.skillbridge.ai.util.SesionKeys;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Desde la migración a Spring Security, QUIÉN puede entrar a cada área lo
 * decide config/SecurityConfig (hasAuthority) y la revalidación contra la BD
 * la hace security/SesionVigenteFilter. Este interceptor queda como segunda
 * capa y para la navegación: fija rolEfectivo (la vista activa, "Ver como")
 * según el área visitada y redirige al inicio correcto.
 *
 * Historia (antes de Spring Security), adaptado de AuthFilter de
 * QuintaOla-SGA:
 *  - exige sesion activa para /administrador/**, /resource-manager/**,
 *    /project-manager/** y /colaborador/**,
 *  - revalida contra la BD en cada request protegido: si el usuario fue
 *    desactivado mientras tenia la sesion abierta, se cierra su sesion de
 *    inmediato (igual que la referencia),
 *  - si su rol cambio, se refresca en la sesion en caliente en vez de
 *    forzar un logout (la autorizacion de ESTE request igual se decide con
 *    los datos recien leidos de la BD, que es lo que importa),
 *  - modelo v7: el acceso es por capacidad (ver puedeEntrarA). Un Miembro
 *    que lidera proyectos puede usar /project-manager/** Y /colaborador/**;
 *    rolEfectivo pasa a ser la vista del area que esta visitando,
 *  - si entra a una seccion que no le corresponde, se le redirige a su
 *    inicio por defecto (no a login).
 *
 * "/cuenta/**" y "/notificaciones/**" son un caso aparte: son un único
 * controlador (CuentaController, NotificacionesController) que sirven a
 * los 4 roles por igual, así que solo exigen sesion activa - sin el
 * redireccionamiento por rol especifico que si aplica a "/administrador/**"
 * y compania.
 */
@Component
public class SesionInterceptor implements HandlerInterceptor {

    private final AuthService authService;

    public SesionInterceptor(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate");
        response.setHeader("Pragma", "no-cache");

        String ruta = request.getRequestURI().substring(request.getContextPath().length());
        boolean rutaCompartidaEntreRoles = ruta.startsWith("/cuenta/") || ruta.startsWith("/notificaciones/")
                || ruta.startsWith("/certificados/")
                // Adjuntos del foro (ForoAdjuntoController): los ven PM y Colaborador;
                // ForoService.obtenerAdjunto verifica que sea miembro del proyecto.
                || ruta.startsWith("/foros/");
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

        // La revalidación contra la BD (cuenta suspendida, rol o liderazgo
        // cambiados) ya la hizo security/SesionVigenteFilter en la cadena de
        // Spring Security antes de llegar aquí, y dejó rolOrganizacional y
        // lideraProyectos al día en el UsuarioSesion de la sesión.
        String rolOrg = sesion.getRolOrganizacional();
        boolean lidera = sesion.isLideraProyectos();

        // Rutas compartidas (/cuenta, /notificaciones...): se conserva la
        // vista en la que venía navegando, salvo que ya no le corresponda
        // (p.ej. dejó de liderar proyectos o le cambiaron el rol).
        if (rutaCompartidaEntreRoles) {
            if (!puedeEntrarA(sesion.getRolEfectivo(), rolOrg, lidera)) {
                sesion.setRolEfectivo(authService.calcularRolEfectivo(sesion.getPerfilId(), rolOrg));
            }
            session.setAttribute(SesionKeys.USUARIO, sesion);
            return true;
        }

        // Modelo v7: el acceso se decide por CAPACIDAD, no por un único rol.
        //  - /administrador/**    -> rol_organizacional = administrador
        //  - /resource-manager/** -> rol_organizacional = resource_manager
        //  - /project-manager/**  -> Miembro que lidera al menos un proyecto
        //  - /colaborador/**      -> cualquier Miembro (aunque también lidere)
        // La vista activa (rolEfectivo) es la del área que está visitando: así
        // un Miembro como "Paco" ve sus proyectos de PM en una vista y sus
        // proyectos de colaborador en la otra, con los permisos de cada una.
        if (!puedeEntrarA(rolRequerido, rolOrg, lidera)) {
            // Antes redirigia en silencio al inicio propio del usuario; el
            // equipo prefirio que quede explicito que el acceso fue negado
            // (403) en vez de un salto silencioso a otra pantalla.
            response.sendError(HttpServletResponse.SC_FORBIDDEN,
                    "No tienes permiso para acceder a esta seccion.");
            return false;
        }
        sesion.setRolEfectivo(rolRequerido);
        session.setAttribute(SesionKeys.USUARIO, sesion);
        return true;
    }

    /** ¿Puede alguien con este rol organizacional (y liderazgo) usar la vista indicada? */
    static boolean puedeEntrarA(String vista, String rolOrganizacional, boolean lideraProyectos) {
        if (vista == null) return false;
        if (Roles.ADMINISTRADOR.equals(vista)) return Roles.ADMINISTRADOR.equals(rolOrganizacional);
        if (Roles.RESOURCE_MANAGER.equals(vista)) return Roles.RESOURCE_MANAGER.equals(rolOrganizacional);
        if (Roles.PROJECT_MANAGER.equals(vista)) return rolOrganizacional == null && lideraProyectos;
        if (Roles.COLABORADOR.equals(vista)) return rolOrganizacional == null;
        return false;
    }

    private String rolRequeridoPara(String ruta) {
        if (ruta.startsWith("/administrador/")) return Roles.ADMINISTRADOR;
        if (ruta.startsWith("/resource-manager/")) return Roles.RESOURCE_MANAGER;
        if (ruta.startsWith("/project-manager/")) return Roles.PROJECT_MANAGER;
        if (ruta.startsWith("/colaborador/")) return Roles.COLABORADOR;
        return null;
    }
}
