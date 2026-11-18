package com.skillbridge.ai.dto;

/** Propuesta de asignación pendiente, tal como la recibe el agente del RM. */
public record AgenteSolicitudFila(Long id, Long proyectoId, Long candidatoPerfilId, Integer dedicacionPorcentaje, String estado) {
}
