package com.skillbridge.ai.service;

import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class PadronDniService {

    private static final Logger LOG = LoggerFactory.getLogger(PadronDniService.class);
    private static final Pattern FORMATO_DNI = Pattern.compile("[0-9]{8}");
    public static final String NO_ENCONTRADO = "No se encuentra en nuestros registros";
    public static final String NO_DISPONIBLE = "La consulta de DNI no está disponible en este momento.";

    public record Persona(String nombres, String apellidos) {}

    private final Map<String, Persona> padron;

    public PadronDniService(@Value("${app.registro.padron-archivo}") String archivo) {
        padron = cargar(archivo);
    }

    public boolean formatoValido(String dni) {
        return dni != null && FORMATO_DNI.matcher(dni).matches();
    }

    public boolean disponible() {
        return !padron.isEmpty();
    }

    public Optional<Persona> buscar(String dni) {
        return formatoValido(dni) ? Optional.ofNullable(padron.get(dni)) : Optional.empty();
    }

    private Map<String, Persona> cargar(String archivo) {
        var datos = new HashMap<String, Persona>();
        try (var entrada = Files.newInputStream(Path.of(archivo)); var libro = WorkbookFactory.create(entrada)) {
            var hoja = libro.getSheet("api_Reniec_simulado");
            if (hoja == null) throw new IOException("Falta la hoja api_Reniec_simulado.");

            var formato = new DataFormatter(Locale.ROOT);
            Row titulos = hoja.getRow(hoja.getFirstRowNum());
            if (titulos == null) throw new IOException("Faltan los encabezados del padrón.");
            var columnas = new HashMap<String, Integer>();
            for (var celda : titulos) {
                String titulo = formato.formatCellValue(celda).strip().toLowerCase(Locale.ROOT);
                columnas.put(titulo, celda.getColumnIndex());
            }
            if (!columnas.keySet().containsAll(java.util.Set.of("dni", "nombres", "apellidos"))) {
                throw new IOException("Faltan las columnas DNI, Nombres o Apellidos.");
            }

            for (Row fila : hoja) {
                if (fila.getRowNum() == titulos.getRowNum()) continue;
                String nombres = texto(formato, fila, columnas.get("nombres"));
                String apellidos = texto(formato, fila, columnas.get("apellidos"));
                String dni = leerDni(formato, fila, columnas.get("dni"));
                if (dni.isEmpty() && nombres.isEmpty() && apellidos.isEmpty()) continue;
                if (!formatoValido(dni) || nombres.isEmpty() || apellidos.isEmpty()
                        || nombres.length() + apellidos.length() + 1 > 150) {
                    throw new IOException("Fila inválida: " + (fila.getRowNum() + 1));
                }
                if (datos.putIfAbsent(dni, new Persona(nombres, apellidos)) != null) {
                    throw new IOException("DNI duplicado en la fila " + (fila.getRowNum() + 1));
                }
            }
            if (datos.isEmpty()) throw new IOException("La hoja no contiene participantes.");
            LOG.info("Padrón simulado de DNI cargado con {} registros.", datos.size());
            return Map.copyOf(datos);
        } catch (IOException | RuntimeException ex) {
            LOG.warn("Padrón simulado de DNI no disponible. Revisa PADRON_DNI_ARCHIVO y el formato de la hoja ({}).",
                    ex.getClass().getSimpleName());
            return Map.of();
        }
    }

    private String texto(DataFormatter formato, Row fila, int columna) {
        return formato.formatCellValue(fila.getCell(columna)).strip();
    }

    private String leerDni(DataFormatter formato, Row fila, int columna) {
        var celda = fila.getCell(columna);
        if (celda != null && celda.getCellType() == CellType.NUMERIC) {
            double numero = celda.getNumericCellValue();
            if (numero >= 0 && numero <= 99_999_999 && numero == Math.rint(numero)) {
                return String.format(Locale.ROOT, "%08d", (long) numero);
            }
            return "-";
        }
        return texto(formato, fila, columna);
    }
}
