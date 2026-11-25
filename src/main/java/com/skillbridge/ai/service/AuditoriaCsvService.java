package com.skillbridge.ai.service;

import com.opencsv.CSVWriter;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static com.skillbridge.ai.service.AuditoriaService.COLUMNAS_EXPORTACION;

@Service
public class AuditoriaCsvService {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");   // día/mes/año, como el resto del sistema
    private final AuditoriaService auditoria;

    public AuditoriaCsvService(AuditoriaService auditoria) {
        this.auditoria = auditoria;
    }

    public void escribir(LocalDate desde, LocalDate hasta, String usuario, String accion, OutputStream salida)
            throws IOException {
        var escritor = new OutputStreamWriter(salida, StandardCharsets.UTF_8);
        escritor.write('\uFEFF');
        var csv = new CSVWriter(escritor, ';', '"', '"', "\r\n");
        csv.writeNext(COLUMNAS_EXPORTACION);
        auditoria.recorrerExportacion(desde, hasta, usuario, accion, evento -> {
            String[] valores = evento.columnas(evento.fecha() == null ? "" : evento.fecha().format(FECHA));
            for (int i = 0; i < valores.length; i++) valores[i] = texto(valores[i]);
            csv.writeNext(valores);
            if (csv.getException() != null) throw csv.getException();
        });
        csv.flush();
        if (csv.getException() != null) throw csv.getException();
    }

    private String texto(String valor) {
        if (valor == null) return "";
        // Excel puede interpretar una celda CSV como fórmula aunque esté entre comillas.
        String inicio = valor.stripLeading();
        if ((!inicio.isEmpty() && "=+-@＝＋－＠".indexOf(inicio.charAt(0)) >= 0)
                || valor.startsWith("\t") || valor.startsWith("\r") || valor.startsWith("\n")) {
            return "'" + valor;
        }
        return valor;
    }
}
