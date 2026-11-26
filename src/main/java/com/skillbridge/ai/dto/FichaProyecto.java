package com.skillbridge.ai.dto;

import java.util.List;

/**
 * Ficha completa de un proyecto para los reportes del PM y del RM (vista,
 * Excel con una hoja por proyecto y PDF con una página por proyecto).
 * resumenIa es null si no se pidió o si la IA no respondió.
 */
public record FichaProyecto(Long id, String nombre, String descripcion, List<String> tecnologias,
                            String estado, String fechaInicio, String fechaFin, int avance,
                            String situacion, String riesgo, String projectManager, long vacantes,
                            List<Miembro> equipo, List<HabilidadCobertura> habilidades,
                            int entregablesTotal, int entregablesAbiertos, int entregablesVencidos,
                            List<Hito> proximos, List<String> alertas, String resumenIa) {

    public record Miembro(String nombre, String cargo, String rol, int dedicacion) {}

    /** Habilidad pedida por el proyecto y si alguien del equipo la cubre al nivel pedido. */
    public record HabilidadCobertura(String nombre, String nivelRequerido, boolean cubierta, String mejorNivel, String quien) {}

    public record Hito(String fecha, String titulo, String tipo) {}

    public FichaProyecto conResumenIa(String resumen) {
        return new FichaProyecto(id, nombre, descripcion, tecnologias, estado, fechaInicio, fechaFin, avance, situacion, riesgo,
                projectManager, vacantes, equipo, habilidades, entregablesTotal, entregablesAbiertos, entregablesVencidos,
                proximos, alertas, resumen);
    }

    public long habilidadesCubiertas() {
        return habilidades.stream().filter(HabilidadCobertura::cubierta).count();
    }

    public int cargaEquipo() {
        return equipo.stream().mapToInt(Miembro::dedicacion).sum();
    }
}
