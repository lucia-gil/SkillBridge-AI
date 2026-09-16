package com.skillbridge.ai.service;

import com.skillbridge.ai.dto.NavItem;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Puerto a Java de NAV_CONFIG (static/js/state.js): los items de sidebar
 * por rol. Se mantienen las mismas etiquetas/iconos/hrefs relativos que ya
 * usaba la demo estatica -> la navegacion entre paginas sigue funcionando
 * igual, ahora servida por Spring MVC en vez de archivos estaticos (ver
 * SitioController, que mapea cada uno de estos hrefs 1:1 a una plantilla).
 */
@Service
public class NavService {

    public List<NavItem> itemsPara(String rolSlug) {
        List<NavItem> items = new ArrayList<>();
        switch (rolSlug) {
            case "administrador" -> {
                items.add(new NavItem("Inicio", "icon-home", "inicio.html"));
                items.add(new NavItem("Usuarios y roles", "icon-users", "usuarios.html"));
                items.add(new NavItem("Catálogo de habilidades", "icon-book", "habilidades.html"));
                items.add(new NavItem("Proyectos", "icon-folder", "proyectos.html"));
                items.add(new NavItem("Reportes globales", "icon-bar-chart", "reportes.html"));
                items.add(new NavItem("Auditoría", "icon-shield", "auditoria.html"));
                items.add(new NavItem("Configuración", "icon-sliders", "configuracion.html"));
            }
            case "resource-manager" -> {
                items.add(new NavItem("Inicio", "icon-home", "inicio.html"));
                items.add(new NavItem("Ocupación del equipo", "icon-layers", "ocupacion.html"));
                items.add(new NavItem("Asignaciones", "icon-target", "asignaciones.html"));
                items.add(new NavItem("Colaboradores", "icon-users", "colaboradores.html"));
                items.add(new NavItem("AI Talent Matching", "icon-search-check", "ai-talent-matching.html"));
                items.add(new NavItem("Reportes", "icon-bar-chart", "reportes.html"));
            }
            case "project-manager" -> {
                items.add(new NavItem("Inicio", "icon-home", "inicio.html"));
                items.add(new NavItem("Proyectos", "icon-folder", "proyectos.html"));
                items.add(new NavItem("Asignaciones", "icon-target", "asignaciones.html"));
                items.add(new NavItem("Calendario", "icon-calendar", "calendario.html"));
                items.add(new NavItem("AI Talent Matching", "icon-search-check", "ai-talent-matching.html"));
                items.add(new NavItem("Foros", "icon-message", "foros.html"));
                items.add(new NavItem("Asistente IA", "icon-sparkles", "asistente-ia.html"));
                items.add(new NavItem("Reportes", "icon-bar-chart", "reportes.html"));
            }
            default -> { // colaborador
                items.add(new NavItem("Inicio", "icon-home", "inicio.html"));
                items.add(new NavItem("Mi perfil", "icon-user", "perfil.html"));
                items.add(new NavItem("Mis proyectos", "icon-folder", "proyectos.html"));
                items.add(new NavItem("Calendario", "icon-calendar", "calendario.html"));
                items.add(new NavItem("Foros", "icon-message", "foros.html"));
                items.add(new NavItem("Asistente IA", "icon-sparkles", "asistente-ia.html"));
                items.add(new NavItem("Notificaciones", "icon-bell", "notificaciones.html"));
            }
        }
        return items;
    }
}
