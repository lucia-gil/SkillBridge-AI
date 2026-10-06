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

    // Modelo v7 (fase 2): datos de pausa / cierre y equipo anterior (para
    // "Reincorporar equipo anterior" al reanudar o reabrir). Se llenan con
    // setters desde ProyectoService.obtenerDetalle.
    private String fechaPausaLabel;
    private String motivoPausa;
    private String fechaReanudacionLabel;
    private String fechaReanudacionIso;
    private boolean reanudacionVencida;
    private String razonCancelacion;
    private List<IntegranteAnterior> equipoAnterior = List.of();
    private List<HistorialEquipoFila> historial = List.of();

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

    public String getFechaPausaLabel() { return fechaPausaLabel; }
    public void setFechaPausaLabel(String v) { this.fechaPausaLabel = v; }
    public String getMotivoPausa() { return motivoPausa; }
    public void setMotivoPausa(String v) { this.motivoPausa = v; }
    public String getFechaReanudacionLabel() { return fechaReanudacionLabel; }
    public void setFechaReanudacionLabel(String v) { this.fechaReanudacionLabel = v; }
    public String getFechaReanudacionIso() { return fechaReanudacionIso; }
    public void setFechaReanudacionIso(String v) { this.fechaReanudacionIso = v; }
    /** true si ya pasó la fecha estimada de reanudación y el proyecto sigue en pausa. */
    public boolean isReanudacionVencida() { return reanudacionVencida; }
    public void setReanudacionVencida(boolean v) { this.reanudacionVencida = v; }
    public String getRazonCancelacion() { return razonCancelacion; }
    public void setRazonCancelacion(String v) { this.razonCancelacion = v; }
    public List<IntegranteAnterior> getEquipoAnterior() { return equipoAnterior; }
    public void setEquipoAnterior(List<IntegranteAnterior> v) { this.equipoAnterior = v != null ? v : List.of(); }
    /** Proyecto completado o cancelado: solo lectura salvo "Reabrir". */
    public boolean isCerrado() { return "completado".equals(estadoCrudo) || "cancelado".equals(estadoCrudo); }
    public List<HistorialEquipoFila> getHistorial() { return historial; }
    public void setHistorial(List<HistorialEquipoFila> v) { this.historial = v != null ? v : List.of(); }
    /** "PM 1 · Colaboradores 3/4" para el encabezado de la pestaña Equipo. */
    public long getPmsActivos() { return equipo.stream().filter(m -> "project_manager".equals(m.getRolEnProyectoCrudo())).count(); }
}
