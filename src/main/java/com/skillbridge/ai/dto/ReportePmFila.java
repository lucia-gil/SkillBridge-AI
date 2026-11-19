package com.skillbridge.ai.dto;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/** Una fila compartida por la vista, el PDF y el Excel de reportes del PM. */
public class ReportePmFila {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final Long proyectoId;
    private final String proyecto;
    private final String estadoCrudo;
    private final String estado;
    private final String estadoBadgeClass;
    private final LocalDate fechaInicio;
    private final LocalDate fechaFin;
    private final int avance;
    private final int equipoActivo;
    private final int cargaAsignada;
    private final String situacion;
    private final String situacionBadgeClass;
    private final String riesgo;
    private final String riesgoBadgeClass;

    public ReportePmFila(Long proyectoId, String proyecto, String estadoCrudo,
                         LocalDate fechaInicio, LocalDate fechaFin, int avance,
                         int equipoActivo, int cargaAsignada) {
        this.proyectoId = proyectoId;
        this.proyecto = proyecto;
        this.estadoCrudo = estadoCrudo;
        this.estado = EstadoProyectoUtil.label(estadoCrudo);
        this.estadoBadgeClass = EstadoProyectoUtil.badgeClass(estadoCrudo);
        this.fechaInicio = fechaInicio;
        this.fechaFin = fechaFin;
        this.avance = avance;
        this.equipoActivo = equipoActivo;
        this.cargaAsignada = cargaAsignada;

        boolean atrasado = fechaFin != null && fechaFin.isBefore(LocalDate.now())
                && !"completado".equals(estadoCrudo) && !"cancelado".equals(estadoCrudo);
        if (atrasado) {
            this.situacion = "Atrasado";
            this.situacionBadgeClass = "badge-red";
        } else if ("completado".equals(estadoCrudo)) {
            this.situacion = "Finalizado";
            this.situacionBadgeClass = "badge-blue";
        } else if ("cancelado".equals(estadoCrudo)) {
            this.situacion = "Cancelado";
            this.situacionBadgeClass = "badge-neutral";
        } else if ("en_pausa".equals(estadoCrudo)) {
            this.situacion = "Requiere atención";
            this.situacionBadgeClass = "badge-amber";
        } else {
            this.situacion = "En plazo";
            this.situacionBadgeClass = "badge-green";
        }

        // Riesgo (RF nuevo: segunda dona de "Resumen"): deriva del mismo
        // cálculo de situación, sin pedir datos nuevos. Finalizado/Cancelado
        // quedan fuera del análisis de riesgo (ya no aplica).
        if ("Atrasado".equals(this.situacion)) {
            this.riesgo = "Alto";
            this.riesgoBadgeClass = "badge-red";
        } else if ("Requiere atención".equals(this.situacion)) {
            this.riesgo = "Medio";
            this.riesgoBadgeClass = "badge-amber";
        } else if ("En plazo".equals(this.situacion)) {
            this.riesgo = "Bajo";
            this.riesgoBadgeClass = "badge-green";
        } else {
            this.riesgo = "";
            this.riesgoBadgeClass = "badge-neutral";
        }
    }

    public Long getProyectoId() { return proyectoId; }
    public String getProyecto() { return proyecto; }
    public String getEstadoCrudo() { return estadoCrudo; }
    public String getEstado() { return estado; }
    public String getEstadoBadgeClass() { return estadoBadgeClass; }
    public LocalDate getFechaInicio() { return fechaInicio; }
    public LocalDate getFechaFin() { return fechaFin; }
    public String getFechaInicioLabel() { return fechaInicio != null ? fechaInicio.format(FECHA) : "—"; }
    public String getFechaFinLabel() { return fechaFin != null ? fechaFin.format(FECHA) : "—"; }
    public int getAvance() { return avance; }
    public int getEquipoActivo() { return equipoActivo; }
    public int getCargaAsignada() { return cargaAsignada; }
    public String getSituacion() { return situacion; }
    public String getSituacionBadgeClass() { return situacionBadgeClass; }
    public boolean isAtrasado() { return "Atrasado".equals(situacion); }
    public String getRiesgo() { return riesgo; }
    public String getRiesgoBadgeClass() { return riesgoBadgeClass; }
    public boolean isEnRiesgo() { return "Alto".equals(riesgo) || "Medio".equals(riesgo); }
}
