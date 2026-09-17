package com.skillbridge.ai.dto;

import java.util.List;

/** Fila de "Mis proyectos" (Colaborador): inicio.html, perfil.html, proyectos.html. */
public class MiProyectoFila {

    private final Long asignacionId;
    private final Long proyectoId;
    private final String nombre;
    private final String descripcion;
    private final List<String> tecnologias;
    private final String estadoCrudo;
    private final String estadoLabel;
    private final String estadoBadgeClass;
    private final String rolEnProyectoCrudo;
    private final String rolLabel;
    private final int cargaPorcentaje;
    private final int avance;
    private final String periodoLabel;
    private final String asignacionEstadoCrudo;

    public MiProyectoFila(Long asignacionId, Long proyectoId, String nombre, String descripcion, List<String> tecnologias,
                           String estadoCrudo, String rolEnProyectoCrudo, int cargaPorcentaje, int avance,
                           String periodoLabel, String asignacionEstadoCrudo) {
        this.asignacionId = asignacionId;
        this.proyectoId = proyectoId;
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.tecnologias = tecnologias;
        this.estadoCrudo = estadoCrudo;
        this.estadoLabel = EstadoProyectoUtil.label(estadoCrudo);
        this.estadoBadgeClass = EstadoProyectoUtil.badgeClass(estadoCrudo);
        this.rolEnProyectoCrudo = rolEnProyectoCrudo;
        this.rolLabel = "project_manager".equals(rolEnProyectoCrudo) ? "Project Manager" : "Colaborador";
        this.cargaPorcentaje = cargaPorcentaje;
        this.avance = avance;
        this.periodoLabel = periodoLabel;
        this.asignacionEstadoCrudo = asignacionEstadoCrudo;
    }

    public Long getAsignacionId() {
        return asignacionId;
    }

    public Long getProyectoId() {
        return proyectoId;
    }

    public String getNombre() {
        return nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public List<String> getTecnologias() {
        return tecnologias;
    }

    public String getEstadoCrudo() {
        return estadoCrudo;
    }

    public String getEstadoLabel() {
        return estadoLabel;
    }

    public String getEstadoBadgeClass() {
        return estadoBadgeClass;
    }

    public String getRolEnProyectoCrudo() {
        return rolEnProyectoCrudo;
    }

    public String getRolLabel() {
        return rolLabel;
    }

    public int getCargaPorcentaje() {
        return cargaPorcentaje;
    }

    public int getAvance() {
        return avance;
    }

    public String getPeriodoLabel() {
        return periodoLabel;
    }

    public String getAsignacionEstadoCrudo() {
        return asignacionEstadoCrudo;
    }
}
