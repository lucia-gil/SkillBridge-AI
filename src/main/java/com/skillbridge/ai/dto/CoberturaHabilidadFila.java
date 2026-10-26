package com.skillbridge.ai.dto;

import java.util.List;

/**
 * Oferta contra demanda de una habilidad (Reportes del Resource Manager).
 *
 * @param habilidadId     id de la habilidad
 * @param nombre          nombre de la habilidad
 * @param categoriaId     id de su categoría (para el color en la vista)
 * @param categoria       nombre de su categoría
 * @param proyectos       proyectos vigentes que la piden
 * @param nivelRequerido  nivel más alto que pide alguno de esos proyectos (1-5)
 * @param calificadas     miembros que la declaran con ese nivel o más
 * @param disponibles     de las calificadas, cuántas tienen capacidad libre (carga menor al límite)
 * @param proyectosNombres nombres de los proyectos que la piden
 */
public record CoberturaHabilidadFila(Long habilidadId, String nombre, Long categoriaId, String categoria,
                                     int proyectos, int nivelRequerido, int calificadas, int disponibles,
                                     List<String> proyectosNombres) {

    /** "Sin cobertura": nadie la tiene al nivel pedido. */
    public static final String SIN_COBERTURA = "Sin cobertura";
    /** "Ajustada": hay gente calificada, pero menos personas libres que proyectos que la piden. */
    public static final String AJUSTADA = "Ajustada";
    /** "Cubierta": al menos una persona libre por proyecto que la pide. */
    public static final String CUBIERTA = "Cubierta";

    public String estado() {
        if (calificadas == 0) return SIN_COBERTURA;
        if (disponibles < proyectos) return AJUSTADA;
        return CUBIERTA;
    }

    public String estadoBadgeClass() {
        return switch (estado()) {
            case SIN_COBERTURA -> "badge-red";
            case AJUSTADA -> "badge-amber";
            default -> "badge-green";
        };
    }

    /** true si no está cubierta (sin cobertura o ajustada). */
    public boolean conBrecha() {
        return !CUBIERTA.equals(estado());
    }

    public String nivelLabel() {
        return switch (nivelRequerido) {
            case 1, 2 -> "Básico";
            case 3 -> "Intermedio";
            case 4 -> "Avanzado";
            default -> nivelRequerido >= 5 ? "Experto" : "—";
        };
    }

    /** Orden para el reporte: primero lo más crítico (0 = sin cobertura). */
    public int severidad() {
        return switch (estado()) {
            case SIN_COBERTURA -> 0;
            case AJUSTADA -> 1;
            default -> 2;
        };
    }
}
