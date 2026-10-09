package com.skillbridge.ai.dto;

import java.util.List;

/** Traduce el ENUM crudo de proyectos.estado a etiqueta + clase de badge, en un solo lugar. */
public final class EstadoProyectoUtil {

    /** Orden fijo en el que se agrupan los estados para el resumen y la gráfica circular. */
    public static final List<String> ORDEN = List.of("planificacion", "activo", "en_pausa", "completado", "cancelado");

    private EstadoProyectoUtil() {
    }

    public static String label(String estadoCrudo) {
        if (estadoCrudo == null) return "—";
        return switch (estadoCrudo) {
            case "planificacion" -> "Planificación";
            case "activo" -> "Activo";
            case "en_pausa" -> "En pausa";
            case "completado" -> "Completado";
            case "cancelado" -> "Cancelado";
            default -> estadoCrudo;
        };
    }

    public static String badgeClass(String estadoCrudo) {
        if (estadoCrudo == null) return "badge-neutral";
        return switch (estadoCrudo) {
            case "activo" -> "badge-green";
            case "en_pausa" -> "badge-amber";
            case "planificacion" -> "badge-neutral";
            case "cancelado" -> "badge-red";
            case "completado" -> "badge-blue";
            default -> "badge-neutral";
        };
    }

    /** Color sólido (no el tenue de los badges) usado en la gráfica circular y la de barras. */
    public static String chartColor(String estadoCrudo) {
        if (estadoCrudo == null) return "var(--navy-700)";
        return switch (estadoCrudo) {
            case "activo" -> "var(--green-fg)";
            case "en_pausa" -> "var(--amber-fg)";
            case "planificacion" -> "var(--navy-700)";
            case "cancelado" -> "var(--red-fg)";
            case "completado" -> "var(--blue-fg)";
            default -> "var(--navy-700)";
        };
    }
}
