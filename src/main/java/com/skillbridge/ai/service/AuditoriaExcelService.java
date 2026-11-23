package com.skillbridge.ai.service;

import org.apache.poi.ss.SpreadsheetVersion;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDate;
import java.util.Objects;

import static com.skillbridge.ai.service.AuditoriaService.COLUMNAS_EXPORTACION;

@Service
public class AuditoriaExcelService {
    private static final int MAX_FILAS = SpreadsheetVersion.EXCEL2007.getMaxRows();
    private static final int MAX_TEXTO = SpreadsheetVersion.EXCEL2007.getMaxTextLength();
    private static final String[] COLUMNAS_TEXTOS = {"ID de evento", "Campo", "Parte", "Texto"};
    private static final int[] ANCHOS = {21, 24, 28, 21, 35, 24, 13, 13, 23, 21, 53, 40, 40};
    private final AuditoriaService auditoria;

    public AuditoriaExcelService(AuditoriaService auditoria) {
        this.auditoria = auditoria;
    }

    public void escribir(LocalDate desde, LocalDate hasta, String usuario, String accion, OutputStream salida)
            throws IOException {
        SXSSFWorkbook excel = new SXSSFWorkbook(100);
        excel.setCompressTempFiles(true);
        try (excel) {
            var reporte = new LibroAuditoria(excel);
            long total = auditoria.recorrerExportacion(desde, hasta, usuario, accion, reporte::agregar);
            reporte.finalizar(desde, hasta, usuario, accion, total);
            excel.write(salida);
        } finally {
            excel.dispose();
        }
    }

    private static final class LibroAuditoria {
        private final SXSSFWorkbook excel;
        private final Sheet resumen;
        private final CellStyle encabezado;
        private final CellStyle[] texto;
        private final CellStyle[] fecha;
        private final CellStyle alta;
        private final CellStyle media;
        private Sheet eventos;
        private Sheet textosLargos;
        private int hojaEventos = 1;
        private int hojaTextos = 0;
        private int filaEvento = 1;
        private int filaTexto = MAX_FILAS;

        LibroAuditoria(SXSSFWorkbook excel) {
            this.excel = excel;
            encabezado = estilo(0x19334C, true, 0xFFFFFF);
            texto = new CellStyle[]{estilo(0xFFFFFF, false, 0x233648),
                    estilo(0xEDF3F8, false, 0x233648)};
            fecha = new CellStyle[texto.length];
            for (int i = 0; i < texto.length; i++) {
                fecha[i] = excel.createCellStyle();
                fecha[i].cloneStyleFrom(texto[i]);
                fecha[i].setDataFormat(excel.createDataFormat().getFormat("dd/mm/yyyy hh:mm:ss"));
            }
            alta = estilo(0xFAE5E7, true, 0x852F39);
            media = estilo(0xFFF0D3, true, 0x74551E);
            eventos = tabla("Eventos", COLUMNAS_EXPORTACION, ANCHOS);
            resumen = excel.createSheet("Resumen");
        }

        void agregar(AuditoriaService.EventoExportado e) {
            if (filaEvento == MAX_FILAS) {
                activarFiltros(eventos, filaEvento, COLUMNAS_EXPORTACION.length);
                eventos = tabla("Eventos " + ++hojaEventos, COLUMNAS_EXPORTACION, ANCHOS);
                filaEvento = 1;
            }
            Row fila = eventos.createRow(filaEvento++);
            int banda = fila.getRowNum() % texto.length;
            String id = Objects.toString(e.id(), "");
            String[] valores = e.columnas(null);

            int lineas = 1;
            for (int i = 0; i < valores.length; i++) {
                CellStyle estilo = texto[banda];
                if (i == 1) {
                    var celda = fila.createCell(i);
                    if (e.fecha() != null) celda.setCellValue(e.fecha());
                    celda.setCellStyle(fecha[banda]);
                    continue;
                }
                if (i == 6) {
                    estilo = switch (e.severidad()) {
                        case "Alta" -> alta;
                        case "Media" -> media;
                        default -> estilo;
                    };
                }
                String valor = guardarTextoLargo(id, COLUMNAS_EXPORTACION[i], Objects.toString(valores[i], ""));
                celda(fila, i, valor, estilo);
                lineas = Math.max(lineas, estimarLineas(valor, ANCHOS[i] - 3));
            }
            fila.setHeightInPoints(6 + 15 * Math.min(26, lineas));
        }

        private String guardarTextoLargo(String id, String campo, String valor) {
            if (valor.length() <= MAX_TEXTO) return valor;
            int parte = 1;
            for (int inicio = 0; inicio < valor.length();) {
                if (filaTexto == MAX_FILAS) {
                    if (textosLargos != null) activarFiltros(textosLargos, filaTexto, COLUMNAS_TEXTOS.length);
                    hojaTextos++;
                    String nombre = hojaTextos == 1 ? "Textos largos" : "Textos largos " + hojaTextos;
                    textosLargos = tabla(nombre, COLUMNAS_TEXTOS, new int[]{18, 23, 10, 105});
                    filaTexto = 1;
                }
                int fin = inicio + Math.min(MAX_TEXTO, valor.length() - inicio);
                if (fin < valor.length() && Character.isHighSurrogate(valor.charAt(fin - 1))) fin--;
                Row fila = textosLargos.createRow(filaTexto++);
                fila.setHeightInPoints(120);
                celda(fila, 0, id, texto[0]);
                celda(fila, 1, campo, texto[0]);
                fila.createCell(2).setCellValue(parte++);
                celda(fila, 3, valor.substring(inicio, fin), texto[0]);
                inicio = fin;
            }
            int finVista = Character.isHighSurrogate(valor.charAt(299)) ? 299 : 300;
            return valor.substring(0, finVista) + "…\nTexto completo en «Textos largos» · evento " + id;
        }

        void finalizar(LocalDate desde, LocalDate hasta, String usuario, String accion, long total) {
            activarFiltros(eventos, filaEvento, COLUMNAS_EXPORTACION.length);
            if (textosLargos != null) activarFiltros(textosLargos, filaTexto, COLUMNAS_TEXTOS.length);
            resumen.setColumnWidth(0, 24 * 256);
            resumen.setColumnWidth(1, 86 * 256);
            filaResumen(0, "Auditoría", "SkillBridge AI", encabezado, 28);
            filaResumen(2, "Periodo (Lima)", desde + " al " + hasta + " · ambas fechas incluidas", texto[0], 23);
            filaResumen(3, "Usuario", usuario.isBlank() ? "Todos" : usuario.trim(), texto[1], 37);
            filaResumen(4, "Acción", accion.isBlank() ? "Todas" : accion.trim(), texto[0], 37);
            Row conteo = resumen.createRow(5);
            conteo.setHeightInPoints(23);
            celda(conteo, 0, "Eventos exportados", texto[1]);
            conteo.createCell(1).setCellValue(total);
            conteo.getCell(1).setCellStyle(texto[1]);
            filaResumen(7, "Clasificación", "Tipo, severidad y origen se derivan del registro de auditoría.", texto[0], 25);
            if (textosLargos != null) {
                filaResumen(8, "Textos largos", "Busca el ID de evento y el campo. Las partes numeradas contienen el texto íntegro.",
                        texto[1], 40);
            }
        }

        private void filaResumen(int numero, String etiqueta, String valor, CellStyle estilo, int altura) {
            Row fila = resumen.createRow(numero);
            fila.setHeightInPoints(altura);
            celda(fila, 0, etiqueta, estilo);
            celda(fila, 1, valor, estilo);
        }

        private Sheet tabla(String nombre, String[] columnas, int[] anchos) {
            Sheet hoja = excel.createSheet(nombre);
            hoja.createFreezePane(0, 1);
            Row titulos = hoja.createRow(0);
            titulos.setHeightInPoints(30);
            for (int i = 0; i < columnas.length; i++) {
                celda(titulos, i, columnas[i], encabezado);
                hoja.setColumnWidth(i, anchos[i] * 256);
            }
            return hoja;
        }

        private CellStyle estilo(int fondo, boolean negrita, int color) {
            var estilo = (XSSFCellStyle) excel.createCellStyle();
            var fuente = (XSSFFont) excel.createFont();
            fuente.setBold(negrita);
            fuente.setColor(color(color));
            estilo.setFont(fuente);
            estilo.setFillForegroundColor(color(fondo));
            estilo.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            estilo.setVerticalAlignment(negrita ? VerticalAlignment.CENTER : VerticalAlignment.TOP);
            estilo.setWrapText(true);
            return estilo;
        }

        private XSSFColor color(int rgb) {
            return new XSSFColor(new byte[]{(byte) (rgb >> 16), (byte) (rgb >> 8), (byte) rgb});
        }

        private void celda(Row fila, int indice, String valor, CellStyle estilo) {
            var celda = fila.createCell(indice);
            celda.setCellValue(valor);
            celda.setCellStyle(estilo);
        }

        private void activarFiltros(Sheet hoja, int siguienteFila, int columnas) {
            hoja.setAutoFilter(new CellRangeAddress(0, siguienteFila - 1, 0, columnas - 1));
        }

        private int estimarLineas(String valor, int ancho) {
            int total = 0;
            int inicio = 0;
            int salto;
            while ((salto = valor.indexOf('\n', inicio)) >= 0) {
                total += Math.max(1, (salto - inicio + ancho - 1) / ancho);
                inicio = salto + 1;
            }
            return total + Math.max(1, (valor.length() - inicio + ancho - 1) / ancho);
        }
    }
}
