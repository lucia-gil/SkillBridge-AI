package com.skillbridge.ai.dto;

/**
 * Persona que puede recibir una asignación, con su carga activa actual.
 * Alimenta la lista con checkboxes de "Asignar colaboradores" (asignación
 * en masa) en las vistas de Asignaciones del PM y del Resource Manager:
 * RF04 pide mostrar disponibilidad y carga ANTES de asignar.
 */
public record PerfilAsignable(Long perfilId, String nombre, String correo, String cargo,
                              int cargaActual, int proyectosActivos) {
}
