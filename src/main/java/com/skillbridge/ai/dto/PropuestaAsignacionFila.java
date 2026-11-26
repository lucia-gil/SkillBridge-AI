package com.skillbridge.ai.dto;

import java.util.List;

/**
 * Propuesta de asignación para la bandeja del Resource Manager.
 * Los campos desde proyectoId solo se completan en las pendientes
 * (en el historial van en null/vacío): match de hoy, habilidades y la
 * mejor alternativa disponible, para decidir con contexto.
 */
public record PropuestaAsignacionFila(Long id, String proyecto, String candidato, String candidatoIniciales,
                                      String candidatoCargo, String solicitante, int dedicacion, int scoreTotal,
                                      int scoreHabilidades, int scoreExperiencia, int scoreDisponibilidad,
                                      int cargaActual, int cargaProyectada, boolean cargaCompatible,
                                      String estado, String fecha, String motivo, String origen,
                                      Long proyectoId, Long candidatoPerfilId, long vacantes, Integer scoreActual,
                                      List<MatchingHabilidad> habilidades, Alternativa alternativa) {

    /** Otra persona libre con mejor match para el mismo proyecto. */
    public record Alternativa(Long perfilId, String nombre, String cargo, int score, int scoreHabilidades, int cargaProyectada) {}

    /** Etiqueta legible del origen para la bandeja del Resource Manager. */
    public String origenLabel() {
        if ("manual".equals(origen)) return "Manual";
        if ("reincorporacion".equals(origen)) return "Reincorporación";
        return "Talent Matching";
    }

    /** Habilidades del proyecto que el candidato cumple al nivel pedido. */
    public long habilidadesCumplidas() {
        return habilidades == null ? 0 : habilidades.stream().filter(h -> h.nivelCandidato() >= h.nivelRequerido()).count();
    }

    /** true si el match cambió desde que el PM lo propuso (carga o habilidades nuevas). */
    public boolean scoreCambio() {
        return scoreActual != null && Math.abs(scoreActual - scoreTotal) >= 3;
    }
}
