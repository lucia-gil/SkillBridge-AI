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
}
