package com.skillbridge.ai.dto;

/** Operación del agente que ve el Administrador (sin prompts ni argumentos). */
public record AgenteAuditoriaFila(Long usuarioId, String herramienta, String resultado, java.time.LocalDateTime fecha) {
}
