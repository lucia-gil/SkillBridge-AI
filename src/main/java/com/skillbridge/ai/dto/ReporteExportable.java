package com.skillbridge.ai.dto;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Descripción neutra de un reporte para exportarlo a Excel o PDF con
 * {@code ReporteExportService}, sin que el exportador conozca el dominio.
 * La usan los reportes de Administrador, Resource Manager y Colaborador
 * (el de Project Manager conserva su exportador propio, ReportePmExportService).
 *
 * <p>Uso típico:</p>
 * <pre>
 * ReporteExportable r = new ReporteExportable("Reporte de ocupación", "Sin filtros")
 *         .kpi("Ocupación promedio", "72%");
 * r.tabla("Ocupación por colaborador", new String[]{"Colaborador", "Ocupación"}, new float[]{3, 1})
 *         .fila("Ana Pérez", ReporteExportable.porcentaje(85));
 * </pre>
 */
public class ReporteExportable {

    /** Valor que el exportador muestra como porcentaje (85 → "85%" / formato 0% en Excel). */
    public record Porcentaje(int valor) {
    }

    public record Kpi(String etiqueta, String valor) {
    }

    /** Una tabla del reporte: una hoja en Excel, un bloque en el PDF. */
    public static class Tabla {
        private final String titulo;
        private final List<String> columnas;
        private final float[] anchos;
        private final List<List<Object>> filas = new ArrayList<>();

        Tabla(String titulo, String[] columnas, float[] anchos) {
            this.titulo = titulo;
            this.columnas = Arrays.asList(columnas);
            this.anchos = anchos;
        }

        /** Agrega una fila; String, Number o {@link Porcentaje} por celda. */
        public Tabla fila(Object... celdas) {
            filas.add(Arrays.asList(celdas));
            return this;
        }

        public String getTitulo() {
            return titulo;
        }

        public List<String> getColumnas() {
            return columnas;
        }

        public float[] getAnchos() {
            return anchos;
        }

        public List<List<Object>> getFilas() {
            return filas;
        }
    }

    private final String titulo;
    private final String filtrosLabel;
    private final List<Kpi> kpis = new ArrayList<>();
    private final List<Tabla> tablas = new ArrayList<>();

    public ReporteExportable(String titulo, String filtrosLabel) {
        this.titulo = titulo;
        this.filtrosLabel = filtrosLabel;
    }

    public static Porcentaje porcentaje(int valor) {
        return new Porcentaje(valor);
    }

    public ReporteExportable kpi(String etiqueta, Object valor) {
        kpis.add(new Kpi(etiqueta, String.valueOf(valor)));
        return this;
    }

    /** Crea y devuelve una tabla nueva para llenarla con {@link Tabla#fila}. */
    public Tabla tabla(String titulo, String[] columnas, float[] anchos) {
        if (anchos.length != columnas.length) {
            throw new IllegalArgumentException("Cada columna necesita su ancho relativo.");
        }
        Tabla t = new Tabla(titulo, columnas, anchos);
        tablas.add(t);
        return t;
    }

    public String getTitulo() {
        return titulo;
    }

    public String getFiltrosLabel() {
        return filtrosLabel;
    }

    public List<Kpi> getKpis() {
        return kpis;
    }

    public List<Tabla> getTablas() {
        return tablas;
    }
}
