package com.skillbridge.ai.service;

import com.skillbridge.ai.dto.NavItem;
import com.skillbridge.ai.dto.NotificacionFila;
import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.dto.UserSummary;
import com.skillbridge.ai.util.Roles;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Rellena en el Model los atributos que esperan fragments/sidebar.html y
 * fragments/topbar.html (roleLabel, navItems, user, activeHref, title,
 * subtitle, unreadCount, notifications), para no repetir este bloque en
 * cada controlador de pantalla.
 *
 * "notifications"/"unreadCount" ahora salen de NotificacionService (tabla
 * real "notificaciones"): se inyecta aca en vez de en cada controlador para
 * que el campanario del topbar funcione igual en TODAS las pantallas
 * (incluidas usuarios.html/habilidades.html, que no cambiaron su firma de
 * llamada a aplicar(...)) sin tocar esos controladores ya entregados.
 */
@Service
public class ShellModelBuilder {

    private final NavService navService;
    private final NotificacionService notificacionService;

    public ShellModelBuilder(NavService navService, NotificacionService notificacionService) {
        this.navService = navService;
        this.notificacionService = notificacionService;
    }

    public void aplicar(Model model, UsuarioSesion sesion, String activeHref, String title, String subtitle) {
        aplicar(model, sesion, activeHref, title, subtitle, Collections.emptyMap());
    }

    public void aplicar(Model model, UsuarioSesion sesion, String activeHref, String title, String subtitle,
                         Map<String, Integer> badgesPorHref) {
        String slug = Roles.slug(sesion.getRolEfectivo());
        List<NavItem> navItems = navService.itemsPara(slug);
        for (NavItem item : navItems) {
            Integer badge = badgesPorHref.get(item.getHref());
            if (badge != null) item.setBadge(badge);
        }

        model.addAttribute("roleLabel", Roles.etiqueta(sesion.getRolEfectivo()));
        model.addAttribute("navItems", navItems);
        model.addAttribute("activeHref", activeHref);
        model.addAttribute("user", new UserSummary(sesion.getIniciales(), sesion.getNombreCompleto(), sesion.getCorreo()));
        model.addAttribute("title", title);
        model.addAttribute("subtitle", subtitle);

        long noLeidas = 0;
        List<NotificacionFila> recientes = Collections.emptyList();
        if (sesion.getPerfilId() != null) {
            noLeidas = notificacionService.contarNoLeidas(sesion.getPerfilId());
            List<NotificacionFila> todas = notificacionService.listar(sesion.getPerfilId());
            recientes = todas.size() > 4 ? todas.subList(0, 4) : todas;
        }
        model.addAttribute("unreadCount", noLeidas);
        model.addAttribute("notifications", recientes);

        // El item de sidebar "notificaciones.html" (solo existe en el menú de
        // Colaborador, ver NavService) refleja el mismo contador que la
        // campanita del topbar, salvo que el controlador ya haya fijado un
        // badge explícito para ese href en badgesPorHref.
        if (noLeidas > 0) {
            for (NavItem item : navItems) {
                if ("notificaciones.html".equals(item.getHref()) && item.getBadge() == null) {
                    item.setBadge((int) Math.min(noLeidas, Integer.MAX_VALUE));
                }
            }
        }
    }
}
