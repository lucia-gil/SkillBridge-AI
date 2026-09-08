package com.skillbridge.ai.service;

import com.skillbridge.ai.dto.NavItem;
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
 * "notifications"/"unreadCount" quedan vacios/0 en esta entrega: el modulo
 * de notificaciones (RF09) no forma parte de los 2 CRUD ni de login/registro
 * pedidos, asi que no se inventan datos - el topbar simplemente no muestra
 * ninguna notificacion real todavia.
 */
@Service
public class ShellModelBuilder {

    private final NavService navService;

    public ShellModelBuilder(NavService navService) {
        this.navService = navService;
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
        model.addAttribute("unreadCount", 0);
        model.addAttribute("notifications", Collections.emptyList());
    }
}
