package com.skillbridge.ai.dto;

import java.util.List;

/** Fila de administrador/proyectos.html, ya lista para pintar. */
public class ProyectoFila {

    private final Long id;
    private final String nombre;
    private final String estadoCrudo;
    private final String estadoLabel;
    private final String estadoBadgeClass;
    private final String pmNombre;
    private final long equipoSize;
    private final String fechaInicioLabel;
    private final String fechaFinLabel;
    private final int avance;
    private final List<String> tecnologias;

    public ProyectoFila(Long id, String nombre, String estadoCrudo, String pmNombre, long equipoSize,
                         String fechaInicioLabel, String fechaFinLabel, int avance, List<String> tecnologias) {
        this.id = id;
        this.nombre = nombre;
        this.estadoCrudo = estadoCrudo;
        this.estadoLabel = EstadoProyectoUtil.label(estadoCrudo);
        this.estadoBadgeClass = EstadoProyectoUtil.badgeClass(estadoCrudo);
        this.pmNombre = pmNombre != null ? pmNombre : "Sin asignar";
        this.equipoSize = equipoSize;
        this.fechaInicioLabel = fechaInicioLabel;
        this.fechaFinLabel = fechaFinLabel;
        this.avance = avance;
        this.tecnologias = tecnologias;
    }

    public Long getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
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

    public String getPmNombre() {
        return pmNombre;
    }

    public long getEquipoSize() {
        return equipoSize;
    }

    public String getFechaInicioLabel() {
        return fechaInicioLabel;
    }

    public String getFechaFinLabel() {
        return fechaFinLabel;
    }

    public int getAvance() {
        return avance;
    }

    public List<String> getTecnologias() {
        return tecnologias;
    }
}
