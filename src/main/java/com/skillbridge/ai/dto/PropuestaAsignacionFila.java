package com.skillbridge.ai.dto;

public record PropuestaAsignacionFila(Long id, String proyecto, String candidato, String candidatoIniciales,
                                      String candidatoCargo, String solicitante, int dedicacion, int scoreTotal,
                                      int scoreHabilidades, int scoreExperiencia, int scoreDisponibilidad,
                                      int cargaActual, int cargaProyectada, boolean cargaCompatible,
                                      String estado, String fecha, String motivo, String origen) {

    /** Etiqueta legible del origen para la bandeja del Resource Manager. */
    public String origenLabel() {
        if ("manual".equals(origen)) return "Manual";
        if ("reincorporacion".equals(origen)) return "Reincorporación";
        return "Talent Matching";
    }
}
