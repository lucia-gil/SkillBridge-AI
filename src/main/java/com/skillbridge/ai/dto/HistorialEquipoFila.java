package com.skillbridge.ai.dto;

/**
 * Una asignación pasada de un proyecto (pestaña "Historial" del detalle,
 * modelo v7 fase 5): quién estuvo, con qué rol, cuánto y en qué periodo.
 */
public record HistorialEquipoFila(String nombre, String iniciales, String rolLabel, int cargaPorcentaje,
                                  String periodo, String estado) {

    public String estadoLabel() {
        return "cancelada".equals(estado) ? "Cancelada" : "Finalizada";
    }

    public String estadoBadgeClass() {
        return "cancelada".equals(estado) ? "badge-red" : "badge-neutral";
    }
}
