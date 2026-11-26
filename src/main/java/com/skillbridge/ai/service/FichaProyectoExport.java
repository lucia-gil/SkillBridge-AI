package com.skillbridge.ai.service;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.skillbridge.ai.dto.FichaProyecto;
import org.apache.poi.common.usermodel.HyperlinkType;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Hyperlink;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.awt.Color;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Ficha de proyecto en los exportables: una hoja de Excel por proyecto y una
 * página de PDF por proyecto. La usan los reportes del PM y del RM para que
 * ambos documentos se vean y se lean igual.
 */
public final class FichaProyectoExport {

    private FichaProyectoExport() {}

    private static final int COLUMNAS = 6;

    // ───────────────────────── Excel ─────────────────────────

    /** Crea una hoja por ficha y devuelve proyectoId → nombre de hoja (para enlazarla desde el resumen). */
    public static Map<Long, String> hojas(XSSFWorkbook wb, List<FichaProyecto> fichas) {
        Estilos e = new Estilos(wb);
        Set<String> usados = new HashSet<>();
        for (int i = 0; i < wb.getNumberOfSheets(); i++) usados.add(wb.getSheetName(i).toLowerCase());
        Map<Long, String> nombres = new LinkedHashMap<>();
        for (FichaProyecto f : fichas) {
            String nombre = nombreHoja(f.nombre(), usados);
            nombres.put(f.id(), nombre);
            hoja(wb.createSheet(nombre), f, e);
        }
        return nombres;
    }

    /** Convierte la celda en un enlace a la hoja del proyecto. */
    public static void enlazar(XSSFWorkbook wb, Cell celda, String hoja) {
        Hyperlink link = wb.getCreationHelper().createHyperlink(HyperlinkType.DOCUMENT);
        link.setAddress("'" + hoja.replace("'", "''") + "'!A1");
        celda.setHyperlink(link);
        XSSFCellStyle estilo = wb.createCellStyle();
        XSSFFont fuente = wb.createFont();
        fuente.setUnderline(XSSFFont.U_SINGLE);
        fuente.setColor(new XSSFColor(new byte[]{(byte) 46, (byte) 92, (byte) 184}, null));
        estilo.setFont(fuente);
        celda.setCellStyle(estilo);
    }

    private static String nombreHoja(String nombre, Set<String> usados) {
        String base = WorkbookUtil.createSafeSheetName(nombre == null || nombre.isBlank() ? "Proyecto" : nombre.trim());
        if (base.length() > 28) base = base.substring(0, 28).trim();
        String candidato = base;
        int n = 2;
        while (usados.contains(candidato.toLowerCase())) candidato = base + " " + n++;
        usados.add(candidato.toLowerCase());
        return candidato;
    }

    private static void hoja(Sheet h, FichaProyecto f, Estilos e) {
        h.setDisplayGridlines(false);
        int[] anchos = {26, 22, 22, 18, 18, 22};
        for (int i = 0; i < anchos.length; i++) h.setColumnWidth(i, anchos[i] * 256);
        int r = 0;
        celdaUnida(h, r++, f.nombre(), e.titulo, 26);
        celdaUnida(h, r++, f.estado() + " · " + f.fechaInicio() + " → " + f.fechaFin() + " · PM: " + f.projectManager(), e.sub, 18);
        r++;
        if (f.resumenIa() != null) {
            celdaUnida(h, r++, "Resumen IA", e.seccion, 20);
            r = parrafo(h, r, f.resumenIa(), e.ia);
            r++;
        }
        celdaUnida(h, r++, "De qué trata", e.seccion, 20);
        r = parrafo(h, r, f.descripcion(), e.texto);
        r++;

        celdaUnida(h, r++, "Datos clave", e.seccion, 20);
        String[][] datos = {
                {"Estado", f.estado(), "Avance estimado", f.avance() + "%"},
                {"Inicio", f.fechaInicio(), "Entrega estimada", f.fechaFin()},
                {"Situación", f.situacion(), "Riesgo", f.riesgo()},
                {"Project Manager", f.projectManager(), "Vacantes", String.valueOf(f.vacantes())},
                {"Equipo", f.equipo().size() + " persona(s)", "Carga del equipo", f.cargaEquipo() + "%"},
        };
        for (String[] d : datos) {
            Row row = h.createRow(r++);
            celda(row, 0, d[0], e.etiqueta); celda(row, 1, d[1], e.texto);
            celda(row, 3, d[2], e.etiqueta); celda(row, 4, d[3], e.texto);
        }
        Row tec = h.createRow(r);
        celda(tec, 0, "Tecnologías", e.etiqueta);
        celda(tec, 1, f.tecnologias().isEmpty() ? "Sin tecnologías registradas" : String.join(", ", f.tecnologias()), e.texto);
        h.addMergedRegion(new CellRangeAddress(r, r, 1, COLUMNAS - 1));
        r += 2;

        celdaUnida(h, r++, "Equipo", e.seccion, 20);
        r = tabla(h, r, new String[]{"Nombre", "Cargo", "Rol en el proyecto", "Dedicación"}, e,
                f.equipo().stream().map(m -> new String[]{m.nombre(), m.cargo(), m.rol(), m.dedicacion() + "%"}).toList(),
                "Sin integrantes activos.");
        r++;

        celdaUnida(h, r++, "Habilidades requeridas (" + f.habilidadesCubiertas() + " de " + f.habilidades().size() + " cubiertas)", e.seccion, 20);
        r = tabla(h, r, new String[]{"Habilidad", "Nivel pedido", "Mejor nivel del equipo", "Quién", "¿Cubierta?"}, e,
                f.habilidades().stream().map(x -> new String[]{x.nombre(), x.nivelRequerido(), x.mejorNivel(), x.quien(),
                        x.cubierta() ? "Sí" : "No"}).toList(), "El proyecto no tiene habilidades requeridas.");
        r++;

        celdaUnida(h, r++, "Entregables y próximas fechas", e.seccion, 20);
        Row ent = h.createRow(r++);
        celda(ent, 0, "Total", e.etiqueta); celda(ent, 1, String.valueOf(f.entregablesTotal()), e.texto);
        celda(ent, 2, "Abiertos", e.etiqueta); celda(ent, 3, String.valueOf(f.entregablesAbiertos()), e.texto);
        celda(ent, 4, "Vencidos", e.etiqueta); celda(ent, 5, String.valueOf(f.entregablesVencidos()), e.texto);
        r = tabla(h, r, new String[]{"Fecha", "Título", "Tipo"}, e,
                f.proximos().stream().map(x -> new String[]{x.fecha(), x.titulo(), x.tipo()}).toList(), "Sin fechas próximas.");
        r++;

        celdaUnida(h, r++, "Alertas", e.seccion, 20);
        if (f.alertas().isEmpty()) celdaUnida(h, r++, "Sin alertas.", e.texto, 16);
        for (String a : f.alertas()) celdaUnida(h, r++, "• " + a, e.alerta, 16);
    }

    private static int tabla(Sheet h, int r, String[] cabecera, Estilos e, List<String[]> filas, String vacio) {
        Row c = h.createRow(r++);
        for (int i = 0; i < cabecera.length; i++) celda(c, i, cabecera[i], e.cabecera);
        if (filas.isEmpty()) { celdaUnida(h, r++, vacio, e.texto, 16); return r; }
        for (String[] f : filas) {
            Row row = h.createRow(r++);
            for (int i = 0; i < f.length; i++) celda(row, i, f[i], e.borde);
        }
        return r;
    }

    private static int parrafo(Sheet h, int r, String texto, CellStyle estilo) {
        Row row = h.createRow(r);
        Cell c = row.createCell(0);
        c.setCellValue(texto);
        c.setCellStyle(estilo);
        h.addMergedRegion(new CellRangeAddress(r, r, 0, COLUMNAS - 1));
        // Alto aproximado: ~110 caracteres por línea en el ancho de la hoja.
        int lineas = Math.max(1, (int) Math.ceil(texto.length() / 110.0) + (int) texto.chars().filter(ch -> ch == '\n').count());
        row.setHeightInPoints(Math.min(400, 16 * lineas + 4));
        return r + 1;
    }

    private static void celdaUnida(Sheet h, int r, String texto, CellStyle estilo, float alto) {
        Row row = h.createRow(r);
        row.setHeightInPoints(alto);
        celda(row, 0, texto, estilo);
        h.addMergedRegion(new CellRangeAddress(r, r, 0, COLUMNAS - 1));
    }

    private static void celda(Row row, int col, String texto, CellStyle estilo) {
        Cell c = row.createCell(col);
        c.setCellValue(texto == null ? "" : texto);
        c.setCellStyle(estilo);
    }

    private static final class Estilos {
        final CellStyle titulo, sub, seccion, texto, ia, etiqueta, cabecera, borde, alerta;

        Estilos(XSSFWorkbook wb) {
            titulo = estilo(wb, 16, true, rgb(24, 44, 78), null, false);
            sub = estilo(wb, 10, false, rgb(100, 116, 139), null, false);
            seccion = estilo(wb, 11, true, rgb(46, 92, 184), null, false);
            texto = estilo(wb, 10, false, rgb(30, 41, 59), null, true);
            ia = estilo(wb, 10, false, rgb(74, 52, 128), rgb(240, 235, 252), true);
            etiqueta = estilo(wb, 10, true, rgb(71, 85, 105), null, false);
            cabecera = estilo(wb, 10, true, rgb(255, 255, 255), rgb(24, 44, 78), false);
            borde = estilo(wb, 10, false, rgb(30, 41, 59), null, true);
            borde.setBorderBottom(BorderStyle.THIN);
            borde.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
            alerta = estilo(wb, 10, false, rgb(122, 83, 19), rgb(255, 244, 222), true);
        }

        private static XSSFColor rgb(int r, int g, int b) {
            return new XSSFColor(new byte[]{(byte) r, (byte) g, (byte) b}, null);
        }

        private static CellStyle estilo(XSSFWorkbook wb, int tam, boolean negrita, XSSFColor color, XSSFColor fondo, boolean ajustar) {
            XSSFCellStyle s = wb.createCellStyle();
            XSSFFont f = wb.createFont();
            f.setFontHeightInPoints((short) tam);
            f.setBold(negrita);
            f.setColor(color);
            s.setFont(f);
            if (fondo != null) { s.setFillForegroundColor(fondo); s.setFillPattern(FillPatternType.SOLID_FOREGROUND); }
            s.setWrapText(ajustar);
            s.setVerticalAlignment(VerticalAlignment.TOP);
            return s;
        }
    }

    // ───────────────────────── PDF ─────────────────────────

    private static final Color AZUL = new Color(24, 44, 78);
    private static final Color GRIS = new Color(100, 116, 139);
    private static final Color VIOLETA = new Color(74, 52, 128);

    /** Una página por proyecto, a continuación de lo que ya tenga el documento. */
    public static void paginas(Document doc, List<FichaProyecto> fichas) throws DocumentException {
        Font titulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 17, AZUL);
        Font sub = FontFactory.getFont(FontFactory.HELVETICA, 9.5f, GRIS);
        Font seccion = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, new Color(46, 92, 184));
        Font texto = FontFactory.getFont(FontFactory.HELVETICA, 9.5f, new Color(30, 41, 59));
        Font negrita = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9.5f, new Color(30, 41, 59));
        Font ia = FontFactory.getFont(FontFactory.HELVETICA, 9.5f, VIOLETA);
        Font iaTitulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9.5f, VIOLETA);
        Font alerta = FontFactory.getFont(FontFactory.HELVETICA, 9.5f, new Color(122, 83, 19));

        for (FichaProyecto f : fichas) {
            doc.newPage();
            doc.add(new Paragraph(f.nombre(), titulo));
            doc.add(new Paragraph(f.estado() + " · del " + f.fechaInicio() + " al " + f.fechaFin() + " · PM: " + f.projectManager()
                    + " · avance estimado " + f.avance() + "% · riesgo " + f.riesgo(), sub));
            espacio(doc);

            if (f.resumenIa() != null) {
                PdfPTable caja = new PdfPTable(1);
                caja.setWidthPercentage(100);
                Phrase frase = new Phrase();
                frase.add(new Chunk("Resumen IA  ", iaTitulo));
                frase.add(new Chunk(f.resumenIa(), ia));
                PdfPCell c = new PdfPCell(frase);
                c.setBackgroundColor(new Color(240, 235, 252));
                c.setBorderColor(new Color(214, 200, 245));
                c.setPadding(8);
                caja.addCell(c);
                doc.add(caja);
                espacio(doc);
            }

            doc.add(new Paragraph("De qué trata", seccion));
            doc.add(new Paragraph(f.descripcion(), texto));
            Paragraph tec = new Paragraph();
            tec.add(new Chunk("Tecnologías: ", negrita));
            tec.add(new Chunk(f.tecnologias().isEmpty() ? "sin tecnologías registradas" : String.join(", ", f.tecnologias()), texto));
            doc.add(tec);
            espacio(doc);

            PdfPTable datos = new PdfPTable(new float[]{1.2f, 1.4f, 1.2f, 1.4f, 1.2f, 1.4f});
            datos.setWidthPercentage(100);
            String[][] pares = {{"Situación", f.situacion()}, {"Vacantes", String.valueOf(f.vacantes())},
                    {"Carga del equipo", f.cargaEquipo() + "%"}, {"Entregables", String.valueOf(f.entregablesTotal())},
                    {"Abiertos", String.valueOf(f.entregablesAbiertos())}, {"Vencidos", String.valueOf(f.entregablesVencidos())}};
            for (String[] p : pares) { datos.addCell(dato(p[0], sub)); datos.addCell(dato(p[1], negrita)); }
            doc.add(datos);
            espacio(doc);

            PdfPTable doble = new PdfPTable(2);
            doble.setWidthPercentage(100);
            doble.setWidths(new float[]{1f, 1.15f});
            PdfPCell izq = new PdfPCell(); izq.setBorder(Rectangle.NO_BORDER); izq.setPaddingRight(8);
            izq.addElement(new Paragraph("Equipo (" + f.equipo().size() + ")", seccion));
            izq.addElement(tablaPdf(new String[]{"Nombre", "Rol", "Dedicación"}, f.equipo().stream()
                    .map(m -> new String[]{m.nombre() + " · " + m.cargo(), m.rol(), m.dedicacion() + "%"}).toList(),
                    "Sin integrantes activos.", texto));
            izq.addElement(new Paragraph("Próximas fechas", seccion));
            izq.addElement(tablaPdf(new String[]{"Fecha", "Título", "Tipo"}, f.proximos().stream()
                    .map(x -> new String[]{x.fecha(), x.titulo(), x.tipo()}).toList(), "Sin fechas próximas.", texto));
            PdfPCell der = new PdfPCell(); der.setBorder(Rectangle.NO_BORDER);
            der.addElement(new Paragraph("Habilidades (" + f.habilidadesCubiertas() + " de " + f.habilidades().size() + " cubiertas)", seccion));
            der.addElement(tablaPdf(new String[]{"Habilidad", "Pide", "Equipo", "¿Cubierta?"}, f.habilidades().stream()
                    .map(x -> new String[]{x.nombre(), x.nivelRequerido(), x.mejorNivel() + " (" + x.quien() + ")", x.cubierta() ? "Sí" : "No"}).toList(),
                    "Sin habilidades requeridas.", texto));
            der.addElement(new Paragraph("Alertas", seccion));
            if (f.alertas().isEmpty()) der.addElement(new Paragraph("Sin alertas.", texto));
            for (String a : f.alertas()) der.addElement(new Paragraph("• " + a, alerta));
            doble.addCell(izq);
            doble.addCell(der);
            doc.add(doble);
        }
    }

    private static PdfPCell dato(String s, Font f) {
        PdfPCell c = new PdfPCell(new Phrase(s, f));
        c.setBorderColor(new Color(226, 232, 240));
        c.setPadding(5);
        return c;
    }

    private static PdfPTable tablaPdf(String[] cabecera, List<String[]> filas, String vacio, Font texto) {
        PdfPTable t = new PdfPTable(cabecera.length);
        t.setWidthPercentage(100);
        t.setSpacingBefore(3);
        t.setSpacingAfter(8);
        Font cab = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8.5f, Color.WHITE);
        for (String c : cabecera) {
            PdfPCell celda = new PdfPCell(new Phrase(c, cab));
            celda.setBackgroundColor(AZUL);
            celda.setPadding(4);
            celda.setBorderColor(AZUL);
            t.addCell(celda);
        }
        if (filas.isEmpty()) {
            PdfPCell celda = new PdfPCell(new Phrase(vacio, texto));
            celda.setColspan(cabecera.length);
            celda.setPadding(4);
            celda.setBorderColor(new Color(226, 232, 240));
            t.addCell(celda);
        }
        for (String[] f : filas) {
            for (String v : f) {
                PdfPCell celda = new PdfPCell(new Phrase(v, texto));
                celda.setPadding(4);
                celda.setBorderColor(new Color(226, 232, 240));
                celda.setHorizontalAlignment(Element.ALIGN_LEFT);
                t.addCell(celda);
            }
        }
        return t;
    }

    private static void espacio(Document doc) throws DocumentException {
        doc.add(new Paragraph(" ", FontFactory.getFont(FontFactory.HELVETICA, 5)));
    }
}
