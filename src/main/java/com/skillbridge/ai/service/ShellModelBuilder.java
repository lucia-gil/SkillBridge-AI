package com.skillbridge.ai.service;

import com.skillbridge.ai.dto.NavItem;
import com.skillbridge.ai.dto.NotificacionFila;
import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.dto.UserSummary;
import com.skillbridge.ai.repository.UsuarioRepository;
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
 * "notifications"/"unreadCount" salen de NotificacionService.
 */
@Service
public class ShellModelBuilder {

    private final NavService navService;
    private final NotificacionService notificacionService;
    private final UsuarioRepository usuarioRepository;

    public ShellModelBuilder(
            NavService navService,
            NotificacionService notificacionService,
            UsuarioRepository usuarioRepository) {

        this.navService = navService;
        this.notificacionService = notificacionService;
        this.usuarioRepository = usuarioRepository;
    }

    public void aplicar(
            Model model,
            UsuarioSesion sesion,
            String activeHref,
            String title,
            String subtitle) {

        aplicar(
                model,
                sesion,
                activeHref,
                title,
                subtitle,
                Collections.emptyMap()
        );
    }

    public void aplicar(
            Model model,
            UsuarioSesion sesion,
            String activeHref,
            String title,
            String subtitle,
            Map<String, Integer> badgesPorHref) {

        String slug = Roles.slug(sesion.getRolEfectivo());

        List<NavItem> navItems = navService.itemsPara(slug);

        for (NavItem item : navItems) {
            Integer badge = badgesPorHref.get(item.getHref());

            if (badge != null) {
                item.setBadge(badge);
            }
        }

        /*
         * El BLOB no se coloca en el Model.
         * Solo consultamos si el usuario tiene una foto.
         */
        boolean tieneFoto = sesion.getUsuarioId() != null
                && usuarioRepository.existsByIdAndFotoPerfilIsNotNull(
                sesion.getUsuarioId()
        );

        UserSummary user = new UserSummary(
                sesion.getIniciales(),
                sesion.getNombreCompleto(),
                sesion.getCorreo(),
                tieneFoto
        );

        model.addAttribute("roleLabel", Roles.etiqueta(sesion.getRolEfectivo()));
        model.addAttribute("navItems", navItems);
        model.addAttribute("activeHref", activeHref);
        model.addAttribute("user", user);
        model.addAttribute("title", title);
        model.addAttribute("subtitle", subtitle);

        long noLeidas = 0;
        List<NotificacionFila> recientes = Collections.emptyList();

        if (sesion.getPerfilId() != null) {
            noLeidas = notificacionService.contarNoLeidas(
                    sesion.getPerfilId()
            );

            List<NotificacionFila> todas =
                    notificacionService.listar(sesion.getPerfilId());

            recientes = todas.size() > 4
                    ? todas.subList(0, 4)
                    : todas;
        }

        model.addAttribute("unreadCount", noLeidas);
        model.addAttribute("notifications", recientes);

        /*
         * El item de sidebar "notificaciones.html" refleja el mismo
         * contador que la campanita del topbar.
         */
        if (noLeidas > 0) {
            for (NavItem item : navItems) {
                if ("notificaciones.html".equals(item.getHref())
                        && item.getBadge() == null) {

                    item.setBadge(
                            (int) Math.min(
                                    noLeidas,
                                    Integer.MAX_VALUE
                            )
                    );
                }
            }
        }
    }
}