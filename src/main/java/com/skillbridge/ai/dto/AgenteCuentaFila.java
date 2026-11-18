package com.skillbridge.ai.dto;

/** Cuenta de usuario sin datos sensibles, tal como la recibe el agente del Administrador. */
public record AgenteCuentaFila(Long id, String nombreCompleto, String rolOrganizacional, String estado) {
}
