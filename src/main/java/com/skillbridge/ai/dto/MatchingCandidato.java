package com.skillbridge.ai.dto;

import java.util.List;

public class MatchingCandidato {
    private final Long perfilId;
    private final String nombre;
    private final String iniciales;
    private final String cargo;
    private final int scoreTotal;
    private final int scoreHabilidades;
    private final int scoreExperiencia;
    private final int scoreDisponibilidad;
    private final int experienciaAnios;
    private final int cargaActual;
    private final int disponibilidad;
    private final int cargaProyectada;
    private final boolean cargaCompatible;
    private final List<MatchingHabilidad> habilidades;

    public MatchingCandidato(Long perfilId, String nombre, String iniciales, String cargo, int scoreTotal,
                             int scoreHabilidades, int scoreExperiencia, int scoreDisponibilidad,
                             int experienciaAnios, int cargaActual, int disponibilidad, int cargaProyectada,
                             boolean cargaCompatible, List<MatchingHabilidad> habilidades) {
        this.perfilId = perfilId; this.nombre = nombre; this.iniciales = iniciales; this.cargo = cargo;
        this.scoreTotal = scoreTotal; this.scoreHabilidades = scoreHabilidades;
        this.scoreExperiencia = scoreExperiencia; this.scoreDisponibilidad = scoreDisponibilidad;
        this.experienciaAnios = experienciaAnios; this.cargaActual = cargaActual; this.disponibilidad = disponibilidad;
        this.cargaProyectada = cargaProyectada; this.cargaCompatible = cargaCompatible; this.habilidades = habilidades;
    }
    public Long getPerfilId() { return perfilId; }
    public String getNombre() { return nombre; }
    public String getIniciales() { return iniciales; }
    public String getCargo() { return cargo; }
    public int getScoreTotal() { return scoreTotal; }
    public int getScoreHabilidades() { return scoreHabilidades; }
    public int getScoreExperiencia() { return scoreExperiencia; }
    public int getScoreDisponibilidad() { return scoreDisponibilidad; }
    public int getExperienciaAnios() { return experienciaAnios; }
    public int getCargaActual() { return cargaActual; }
    public int getDisponibilidad() { return disponibilidad; }
    public int getCargaProyectada() { return cargaProyectada; }
    public boolean isCargaCompatible() { return cargaCompatible; }
    public List<MatchingHabilidad> getHabilidades() { return habilidades; }
    public String getExplicacion() {
        return "Coincidencia técnica de " + scoreHabilidades + "%, " + experienciaAnios +
                " año(s) de experiencia y " + disponibilidad + "% disponible. Su carga pasaría de " +
                cargaActual + "% a " + cargaProyectada + "%.";
    }
}
