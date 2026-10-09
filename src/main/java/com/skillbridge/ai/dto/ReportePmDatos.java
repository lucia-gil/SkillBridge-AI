package com.skillbridge.ai.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Snapshot inmutable que alimenta por igual la pantalla, el PDF y el Excel. */
public class ReportePmDatos {

    private static final DateTimeFormatter GENERADO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final List<ReportePmFila> filas;
    private final Map<String, Long> porEstado;
    private final List<Segmento> segmentos;
    private final String conicGradient;
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

        // Gráfica circular "Resumen por estado" (donut con conic-gradient): se
        // calcula una sola vez aquí, con los mismos conteos que ya alimentan
        // la tabla, el Excel y el PDF, para que nunca se desincronice.
        List<Segmento> segmentosCalc = new ArrayList<>();
        StringBuilder gradiente = new StringBuilder();
        int total = this.filas.size();
        double acumulado = 0;
        for (String codigo : EstadoProyectoUtil.ORDEN) {
            long cantidad = this.filas.stream().filter(f -> codigo.equals(f.getEstadoCrudo())).count();
            if (cantidad == 0) continue;
            double porcentaje = total == 0 ? 0 : (cantidad * 100.0 / total);
            String color = EstadoProyectoUtil.chartColor(codigo);
            segmentosCalc.add(new Segmento(EstadoProyectoUtil.label(codigo), cantidad, porcentaje, color));
            double inicio = acumulado;
            acumulado += porcentaje;
            if (gradiente.length() > 0) gradiente.append(", ");
            gradiente.append(color).append(' ')
                    .append(String.format(Locale.US, "%.2f", inicio)).append("% ")
                    .append(String.format(Locale.US, "%.2f", acumulado)).append("%");
        }
        this.segmentos = List.copyOf(segmentosCalc);
        this.conicGradient = total == 0 ? "var(--surface-sunken) 0% 100%" : gradiente.toString();
    }

    public List<ReportePmFila> getFilas() { return filas; }
    public Map<String, Long> getPorEstado() { return porEstado; }
    public List<Segmento> getSegmentos() { return segmentos; }
    public String getConicGradient() { return conicGradient; }
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

    /** Una porción de la gráfica circular / fila de la leyenda de "Resumen por estado". */
    public static class Segmento {
        private final String label;
        private final long cantidad;
        private final double porcentaje;
        private final String color;

        public Segmento(String label, long cantidad, double porcentaje, String color) {
            this.label = label;
            this.cantidad = cantidad;
            this.porcentaje = porcentaje;
            this.color = color;
        }

        public String getLabel() { return label; }
        public long getCantidad() { return cantidad; }
        public int getPorcentajeRedondeado() { return (int) Math.round(porcentaje); }
        public String getColor() { return color; }
    }
}
