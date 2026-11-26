package com.skillbridge.ai.service;

import com.skillbridge.ai.util.OperacionInvalidaException;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Adjuntos del Asistente IA: reconoce el tipo por el CONTENIDO del archivo
 * (no por la extensión ni por lo que diga el navegador) y convierte Excel a
 * texto con límites. Admite imágenes PNG/JPEG/WEBP/GIF, PDF, texto UTF-8 y
 * Excel .xlsx sin macros. Rechaza todo lo demás (.xlsm, .xls, HTML, SVG,
 * ejecutables o archivos renombrados).
 */
public final class LectorAdjuntosIa {

    public static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    public static final String TEXTO = "text/plain";
    public static final Set<String> BINARIOS = Set.of("image/png", "image/jpeg", "image/webp", "image/gif", "application/pdf");

    /** Extensiones de texto plano aceptadas (sin HTML ni SVG: el navegador podría interpretarlos). */
    private static final Set<String> EXT_TEXTO = Set.of("txt", "csv", "md", "json", "log", "xml", "java", "sql",
            "js", "css", "yml", "yaml", "properties");

    static final int MAX_HOJAS = 3, MAX_FILAS = 500, MAX_COLUMNAS = 30;
    private static final long MAX_DESCOMPRIMIDO = 50L * 1024 * 1024;

    private LectorAdjuntosIa() {}

    /** Tipo MIME admitido según los bytes del archivo, o null si no se admite. */
    public static String tipo(String nombre, byte[] d) {
        if (d == null || d.length == 0) return null;
        String ext = extension(nombre);
        if (empieza(d, 0x25, 0x50, 0x44, 0x46, 0x2D)) return "application/pdf";                 // %PDF-
        if (empieza(d, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) return "image/png";
        if (empieza(d, 0xFF, 0xD8, 0xFF)) return "image/jpeg";
        if (empieza(d, 0x47, 0x49, 0x46, 0x38)) return "image/gif";                             // GIF8
        if (d.length > 12 && empieza(d, 0x52, 0x49, 0x46, 0x46)
                && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P') return "image/webp";
        if (empieza(d, 0x50, 0x4B, 0x03, 0x04)) return ("xlsx".equals(ext) && esXlsxSinMacros(d)) ? XLSX : null;   // ZIP
        if (EXT_TEXTO.contains(ext) && esTextoUtf8(d)) return TEXTO;
        return null;
    }

    /** Excel → texto separado por tabulaciones: solo valores (las fórmulas no se recalculan), con límites. */
    public static String excelATexto(byte[] datos) {
        ZipSecureFile.setMinInflateRatio(0.01);           // protección ante "bombas ZIP"
        ZipSecureFile.setMaxEntrySize(MAX_DESCOMPRIMIDO);
        DataFormatter formato = new DataFormatter(new Locale("es", "PE"));
        StringBuilder out = new StringBuilder();
        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(datos))) {
            int hojas = Math.min(libro.getNumberOfSheets(), MAX_HOJAS);
            for (int h = 0; h < hojas; h++) {
                Sheet hoja = libro.getSheetAt(h);
                out.append("Hoja: ").append(hoja.getSheetName()).append('\n');
                int filas = 0;
                for (Row fila : hoja) {
                    if (filas++ >= MAX_FILAS) { out.append("[… hoja recortada a ").append(MAX_FILAS).append(" filas]\n"); break; }
                    StringBuilder linea = new StringBuilder();
                    for (int c = 0; c < Math.min(fila.getLastCellNum(), MAX_COLUMNAS); c++) {
                        if (c > 0) linea.append('\t');
                        Cell celda = fila.getCell(c);
                        if (celda == null) continue;
                        // Fórmula: se usa el último valor guardado en el archivo, nunca se evalúa.
                        String v = celda.getCellType() == CellType.FORMULA ? valorGuardado(celda, formato) : formato.formatCellValue(celda);
                        linea.append(v.replaceAll("[\\t\\r\\n]+", " "));
                    }
                    if (!linea.toString().isBlank()) out.append(linea).append('\n');
                }
                out.append('\n');
            }
            if (libro.getNumberOfSheets() > MAX_HOJAS) out.append("[… solo se leyeron las primeras ").append(MAX_HOJAS).append(" hojas]\n");
        } catch (IOException | RuntimeException e) {
            throw new OperacionInvalidaException("No se pudo leer el Excel. Revisa que sea un .xlsx válido y sin protección.");
        }
        return out.toString();
    }

    private static String valorGuardado(Cell celda, DataFormatter formato) {
        return switch (celda.getCachedFormulaResultType()) {
            case NUMERIC -> formato.formatRawCellContents(celda.getNumericCellValue(), celda.getCellStyle().getDataFormat(),
                    celda.getCellStyle().getDataFormatString());
            case STRING -> celda.getRichStringCellValue().getString();
            case BOOLEAN -> String.valueOf(celda.getBooleanCellValue());
            default -> "";
        };
    }

    /** Un .xlsx es un ZIP con xl/workbook.xml; si trae vbaProject.bin tiene macros y se rechaza. */
    private static boolean esXlsxSinMacros(byte[] d) {
        boolean libro = false;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(d))) {
            ZipEntry e;
            int entradas = 0;
            while ((e = zip.getNextEntry()) != null) {
                if (++entradas > 2000) return false;
                String n = e.getName().toLowerCase(Locale.ROOT);
                if (n.endsWith("vbaproject.bin") || n.endsWith(".bin") && n.contains("activex")) return false;
                if (n.equals("xl/workbook.xml")) libro = true;
            }
        } catch (IOException e) {
            return false;
        }
        return libro;
    }

    private static boolean esTextoUtf8(byte[] d) {
        for (byte b : d) if (b == 0) return false;                       // un binario suele tener bytes nulos
        try {
            StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(d));
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }

    private static boolean empieza(byte[] d, int... firma) {
        if (d.length < firma.length) return false;
        for (int i = 0; i < firma.length; i++) if ((d[i] & 0xFF) != firma[i]) return false;
        return true;
    }

    private static String extension(String nombre) {
        String n = nombre == null ? "" : nombre.toLowerCase(Locale.ROOT);
        return n.contains(".") ? n.substring(n.lastIndexOf('.') + 1) : "";
    }
}
