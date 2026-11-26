package com.skillbridge.ai.service;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.BadElementException;
import com.lowagie.text.Image;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfTemplate;
import com.lowagie.text.pdf.PdfWriter;
import com.skillbridge.ai.dto.FichaProyecto;
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
import java.util.List;
import java.util.Map;

@Service
public class ReportePmExportService {

    private static final String[] COLUMNAS = {
            "Proyecto", "Estado", "Inicio", "Entrega estimada", "Avance estimado",
            "Equipo activo", "Carga asignada", "Situación"
    };

    public void escribirExcel(ReportePmDatos datos, OutputStream salida) throws IOException {
        escribirExcel(datos, List.of(), salida);
    }

    /** Hoja "Reporte" con la tabla comparativa y, después, una hoja por proyecto con su ficha completa. */
    public void escribirExcel(ReportePmDatos datos, List<FichaProyecto> fichas, OutputStream salida) throws IOException {
        try (XSSFWorkbook libro = new XSSFWorkbook()) {
            Map<Long, Cell> celdasProyecto = new java.util.HashMap<>();
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

            String[] kpiNombres = {"Proyectos", "Avance promedio", "Asignaciones activas", "Atrasados", "Riesgo alto", "Entregables vencidos"};
            double[] kpiValores = {datos.getTotalProyectos(), datos.getAvancePromedio() / 100.0,
                    datos.getAsignacionesActivas(), datos.getAtrasados(), datos.getRiesgoAlto(), datos.getEntregablesVencidos()};
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
                Cell nombreProyecto = row.createCell(0);
                nombreProyecto.setCellValue(fila.getProyecto());
                celdasProyecto.put(fila.getProyectoId(), nombreProyecto);
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

            // Una hoja por proyecto; el nombre en la tabla lleva a su hoja.
            if (!fichas.isEmpty()) {
                Row nota = hoja.createRow(9);
                nota.createCell(0).setCellValue("Cada proyecto tiene su propia hoja con la ficha completa: haz clic en su nombre.");
                Map<Long, String> nombres = FichaProyectoExport.hojas(libro, fichas);
                nombres.forEach((id, nombreHoja) -> {
                    Cell c = celdasProyecto.get(id);
                    if (c != null) FichaProyectoExport.enlazar(libro, c, nombreHoja);
                });
            }
            libro.write(salida);
        }
    }

    public void escribirPdf(ReportePmDatos datos, OutputStream salida) throws IOException {
        escribirPdf(datos, List.of(), salida);
    }

    /** Portada con indicadores y tabla comparativa y, después, una página por proyecto. */
    public void escribirPdf(ReportePmDatos datos, List<FichaProyecto> fichas, OutputStream salida) throws IOException {
        Document documento = new Document(PageSize.A4.rotate(), 28, 28, 28, 28);
        try {
            PdfWriter writer = PdfWriter.getInstance(documento, salida);
            documento.open();

        Font titulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, new Color(24, 44, 78));
        Font subtitulo = FontFactory.getFont(FontFactory.HELVETICA, 9, new Color(100, 112, 133));
        documento.add(new Paragraph("SkillBridge AI · Reporte de proyectos", titulo));
        documento.add(new Paragraph("Generado: " + datos.getGenerado(), subtitulo));
        documento.add(new Paragraph(datos.getFiltrosLabel(), subtitulo));
        documento.add(new Paragraph(" "));

        PdfPTable kpis = new PdfPTable(6);
        kpis.setWidthPercentage(100);
        agregarKpi(kpis, "PROYECTOS", String.valueOf(datos.getTotalProyectos()));
        agregarKpi(kpis, "AVANCE PROMEDIO", datos.getAvancePromedio() + "%");
        agregarKpi(kpis, "ASIGNACIONES ACTIVAS", String.valueOf(datos.getAsignacionesActivas()));
        agregarKpi(kpis, "ATRASADOS", String.valueOf(datos.getAtrasados()));
        agregarKpi(kpis, "RIESGO ALTO", String.valueOf(datos.getRiesgoAlto()));
        agregarKpi(kpis, "ENTREGABLES VENCIDOS", String.valueOf(datos.getEntregablesVencidos()));
        documento.add(kpis);
        documento.add(new Paragraph(" "));

        Font seccion = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, new Color(24, 44, 78));

        // ── Panorama: las mismas dos donas (Estado / Riesgo) que se ven en
        // la pestaña "Resumen" de la pantalla, dibujadas con el mismo cálculo
        // de segmentos para que nunca se desincronicen del resto del reporte.
        documento.add(new Paragraph("Panorama", seccion));
        documento.add(new Paragraph("Resumen por estado y por riesgo.", subtitulo));
        documento.add(new Paragraph(" "));
        PdfContentByte cb = writer.getDirectContent();
        PdfPTable donas = new PdfPTable(2);
        donas.setWidthPercentage(100);
        donas.addCell(celdaGrafico("Resumen por estado",
                dona(cb, datos.getSegmentos(), "proyecto(s)", datos.getTotalProyectos())));
        long totalRiesgo = datos.getSegmentosRiesgo().stream().mapToLong(ReportePmDatos.Segmento::getCantidad).sum();
        donas.addCell(celdaGrafico("Resumen por riesgo",
                dona(cb, datos.getSegmentosRiesgo(), "en riesgo alto/medio", totalRiesgo)));
        documento.add(donas);
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
            agregarCeldaNum(tabla, fila.getAvance() + "%");
            agregarCeldaNum(tabla, String.valueOf(fila.getEquipoActivo()));
            agregarCeldaNum(tabla, fila.getCargaAsignada() + "%");
            agregarCelda(tabla, fila.getSituacion());
        }
        if (datos.getFilas().isEmpty()) {
            PdfPCell vacia = new PdfPCell(new Phrase("Sin resultados para estos filtros", subtitulo));
            vacia.setColspan(8);
            vacia.setPadding(10);
            tabla.addCell(vacia);
        }
            documento.add(tabla);
            if (!fichas.isEmpty()) {
                documento.add(new Paragraph(" "));
                documento.add(new Paragraph("En las páginas siguientes: una ficha completa por proyecto ("
                        + fichas.size() + ").", subtitulo));
                FichaProyectoExport.paginas(documento, fichas);
            }
        } catch (DocumentException ex) {
            throw new IOException("No se pudo generar el reporte PDF.", ex);
        } finally {
            if (documento.isOpen()) documento.close();
        }
    }

    /** Color de PDF (no depende de las variables CSS de la pantalla) para cada segmento de las donas. */
    private static Color colorSegmento(String etiqueta) {
        return switch (etiqueta) {
            case "Activo" -> new Color(0x1B, 0xAF, 0x7A);
            case "En pausa" -> new Color(0xED, 0xA1, 0x00);
            case "Planificación" -> new Color(0x2A, 0x78, 0xD6);
            case "Completado" -> new Color(0x62, 0x50, 0xD6);
            case "Cancelado" -> new Color(0xE3, 0x49, 0x48);
            case "Alto" -> new Color(0xCF, 0x4A, 0x3C);
            case "Medio" -> new Color(0xD9, 0x9A, 0x2B);
            case "Bajo" -> new Color(0x2E, 0x9A, 0x65);
            default -> new Color(107, 115, 133);
        };
    }

    private PdfPCell celdaGrafico(String titulo, Image img) {
        PdfPCell c = new PdfPCell();
        c.setBorderColor(new Color(225, 229, 236));
        c.setPadding(10);
        c.addElement(new Paragraph(titulo, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9.5f, new Color(29, 36, 51))));
        img.setSpacingBefore(6);
        c.addElement(img);
        return c;
    }

    /** Dona con el total al centro y leyenda con cantidad y porcentaje, igual que la del Resource Manager. */
    private Image dona(PdfContentByte cb, List<ReportePmDatos.Segmento> segs, String unidad, long total) {
        float w = 240, h = 132, cx = 62, cy = 66, r = 50, grosor = 17;
        PdfTemplate t = cb.createTemplate(w, h);
        t.setLineWidth(grosor);
        if (total == 0 || segs.isEmpty()) {
            t.setColorStroke(new Color(232, 236, 242));
            t.circle(cx, cy, r); t.stroke();
        } else {
            float inicio = 90;
            long conValor = segs.stream().filter(x -> x.getCantidad() > 0).count();
            float hueco = conValor > 1 ? 2.2f : 0;
            for (ReportePmDatos.Segmento s : segs) {
                if (s.getCantidad() == 0) continue;
                float ext = 360f * s.getCantidad() / total;
                t.setColorStroke(colorSegmento(s.getLabel()));
                t.arc(cx - r, cy - r, cx + r, cy + r, inicio - hueco / 2, -(ext - hueco));
                t.stroke();
                inicio -= ext;
            }
        }
        texto(t, String.valueOf(total), cx, cy - 2, 20, true, new Color(24, 44, 78), Element.ALIGN_CENTER);
        texto(t, unidad, cx, cy - 15, 7, false, new Color(107, 115, 133), Element.ALIGN_CENTER);
        float y = cy + (segs.size() * 17) / 2f - 10;
        for (ReportePmDatos.Segmento s : segs) {
            t.setColorFill(colorSegmento(s.getLabel()));
            t.roundRectangle(134, y, 8, 8, 2); t.fill();
            texto(t, s.getLabel(), 147, y + 1, 7.5f, false, new Color(29, 36, 51), Element.ALIGN_LEFT);
            texto(t, s.getCantidad() + "  ·  " + s.getPorcentajeRedondeado() + "%", w - 2, y + 1, 7.5f, true, new Color(29, 36, 51), Element.ALIGN_RIGHT);
            y -= 17;
        }
        return imagen(t);
    }

    private static Image imagen(PdfTemplate t) {
        try {
            Image img = Image.getInstance(t);
            img.setAlignment(Element.ALIGN_LEFT);
            return img;
        } catch (BadElementException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void texto(PdfTemplate t, String s, float x, float y, float tam, boolean negrita, Color c, int alineacion) {
        try {
            BaseFont bf = BaseFont.createFont(negrita ? BaseFont.HELVETICA_BOLD : BaseFont.HELVETICA, BaseFont.WINANSI, false);
            t.beginText();
            t.setFontAndSize(bf, tam);
            t.setColorFill(c);
            t.showTextAligned(alineacion, s, x, y, 0);
            t.endText();
        } catch (Exception e) {
            throw new IllegalStateException(e);
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

    /** Igual que agregarCelda, pero alineada a la derecha — para columnas numéricas (%, conteos). */
    private void agregarCeldaNum(PdfPTable tabla, String texto) {
        Font font = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.DARK_GRAY);
        PdfPCell celda = new PdfPCell(new Phrase(texto, font));
        celda.setPadding(6);
        celda.setBorderColor(new Color(225, 229, 235));
        celda.setHorizontalAlignment(Element.ALIGN_RIGHT);
        tabla.addCell(celda);
    }
}
