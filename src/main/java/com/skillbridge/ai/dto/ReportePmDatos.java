package com.skillbridge.ai.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Snapshot inmutable que alimenta por igual la pantalla, el PDF y el Excel. */
public class ReportePmDatos {

    private static final DateTimeFormatter GENERADO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final List<ReportePmFila> filas;
    private final Map<String, Long> porEstado;
    private final int avancePromedio;
    private final int asignacionesActivas;
    private final long atrasados;
    private final String proyectoFiltro;
    private final String estadoFiltro;
    private final LocalDate desde;
    private final LocalDate hasta;
    private final String generado;

    public ReportePmDatos(List<ReportePmFila> filas, Map<String, Long> porEstado,
                          String proyectoFiltro, String estadoFiltro,
                          LocalDate desde, LocalDate hasta) {
        this.filas = List.copyOf(filas);
        this.porEstado = Collections.unmodifiableMap(new LinkedHashMap<>(porEstado));
        this.avancePromedio = filas.isEmpty() ? 0
                : (int) Math.round(filas.stream().mapToInt(ReportePmFila::getAvance).average().orElse(0));
        this.asignacionesActivas = filas.stream().mapToInt(ReportePmFila::getEquipoActivo).sum();
        this.atrasados = filas.stream().filter(ReportePmFila::isAtrasado).count();
        this.proyectoFiltro = proyectoFiltro;
        this.estadoFiltro = estadoFiltro;
        this.desde = desde;
        this.hasta = hasta;
        this.generado = LocalDateTime.now().format(GENERADO);
    }

    public List<ReportePmFila> getFilas() { return filas; }
    public Map<String, Long> getPorEstado() { return porEstado; }
    public int getTotalProyectos() { return filas.size(); }
    public int getAvancePromedio() { return avancePromedio; }
    public int getAsignacionesActivas() { return asignacionesActivas; }
    public long getAtrasados() { return atrasados; }
    public String getProyectoFiltro() { return proyectoFiltro; }
    public String getEstadoFiltro() { return estadoFiltro; }
    public LocalDate getDesde() { return desde; }
    public LocalDate getHasta() { return hasta; }
    public String getGenerado() { return generado; }

    public String getFiltrosLabel() {
        String rango = desde == null && hasta == null ? "Todas las fechas"
                : (desde != null ? desde.toString() : "inicio") + " a " + (hasta != null ? hasta.toString() : "hoy");
        return "Proyecto: " + proyectoFiltro + " | Estado: " + estadoFiltro + " | Inicio: " + rango;
    }
}
