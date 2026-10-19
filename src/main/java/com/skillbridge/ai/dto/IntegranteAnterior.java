package com.skillbridge.ai.dto;

/**
 * Colaborador que estaba en el equipo cuando el proyecto se pausó o se
 * cerró (modelo v7, fase 2). Alimenta la lista "Reincorporar equipo
 * anterior" al reanudar o reabrir: cada persona marcada genera una
 * propuesta de reincorporación que aprueba el Resource Manager.
 *
 * @param cargaAnterior dedicación que tenía en este proyecto (se propone la misma)
 * @param cargaActual   su carga efectiva HOY en otros proyectos
 * @param conCapacidad  si puede volver con cargaAnterior sin superar el límite
 * @param motivo        por qué no puede volver (null si puede)
 */
public record IntegranteAnterior(Long perfilId, String nombre, String cargo, int cargaAnterior,
                                 int cargaActual, boolean conCapacidad, String motivo) {
}
