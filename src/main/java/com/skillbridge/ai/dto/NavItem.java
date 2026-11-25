package com.skillbridge.ai.dto;

/** Item de navegacion del sidebar (ver fragments/sidebar.html: item.href/icon/label/badge). */
public class NavItem {

    private final String label;
    private final String icon;
    private final String href;
    private Integer badge;

    public NavItem(String label, String icon, String href) {
        this.label = label;
        this.icon = icon;
        this.href = href;
    }

    public String getLabel() {
        return label;
    }

    public String getIcon() {
        return icon;
    }

    public String getHref() {
        return href;
    }

    public Integer getBadge() {
        return badge;
    }

    public void setBadge(Integer badge) {
        this.badge = badge;
    }

    /** true = empieza un grupo nuevo: el sidebar dibuja una línea separadora antes. */
    private boolean nuevoGrupo;
    /** true = va fijo al pie del sidebar (Notificaciones, Configuración). */
    private boolean alPie;

    public boolean isNuevoGrupo() { return nuevoGrupo; }
    public boolean isAlPie() { return alPie; }

    public NavItem nuevoGrupo() { this.nuevoGrupo = true; return this; }
    public NavItem alPie() { this.alPie = true; return this; }
}
