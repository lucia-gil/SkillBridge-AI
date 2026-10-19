package com.skillbridge.ai.service;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.skillbridge.ai.dto.ReportePmDatos;
import com.skillbridge.ai.dto.ReportePmFila;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.awt.Color;
import java.util.Map;

@Service
public class ReportePmExportService {

    private static final String[] COLUMNAS = {
            "Proyecto", "Estado", "Inicio", "Entrega estimada", "Avance estimado",
            "Equipo activo", "Carga asignada", "Situación"
    };

    public void escribirExcel(ReportePmDatos datos, OutputStream salida) throws IOException {
        try (XSSFWorkbook libro = new XSSFWorkbook()) {
            Sheet hoja = libro.createSheet("Reporte");
            hoja.setDisplayGridlines(false);
            hoja.createFreezePane(0, 12);

            CellStyle titulo = estiloTitulo(libro);
            CellStyle encabezado = estiloEncabezado(libro);
            CellStyle etiqueta = estiloEtiqueta(libro);
            CellStyle porcentaje = libro.createCellStyle();
            porcentaje.setDataFormat(libro.createDataFormat().getFormat("0%"));

            Row r0 = hoja.createRow(0);
            Cell c0 = r0.createCell(0);
            c0.setCellValue("SkillBridge AI · Reporte de proyectos");
            c0.setCellStyle(titulo);
            hoja.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, 7));
            hoja.createRow(1).createCell(0).setCellValue("Generado: " + datos.getGenerado());
            hoja.createRow(2).createCell(0).setCellValue(datos.getFiltrosLabel());

            String[] kpiNombres = {"Proyectos", "Avance promedio", "Asignaciones activas", "Atrasados"};
            double[] kpiValores = {datos.getTotalProyectos(), datos.getAvancePromedio() / 100.0,
                    datos.getAsignacionesActivas(), datos.getAtrasados()};
            Row kpiLabels = hoja.createRow(4);
            Row kpiValues = hoja.createRow(5);
            for (int i = 0; i < kpiNombres.length; i++) {
                Cell label = kpiLabels.createCell(i * 2);
                label.setCellValue(kpiNombres[i]);
                label.setCellStyle(etiqueta);
                Cell valor = kpiValues.createCell(i * 2);
                valor.setCellValue(kpiValores[i]);
                if (i == 1) valor.setCellStyle(porcentaje);
            }

            Row estadoTitulo = hoja.createRow(7);
            estadoTitulo.createCell(0).setCellValue("Resumen por estado");
            estadoTitulo.getCell(0).setCellStyle(etiqueta);
            Row estados = hoja.createRow(8);
            int columnaEstado = 0;
            for (Map.Entry<String, Long> entry : datos.getPorEstado().entrySet()) {
                estados.createCell(columnaEstado).setCellValue(entry.getKey());
                estados.createCell(columnaEstado + 1).setCellValue(entry.getValue());
                columnaEstado += 2;
            }

            Row cabecera = hoja.createRow(11);
            for (int i = 0; i < COLUMNAS.length; i++) {
                Cell celda = cabecera.createCell(i);
                celda.setCellValue(COLUMNAS[i]);
                celda.setCellStyle(encabezado);
            }

            int filaN = 12;
            for (ReportePmFila fila : datos.getFilas()) {
                Row row = hoja.createRow(filaN++);
                row.createCell(0).setCellValue(fila.getProyecto());
                row.createCell(1).setCellValue(fila.getEstado());
                row.createCell(2).setCellValue(fila.getFechaInicioLabel());
                row.createCell(3).setCellValue(fila.getFechaFinLabel());
                Cell avance = row.createCell(4);
                avance.setCellValue(fila.getAvance() / 100.0);
                avance.setCellStyle(porcentaje);
                row.createCell(5).setCellValue(fila.getEquipoActivo());
                Cell carga = row.createCell(6);
                carga.setCellValue(fila.getCargaAsignada() / 100.0);
                carga.setCellStyle(porcentaje);
                row.createCell(7).setCellValue(fila.getSituacion());
            }
            if (datos.getFilas().isEmpty()) hoja.createRow(filaN).createCell(0).setCellValue("Sin resultados para estos filtros");

            int[] anchos = {34, 18, 16, 20, 19, 17, 18, 20};
            for (int i = 0; i < anchos.length; i++) hoja.setColumnWidth(i, anchos[i] * 256);
            hoja.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(11, Math.max(11, filaN - 1), 0, 7));
            libro.write(salida);
        }
    }

    public void escribirPdf(ReportePmDatos datos, OutputStream salida) throws IOException {
        Document documento = new Document(PageSize.A4.rotate(), 28, 28, 28, 28);
        try {
            PdfWriter.getInstance(documento, salida);
            documento.open();

        Font titulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, new Color(24, 44, 78));
        Font subtitulo = FontFactory.getFont(FontFactory.HELVETICA, 9, new Color(100, 112, 133));
        documento.add(new Paragraph("SkillBridge AI · Reporte de proyectos", titulo));
        documento.add(new Paragraph("Generado: " + datos.getGenerado(), subtitulo));
        documento.add(new Paragraph(datos.getFiltrosLabel(), subtitulo));
        documento.add(new Paragraph(" "));

        PdfPTable kpis = new PdfPTable(4);
        kpis.setWidthPercentage(100);
        agregarKpi(kpis, "PROYECTOS", String.valueOf(datos.getTotalProyectos()));
        agregarKpi(kpis, "AVANCE PROMEDIO", datos.getAvancePromedio() + "%");
        agregarKpi(kpis, "ASIGNACIONES ACTIVAS", String.valueOf(datos.getAsignacionesActivas()));
        agregarKpi(kpis, "ATRASADOS", String.valueOf(datos.getAtrasados()));
        documento.add(kpis);
        documento.add(new Paragraph(" "));

        Font seccion = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, new Color(24, 44, 78));
        documento.add(new Paragraph("Resumen por estado", seccion));
        String resumen = datos.getPorEstado().isEmpty() ? "Sin resultados"
                : datos.getPorEstado().entrySet().stream()
                .map(e -> e.getKey() + ": " + e.getValue()).reduce((a, b) -> a + "   |   " + b).orElse("");
        documento.add(new Paragraph(resumen, subtitulo));
        documento.add(new Paragraph(" "));

        PdfPTable tabla = new PdfPTable(new float[]{2.4f, 1.2f, 1.0f, 1.1f, 1.1f, 1.0f, 1.1f, 1.2f});
        tabla.setWidthPercentage(100);
        tabla.setHeaderRows(1);
        for (String columna : COLUMNAS) agregarCabecera(tabla, columna);
        for (ReportePmFila fila : datos.getFilas()) {
            agregarCelda(tabla, fila.getProyecto());
            agregarCelda(tabla, fila.getEstado());
            agregarCelda(tabla, fila.getFechaInicioLabel());
            agregarCelda(tabla, fila.getFechaFinLabel());
            agregarCelda(tabla, fila.getAvance() + "%");
            agregarCelda(tabla, String.valueOf(fila.getEquipoActivo()));
            agregarCelda(tabla, fila.getCargaAsignada() + "%");
            agregarCelda(tabla, fila.getSituacion());
        }
        if (datos.getFilas().isEmpty()) {
            PdfPCell vacia = new PdfPCell(new Phrase("Sin resultados para estos filtros", subtitulo));
            vacia.setColspan(8);
            vacia.setPadding(10);
            tabla.addCell(vacia);
        }
            documento.add(tabla);
        } catch (DocumentException ex) {
            throw new IOException("No se pudo generar el reporte PDF.", ex);
        } finally {
            if (documento.isOpen()) documento.close();
        }
    }

    private CellStyle estiloTitulo(XSSFWorkbook libro) {
        CellStyle estilo = libro.createCellStyle();
        org.apache.poi.ss.usermodel.Font fuente = libro.createFont();
        fuente.setBold(true);
        fuente.setFontHeightInPoints((short) 18);
        fuente.setColor(IndexedColors.DARK_BLUE.getIndex());
        estilo.setFont(fuente);
        return estilo;
    }

    private CellStyle estiloEncabezado(XSSFWorkbook libro) {
        CellStyle estilo = libro.createCellStyle();
        org.apache.poi.ss.usermodel.Font fuente = libro.createFont();
        fuente.setBold(true);
        fuente.setColor(IndexedColors.WHITE.getIndex());
        estilo.setFont(fuente);
        estilo.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        estilo.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        estilo.setAlignment(HorizontalAlignment.CENTER);
        estilo.setBorderBottom(BorderStyle.THIN);
        return estilo;
    }

    private CellStyle estiloEtiqueta(XSSFWorkbook libro) {
        CellStyle estilo = libro.createCellStyle();
        org.apache.poi.ss.usermodel.Font fuente = libro.createFont();
        fuente.setBold(true);
        estilo.setFont(fuente);
        estilo.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
        estilo.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return estilo;
    }

    private void agregarKpi(PdfPTable tabla, String etiqueta, String valor) {
        Font label = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, new Color(100, 112, 133));
        Font value = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 17, new Color(24, 44, 78));
        PdfPCell celda = new PdfPCell();
        celda.setPadding(10);
        celda.setBorderColor(new Color(220, 225, 233));
        celda.addElement(new Paragraph(etiqueta, label));
        celda.addElement(new Paragraph(valor, value));
        tabla.addCell(celda);
    }

    private void agregarCabecera(PdfPTable tabla, String texto) {
        Font font = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.WHITE);
        PdfPCell celda = new PdfPCell(new Phrase(texto, font));
        celda.setBackgroundColor(new Color(24, 44, 78));
        celda.setHorizontalAlignment(Element.ALIGN_CENTER);
        celda.setPadding(7);
        tabla.addCell(celda);
    }

    private void agregarCelda(PdfPTable tabla, String texto) {
        Font font = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.DARK_GRAY);
        PdfPCell celda = new PdfPCell(new Phrase(texto, font));
        celda.setPadding(6);
        celda.setBorderColor(new Color(225, 229, 235));
        tabla.addCell(celda);
    }
}
