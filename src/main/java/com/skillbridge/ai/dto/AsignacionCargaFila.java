package com.skillbridge.ai.dto;

/**
 * Una asignación activa de un colaborador con su dedicación. Alimenta el
 * modal "Rebalancear carga" de Ocupación (RM): ahí se puede bajar la
 * dedicación o finalizar la asignación sin salir de la pantalla.
 */
public record AsignacionCargaFila(Long asignacionId, String proyecto, String rolLabel,
                                  boolean esProjectManager, int carga) {
}
