package com.skillbridge.ai.service;

import jakarta.persistence.EntityManager;
import org.apache.poi.ss.SpreadsheetVersion;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDate;

@Service
public class AuditoriaExcelService {
    private static final SpreadsheetVersion FORMATO = SpreadsheetVersion.EXCEL2007;
    private static final String[] COLUMNAS = {"Fecha (Lima)", "Usuario", "Acción", "Tipo", "Severidad", "Entidad",
            "ID de entidad", "Detalle y motivo", "Valor anterior", "Valor nuevo"};
    private static final int[] ANCHOS = {23, 26, 35, 19, 14, 24, 16, 56, 44, 44};
    private final AuditoriaService auditoria;
    private final EntityManager entidades;

    public AuditoriaExcelService(AuditoriaService auditoria, EntityManager entidades) {
        this.auditoria = auditoria;
        this.entidades = entidades;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public void escribir(LocalDate desde, LocalDate hasta, String usuario, String accion, OutputStream salida)
            throws IOException {
        SXSSFWorkbook excel = new SXSSFWorkbook(100);
        excel.setCompressTempFiles(true);
        try (excel) {
            CellStyle estilo = excel.createCellStyle();
            var fuente = excel.createFont();
            fuente.setBold(true);
            fuente.setColor(IndexedColors.WHITE.getIndex());
            estilo.setFont(fuente);
            estilo.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            estilo.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            escribirFiltros(excel, desde, hasta, usuario, accion);

            int numero = 1;
            Sheet hoja = nuevaHoja(excel, estilo, numero);
            int fila = 1;
            var pagina = PageRequest.of(0, 500, Sort.by(Sort.Direction.DESC, "fecha", "id"));
            while (true) {
                var eventos = auditoria.buscarExportacion(desde == null ? null : desde.atStartOfDay(),
                        hasta == null ? null : hasta.plusDays(1).atStartOfDay(), usuario, accion, pagina);
                for (var evento : eventos) {
                    if (fila == FORMATO.getMaxRows()) {
                        filtros(hoja, fila);
                        hoja = nuevaHoja(excel, estilo, ++numero);
                        fila = 1;
                    }
                    escribirFila(hoja.createRow(fila++), evento.fecha(), evento.usuario(), evento.accion(), evento.tipo(),
                            evento.severidad(), evento.entidad(), evento.entidadId() == null ? "" : evento.entidadId().toString(),
                            evento.detalle(), evento.anterior(), evento.nuevo());
                }
                entidades.clear();
                if (!eventos.hasNext()) break;
                pagina = pagina.next();
            }
            filtros(hoja, fila);
            excel.write(salida);
        } finally {
            excel.dispose();
        }
    }

    private void escribirFiltros(SXSSFWorkbook excel, LocalDate desde, LocalDate hasta, String usuario, String accion) {
        Sheet hoja = excel.createSheet("Filtros");
        hoja.setColumnWidth(0, 23 * 256);
        hoja.setColumnWidth(1, 45 * 256);
        escribirFila(hoja.createRow(0), "SkillBridge AI · Auditoría");
        escribirFila(hoja.createRow(2), "Desde", desde == null ? "Cualquier fecha" : desde.toString());
        escribirFila(hoja.createRow(3), "Hasta", hasta == null ? "Cualquier fecha" : hasta.toString());
        escribirFila(hoja.createRow(4), "Usuario", usuario.isBlank() ? "Todos" : usuario.trim());
        escribirFila(hoja.createRow(5), "Acción", accion.isBlank() ? "Todas" : accion.trim());
    }

    private Sheet nuevaHoja(SXSSFWorkbook excel, CellStyle estilo, int numero) {
        Sheet hoja = excel.createSheet(numero == 1 ? "Eventos" : "Eventos " + numero);
        hoja.createFreezePane(0, 1);
        Row encabezado = hoja.createRow(0);
        escribirFila(encabezado, COLUMNAS);
        for (int i = 0; i < COLUMNAS.length; i++) {
            encabezado.getCell(i).setCellStyle(estilo);
            hoja.setColumnWidth(i, ANCHOS[i] * 256);
        }
        return hoja;
    }

    private void filtros(Sheet hoja, int fila) {
        if (fila > 1) hoja.setAutoFilter(new CellRangeAddress(0, fila - 1, 0, COLUMNAS.length - 1));
    }

    private void escribirFila(Row fila, String... textos) {
        int max = FORMATO.getMaxTextLength();
        for (int i = 0; i < textos.length; i++) {
            String texto = textos[i] == null ? "" : textos[i];
            fila.createCell(i).setCellValue(texto.length() > max ? texto.substring(0, max - 1) + "…" : texto);
        }
    }
}
