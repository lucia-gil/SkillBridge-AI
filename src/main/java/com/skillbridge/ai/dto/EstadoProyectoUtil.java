package com.skillbridge.ai.dto;

/** Traduce el ENUM crudo de proyectos.estado a etiqueta + clase de badge, en un solo lugar. */
public final class EstadoProyectoUtil {

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
}
