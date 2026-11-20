package com.skillbridge.ai.security;

import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.service.EmergenciaService;
import com.skillbridge.ai.util.SesionKeys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.Objects;

/** La versión en MySQL invalida incluso sesiones inactivas durante todo el cierre. */
public class PortalFilter extends OncePerRequestFilter {
    public static final String VERSION = "portal.version";
    public static final String SEGUNDO_FACTOR = "portal.segundoFactor";
    public static final String CONSOLA = "/administrador/seguridad.html";
    private final EmergenciaService emergencia;
    public PortalFilter(EmergenciaService emergencia) { this.emergencia = emergencia; }

    @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        var portal = emergencia.estado();
        HttpSession session = req.getSession(false);
        String ruta = req.getServletPath();
        boolean login = "/auth/login.html".equals(ruta);
        if (session != null && !Objects.equals(session.getAttribute(VERSION), portal.version())
                && (session.getAttribute(VERSION) != null || session.getAttribute(SesionKeys.USUARIO) != null
                    || session.getAttribute(SesionKeys.PENDIENTE_2FA) != null) && portal.version() != 0) {
            SesionSegura.cerrar(req);
            session = null;
        }
        if ((login || session != null) && (session == null || session.getAttribute(VERSION) == null)) {
            req.getSession().setAttribute(VERSION, portal.version());
        }
        req.setAttribute(VERSION, portal.version());
        if (portal.activo()) {
            UsuarioSesion usuario = session == null ? null : (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
            UsuarioSesion pendiente = session == null ? null : (UsuarioSesion) session.getAttribute(SesionKeys.PENDIENTE_2FA);
            boolean responsable = usuario != null && Objects.equals(portal.responsable(), usuario.getUsuarioId())
                    && Boolean.TRUE.equals(session.getAttribute(SEGUNDO_FACTOR));
            boolean permitido = login || "/auth/logout".equals(ruta)
                    || ("/auth/verificar-2fa.html".equals(ruta) && pendiente != null
                        && Objects.equals(portal.responsable(), pendiente.getUsuarioId()))
                    || (responsable && (CONSOLA.equals(ruta) || "/administrador/seguridad/restablecer".equals(ruta)));
            if (!permitido) {
                if (responsable && "GET".equals(req.getMethod()) && "/administrador/inicio.html".equals(ruta)) {
                    res.sendRedirect(req.getContextPath() + CONSOLA);
                    return;
                }
                res.setStatus(503);
                res.setContentType("text/html;charset=UTF-8");
                res.getWriter().print("<html lang='es'><title>Mantenimiento</title><h1>Portal en mantenimiento</h1><a href='"
                        + req.getContextPath() + "/auth/login.html'>Acceso del responsable</a></html>");
                return;
            }
        }
        chain.doFilter(req, res);
    }
}
