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
import com.skillbridge.ai.dto.ReporteExportable;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Exporta cualquier {@link ReporteExportable} a Excel (.xlsx, Apache POI) o
 * PDF (OpenPDF), con el mismo estilo visual que el reporte de Project Manager.
 * Excel: hoja "Resumen" (título, filtros, KPIs) + una hoja por tabla, con
 * encabezado fijo y autofiltro. PDF: A4 horizontal con KPIs y cada tabla.
 */
@Service
public class ReporteExportService {

    private static final ZoneId ZONA = ZoneId.of("America/Lima");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final Color AZUL = new Color(24, 44, 78);
    private static final Color GRIS = new Color(100, 112, 133);

    /** Nombre de archivo sugerido: base + fecha de hoy, ej. "skillbridge-reporte-global-2026-10-26". */
    public String nombreArchivo(String base) {
        return base + "-" + LocalDateTime.now(ZONA).toLocalDate();
    }

    /**
     * Escribe el reporte como libro de Excel.
     *
     * @param reporte contenido a exportar
     * @param salida  stream de la respuesta HTTP; no se cierra aquí
     * @throws IOException si falla la escritura
     */
    public void escribirExcel(ReporteExportable reporte, OutputStream salida) throws IOException {
        try (XSSFWorkbook libro = new XSSFWorkbook()) {
            CellStyle titulo = estiloTitulo(libro);
            CellStyle encabezado = estiloEncabezado(libro);
            CellStyle etiqueta = estiloEtiqueta(libro);
            CellStyle porcentaje = libro.createCellStyle();
            porcentaje.setDataFormat(libro.createDataFormat().getFormat("0%"));

            Sheet resumen = libro.createSheet("Resumen");
            resumen.setDisplayGridlines(false);
            Cell c0 = resumen.createRow(0).createCell(0);
            c0.setCellValue("SkillBridge AI · " + reporte.getTitulo());
            c0.setCellStyle(titulo);
            resumen.createRow(1).createCell(0).setCellValue("Generado: " + LocalDateTime.now(ZONA).format(FECHA_HORA));
            resumen.createRow(2).createCell(0).setCellValue(reporte.getFiltrosLabel());
            int r = 4;
            for (ReporteExportable.Kpi kpi : reporte.getKpis()) {
                Row row = resumen.createRow(r++);
                Cell l = row.createCell(0);
                l.setCellValue(kpi.etiqueta());
                l.setCellStyle(etiqueta);
                row.createCell(1).setCellValue(kpi.valor());
            }
            resumen.setColumnWidth(0, 34 * 256);
            resumen.setColumnWidth(1, 18 * 256);

            Set<String> usados = new HashSet<>(Set.of("Resumen"));
            for (ReporteExportable.Tabla tabla : reporte.getTablas()) {
                Sheet hoja = libro.createSheet(nombreHoja(tabla.getTitulo(), usados));
                hoja.createFreezePane(0, 1);
                List<String> columnas = tabla.getColumnas();
                Row cab = hoja.createRow(0);
                for (int i = 0; i < columnas.size(); i++) {
                    Cell celda = cab.createCell(i);
                    celda.setCellValue(columnas.get(i));
                    celda.setCellStyle(encabezado);
                    hoja.setColumnWidth(i, Math.max(12, Math.round(tabla.getAnchos()[i] * 12)) * 256);
                }
                int f = 1;
                for (List<Object> fila : tabla.getFilas()) {
                    Row row = hoja.createRow(f++);
                    for (int i = 0; i < fila.size(); i++) {
                        Cell celda = row.createCell(i);
                        Object v = fila.get(i);
                        if (v instanceof ReporteExportable.Porcentaje p) {
                            celda.setCellValue(p.valor() / 100.0);
                            celda.setCellStyle(porcentaje);
                        } else if (v instanceof Number n) {
                            celda.setCellValue(n.doubleValue());
                        } else {
                            celda.setCellValue(v == null ? "" : String.valueOf(v));
                        }
                    }
                }
                if (tabla.getFilas().isEmpty()) {
                    hoja.createRow(1).createCell(0).setCellValue("Sin resultados para estos filtros");
                } else {
                    hoja.setAutoFilter(new CellRangeAddress(0, f - 1, 0, columnas.size() - 1));
                }
            }
            libro.write(salida);
        }
    }

    /**
     * Escribe el reporte como PDF A4 horizontal.
     *
     * @param reporte contenido a exportar
     * @param salida  stream de la respuesta HTTP
     * @throws IOException si OpenPDF no puede generar el documento
     */
    public void escribirPdf(ReporteExportable reporte, OutputStream salida) throws IOException {
        Document documento = new Document(PageSize.A4.rotate(), 28, 28, 28, 28);
        try {
            PdfWriter.getInstance(documento, salida);
            documento.open();

            Font titulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, AZUL);
            Font subtitulo = FontFactory.getFont(FontFactory.HELVETICA, 9, GRIS);
            Font seccion = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, AZUL);
            documento.add(new Paragraph("SkillBridge AI · " + reporte.getTitulo(), titulo));
            documento.add(new Paragraph("Generado: " + LocalDateTime.now(ZONA).format(FECHA_HORA), subtitulo));
            documento.add(new Paragraph(reporte.getFiltrosLabel(), subtitulo));
            documento.add(new Paragraph(" "));

            if (!reporte.getKpis().isEmpty()) {
                PdfPTable kpis = new PdfPTable(Math.min(4, reporte.getKpis().size()));
                kpis.setWidthPercentage(100);
                for (ReporteExportable.Kpi kpi : reporte.getKpis()) agregarKpi(kpis, kpi.etiqueta(), kpi.valor());
                kpis.completeRow();
                documento.add(kpis);
                documento.add(new Paragraph(" "));
            }

            for (ReporteExportable.Tabla tabla : reporte.getTablas()) {
                documento.add(new Paragraph(tabla.getTitulo(), seccion));
                documento.add(new Paragraph(" "));
                PdfPTable t = new PdfPTable(tabla.getAnchos());
                t.setWidthPercentage(100);
                t.setHeaderRows(1);
                for (String columna : tabla.getColumnas()) agregarCabecera(t, columna);
                for (List<Object> fila : tabla.getFilas()) {
                    for (Object v : fila) {
                        String texto = v instanceof ReporteExportable.Porcentaje p ? p.valor() + "%"
                                : (v == null ? "" : String.valueOf(v));
                        agregarCelda(t, texto);
                    }
                }
                if (tabla.getFilas().isEmpty()) {
                    PdfPCell vacia = new PdfPCell(new Phrase("Sin resultados para estos filtros", subtitulo));
                    vacia.setColspan(tabla.getColumnas().size());
                    vacia.setPadding(10);
                    t.addCell(vacia);
                }
                documento.add(t);
                documento.add(new Paragraph(" "));
            }
        } catch (DocumentException ex) {
            throw new IOException("No se pudo generar el reporte PDF.", ex);
        } finally {
            if (documento.isOpen()) documento.close();
        }
    }

    /** Excel exige nombres de hoja únicos, de máx. 31 caracteres y sin []:*?/\ */
    private String nombreHoja(String titulo, Set<String> usados) {
        String base = WorkbookUtil.createSafeSheetName(titulo == null || titulo.isBlank() ? "Datos" : titulo);
        if (base.length() > 28) base = base.substring(0, 28);
        String nombre = base;
        for (int i = 2; usados.contains(nombre); i++) nombre = base + " " + i;
        usados.add(nombre);
        return nombre;
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
        Font label = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, GRIS);
        Font value = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 17, AZUL);
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
        celda.setBackgroundColor(AZUL);
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
