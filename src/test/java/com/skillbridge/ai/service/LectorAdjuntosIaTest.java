package com.skillbridge.ai.service;

import com.skillbridge.ai.util.OperacionInvalidaException;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/** Adjuntos del Asistente IA: tipo por contenido y Excel a texto con límites. */
class LectorAdjuntosIaTest {

    private static byte[] excel(int filas, boolean conFormula) throws Exception {
        try (XSSFWorkbook libro = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet hoja = libro.createSheet("Equipo");
            Row cab = hoja.createRow(0);
            cab.createCell(0).setCellValue("Nombre");
            cab.createCell(1).setCellValue("Horas");
            for (int i = 1; i <= filas; i++) {
                Row r = hoja.createRow(i);
                r.createCell(0).setCellValue("Persona " + i);
                r.createCell(1).setCellValue(i * 10);
            }
            if (conFormula) hoja.createRow(filas + 1).createCell(1).setCellFormula("SUM(B2:B3)");
            libro.write(out);
            return out.toByteArray();
        }
    }

    private static byte[] zipCon(String... entradas) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (String e : entradas) { zip.putNextEntry(new ZipEntry(e)); zip.write("x".getBytes()); zip.closeEntry(); }
        }
        return out.toByteArray();
    }

    @Test
    @DisplayName("Reconoce PDF, PNG, JPEG, Excel y texto por su contenido")
    void tiposValidos() throws Exception {
        assertEquals("application/pdf", LectorAdjuntosIa.tipo("informe.pdf", "%PDF-1.7\n...".getBytes()));
        assertEquals("image/png", LectorAdjuntosIa.tipo("foto.png", new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0}));
        assertEquals("image/jpeg", LectorAdjuntosIa.tipo("foto.jpg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0}));
        assertEquals(LectorAdjuntosIa.XLSX, LectorAdjuntosIa.tipo("equipo.xlsx", excel(2, false)));
        assertEquals(LectorAdjuntosIa.TEXTO, LectorAdjuntosIa.tipo("notas.csv", "nombre;horas\nAna;10".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("Rechaza archivos disfrazados, con macros, HTML y SVG")
    void tiposRechazados() throws Exception {
        assertNull(LectorAdjuntosIa.tipo("informe.pdf", new byte[]{'M', 'Z', (byte) 0x90, 0}), "un .exe renombrado a .pdf");
        assertNull(LectorAdjuntosIa.tipo("macro.xlsx", zipCon("xl/workbook.xml", "xl/vbaProject.bin")), "Excel con macros");
        assertNull(LectorAdjuntosIa.tipo("macro.xlsm", excel(1, false)), "extensión .xlsm");
        assertNull(LectorAdjuntosIa.tipo("cualquier.xlsx", zipCon("word/document.xml")), "ZIP que no es un libro de Excel");
        assertNull(LectorAdjuntosIa.tipo("pagina.html", "<script>alert(1)</script>".getBytes()), "HTML");
        assertNull(LectorAdjuntosIa.tipo("dibujo.svg", "<svg onload=alert(1)>".getBytes()), "SVG");
        assertNull(LectorAdjuntosIa.tipo("binario.txt", new byte[]{'h', 0, 'o', 0}), "binario renombrado a .txt");
        assertNull(LectorAdjuntosIa.tipo("vacio.txt", new byte[0]));
    }

    @Test
    @DisplayName("Excel a texto: valores por columnas, fórmula con su valor guardado y límite de filas")
    void excelATexto() throws Exception {
        String texto = LectorAdjuntosIa.excelATexto(excel(2, true));
        assertTrue(texto.startsWith("Hoja: Equipo"));
        assertTrue(texto.contains("Nombre\tHoras"));
        assertTrue(texto.contains("Persona 2\t20"));
        assertFalse(texto.contains("SUM("), "nunca se muestra la fórmula como instrucción");

        String largo = LectorAdjuntosIa.excelATexto(excel(LectorAdjuntosIa.MAX_FILAS + 50, false));
        assertTrue(largo.contains("hoja recortada"));
        assertFalse(largo.contains("Persona " + (LectorAdjuntosIa.MAX_FILAS + 10)));
    }

    @Test
    @DisplayName("Un .xlsx dañado da un mensaje claro")
    void excelDanado() {
        assertThrows(OperacionInvalidaException.class, () -> LectorAdjuntosIa.excelATexto("PK\u0003\u0004roto".getBytes()));
    }
}
