package com.skillbridge.ai.dto;

import java.util.List;

/** Detalle completo de un proyecto (modal "Ver detalle" / "Asignar colaborador"). */
public class ProyectoDetalle {

    private final Long id;
    private final String nombre;
    private final String descripcion;
    private final List<String> tecnologias;
    private final String estadoCrudo;
    private final String estadoLabel;
    private final String estadoBadgeClass;
    private final String fechaInicioLabel;
    private final String fechaFinLabel;
    private final String fechaInicioIso;
    private final String fechaFinIso;
    private final int avance;
    private final int colaboradoresRequeridos;
    private final String pmNombre;
    private final List<MiembroEquipoFila> equipo;

    public ProyectoDetalle(Long id, String nombre, String descripcion, List<String> tecnologias, String estadoCrudo,
                            String fechaInicioLabel, String fechaFinLabel, String fechaInicioIso, String fechaFinIso,
                            int avance, int colaboradoresRequeridos, String pmNombre, List<MiembroEquipoFila> equipo) {
        this.id = id;
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.tecnologias = tecnologias;
        this.estadoCrudo = estadoCrudo;
        this.estadoLabel = EstadoProyectoUtil.label(estadoCrudo);
        this.estadoBadgeClass = EstadoProyectoUtil.badgeClass(estadoCrudo);
        this.fechaInicioLabel = fechaInicioLabel;
        this.fechaFinLabel = fechaFinLabel;
        this.fechaInicioIso = fechaInicioIso;
        this.fechaFinIso = fechaFinIso;
        this.avance = avance;
        this.colaboradoresRequeridos = colaboradoresRequeridos;
        this.pmNombre = pmNombre != null ? pmNombre : "Sin asignar";
        this.equipo = equipo;
    }

    public Long getId() {
        return id;
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

    public String getFechaInicioLabel() {
        return fechaInicioLabel;
    }

    public String getFechaFinLabel() {
        return fechaFinLabel;
    }

    public String getFechaInicioIso() {
        return fechaInicioIso;
    }

    public String getFechaFinIso() {
        return fechaFinIso;
    }

    public int getAvance() {
        return avance;
    }

    public int getColaboradoresRequeridos() {
        return colaboradoresRequeridos;
    }

    public String getPmNombre() {
        return pmNombre;
    }

    public List<MiembroEquipoFila> getEquipo() {
        return equipo;
    }

    /** Tecnologias unidas por coma, para precargar el input del modal "Editar" (evitar T() en la plantilla, que es sintaxis SpEL y no OGNL). */
    public String getTecnologiasCsv() {
        return String.join(",", tecnologias);
    }

    /** El PM forma parte del equipo, pero no consume una vacante de colaborador. */
    public long getColaboradoresAsignados() {
        return equipo.stream()
                .filter(m -> "colaborador".equals(m.getRolEnProyectoCrudo()))
                .count();
    }

    public long getVacantes() {
        return Math.max(0, colaboradoresRequeridos - getColaboradoresAsignados());
    }

    public boolean isCupoCompleto() {
        return getColaboradoresAsignados() >= colaboradoresRequeridos;
    }

    public int getCoberturaPorcentaje() {
        if (colaboradoresRequeridos <= 0) return 100;
        return (int) Math.min(100, Math.round(getColaboradoresAsignados() * 100.0 / colaboradoresRequeridos));
    }
}
