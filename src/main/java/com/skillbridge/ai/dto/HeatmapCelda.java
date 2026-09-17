package com.skillbridge.ai.dto;

/** Una celda del mapa de carga (un colaborador × una semana). */
public class HeatmapCelda {

    private final int valor;
    private final String nivelClass;

    public HeatmapCelda(int valor) {
        this.valor = valor;
        if (valor > 100) this.nivelClass = "level-over";
        else if (valor >= 90) this.nivelClass = "level-high";
        else if (valor >= 60) this.nivelClass = "level-optimal";
        else this.nivelClass = "level-low";
    }

    public int getValor() {
        return valor;
    }

    public String getNivelClass() {
        return nivelClass;
    }
}
