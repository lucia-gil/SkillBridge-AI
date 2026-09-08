package com.skillbridge.ai.dto;

/** "user" que esperan fragments/sidebar.html y fragments/topbar.html. */
public record UserSummary(String iniciales, String nombre, String correo) {
}
