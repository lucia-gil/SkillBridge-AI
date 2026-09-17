package com.skillbridge.ai.dto;

import java.util.List;

/** Fila del "Mapa de carga por colaborador" (resource-manager/inicio.html). */
public class HeatmapColaboradorFila {

    private final String nombre;
    private final String iniciales;
    private final String cargo;
    private final List<HeatmapCelda> valores;
    private final int promedio;

    public HeatmapColaboradorFila(String nombre, String cargo, List<Integer> valoresCrudos) {
        this.nombre = nombre;
        this.iniciales = InicialesUtil.de(nombre);
        this.cargo = cargo != null ? cargo : "—";
        this.valores = valoresCrudos.stream().map(HeatmapCelda::new).collect(java.util.stream.Collectors.toList());
        this.promedio = valoresCrudos.isEmpty() ? 0
                : (int) Math.round(valoresCrudos.stream().mapToInt(Integer::intValue).average().orElse(0));
    }

    public String getNombre() {
        return nombre;
    }

    public String getIniciales() {
        return iniciales;
    }

    public String getCargo() {
        return cargo;
    }

    public List<HeatmapCelda> getValores() {
        return valores;
    }

    public int getPromedio() {
        return promedio;
    }
}
