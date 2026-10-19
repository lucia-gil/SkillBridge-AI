package com.skillbridge.ai.dto;

/**
 * Resumen liviano del usuario que esperan fragments/sidebar.html
 * y fragments/topbar.html.
 *
 * No contiene el BLOB de la fotografia. Solo indica si existe una foto
 * para que Thymeleaf pueda decidir entre mostrarla o usar las iniciales.
 */
public record UserSummary(
        String iniciales,
        String nombre,
        String correo,
        boolean tieneFoto
) {
}