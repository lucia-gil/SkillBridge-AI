package com.skillbridge.ai.dto;

/** Confirmación pendiente que muestra el Asistente IA (GET /api/asistente/confirmaciones). */
public record AgenteConfirmacionVista(String id, String herramienta, String argumentos, java.time.Instant expira) {
}
