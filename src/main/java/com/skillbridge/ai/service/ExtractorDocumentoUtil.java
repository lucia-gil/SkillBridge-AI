package com.skillbridge.ai.service;

import com.skillbridge.ai.util.OperacionInvalidaException;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Lectura de texto desde PDF/Word(.docx)/texto plano, compartida por los
 * distintos "Extraer con IA" (proyectos, entregables, eventos): misma
 * lógica que originalmente vivía solo en ExtraccionProyectoIaService,
 * movida aquí para no repetirla en cada servicio de extracción nuevo.
 */
final class ExtractorDocumentoUtil {

    static final long MAX_BYTES = 5L * 1024 * 1024;

    private ExtractorDocumentoUtil() {
    }

    static String extraerTexto(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new OperacionInvalidaException("Selecciona un archivo (PDF, Word o texto) para extraer los datos.");
        }
        if (archivo.getSize() > MAX_BYTES) {
            throw new OperacionInvalidaException("El archivo pesa más de 5 MB.");
        }

        String nombre = archivo.getOriginalFilename() == null ? "" : archivo.getOriginalFilename().toLowerCase(Locale.ROOT);
        String tipo = archivo.getContentType() == null ? "" : archivo.getContentType().toLowerCase(Locale.ROOT);
        String texto;
        try {
            if (nombre.endsWith(".pdf") || tipo.equals("application/pdf")) {
                texto = extraerPdf(archivo.getBytes());
            } else if (nombre.endsWith(".docx") || tipo.contains("wordprocessingml")) {
                texto = extraerDocx(archivo.getBytes());
            } else if (nombre.endsWith(".txt") || tipo.startsWith("text/")) {
                texto = new String(archivo.getBytes(), StandardCharsets.UTF_8);
            } else {
                throw new OperacionInvalidaException("Formato no soportado. Sube un archivo PDF, Word (.docx) o texto (.txt).");
            }
        } catch (OperacionInvalidaException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new OperacionInvalidaException("No se pudo leer \"" + archivo.getOriginalFilename()
                    + "\". Verifica que el archivo no esté dañado.");
        }

        if (texto == null || texto.isBlank()) {
            throw new OperacionInvalidaException("No se pudo leer texto de \"" + archivo.getOriginalFilename()
                    + "\". Revisa que no sea un documento escaneado (solo imágenes) o vacío.");
        }
        return texto;
    }

    private static String extraerPdf(byte[] bytes) throws IOException {
        PdfReader reader = new PdfReader(bytes);
        try {
            StringBuilder sb = new StringBuilder();
            PdfTextExtractor extractor = new PdfTextExtractor(reader);
            int paginas = reader.getNumberOfPages();
            for (int i = 1; i <= paginas; i++) {
                sb.append(extractor.getTextFromPage(i)).append('\n');
            }
            return sb.toString();
        } finally {
            reader.close();
        }
    }

    private static String extraerDocx(byte[] bytes) throws IOException {
        try (InputStream in = new ByteArrayInputStream(bytes); XWPFDocument doc = new XWPFDocument(in)) {
            StringBuilder sb = new StringBuilder();
            for (XWPFParagraph p : doc.getParagraphs()) {
                sb.append(p.getText()).append('\n');
            }
            doc.getTables().forEach(tabla -> tabla.getRows().forEach(fila ->
                    fila.getTableCells().forEach(celda -> sb.append(celda.getText()).append(' '))));
            return sb.toString();
        }
    }

    /** Claude a veces envuelve el JSON en ```json ... ``` aunque se le pida que no lo haga. */
    static String limpiarJson(String texto) {
        if (texto == null) return "{}";
        String t = texto.trim();
        if (t.startsWith("```")) {
            int fin = t.lastIndexOf("```");
            int inicioContenido = t.indexOf('\n');
            if (inicioContenido > 0 && fin > inicioContenido) {
                t = t.substring(inicioContenido + 1, fin).trim();
            }
        }
        return t;
    }

    static String limpiarTexto(String texto) {
        if (texto == null) return null;
        String t = texto.trim();
        return (t.isEmpty() || t.equalsIgnoreCase("null")) ? null : t;
    }
}
