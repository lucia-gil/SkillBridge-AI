package com.skillbridge.ai.dto;

/** Una fila de "Proyectos por estado" (administrador/inicio.html y reportes.html). */
public class EstadoProyectoConConteo {

    private final String crudo;
    private final String label;
    private final String badgeClass;
    private final long conteo;

    public EstadoProyectoConConteo(String crudo, long conteo) {
        this.crudo = crudo;
        this.label = EstadoProyectoUtil.label(crudo);
        this.badgeClass = EstadoProyectoUtil.badgeClass(crudo);
        this.conteo = conteo;
    }

    public String getCrudo() {
        return crudo;
    }

    public String getLabel() {
        return label;
    }

    public String getBadgeClass() {
        return badgeClass;
    }

    public long getConteo() {
        return conteo;
    }
}
