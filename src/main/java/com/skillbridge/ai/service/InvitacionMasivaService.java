package com.skillbridge.ai.service;

import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.RFC4180ParserBuilder;
import com.opencsv.exceptions.CsvValidationException;
import com.skillbridge.ai.util.OperacionInvalidaException;
import com.skillbridge.ai.util.Roles;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.Serializable;
import java.io.StringReader;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipInputStream;

@Service
public class InvitacionMasivaService {
    private static final int MAX_BYTES = 1024 * 1024;
    private static final int MAX_XLSX_DESCOMPRIMIDO = 16 * MAX_BYTES;
    private static final int MAX_FILAS = 200;
    private static final String EXCEL_CONTENT = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml";
    private static final String[] CABECERA = {"correo", "rol"};
    private static final String[] ROLES = {"miembro", Roles.RESOURCE_MANAGER, Roles.ADMINISTRADOR};
    private static final Set<String> ROLES_VALIDOS = Set.of(ROLES);

    private final UsuarioService usuarios;

    public InvitacionMasivaService(UsuarioService usuarios) {
        this.usuarios = usuarios;
    }

    public record Fila(int numero, String correo, String rol, String error) implements Serializable {
        public boolean valida() { return error == null; }
        public String rolLabel() {
            return switch (rol) {
                case "miembro" -> "Miembro";
                case Roles.RESOURCE_MANAGER, Roles.ADMINISTRADOR -> Roles.etiqueta(rol);
                default -> rol.isEmpty() ? "—" : rol;
            };
        }
    }

    public record Revision(String token, Long actor, String archivo, boolean excel, Instant vence,
                           List<Fila> filas) implements Serializable {
        public long validas() { return filas.stream().filter(Fila::valida).count(); }
        public long rechazadas() { return filas.size() - validas(); }
        public boolean vigente(Long usuario) { return actor.equals(usuario) && vence.isAfter(Instant.now()); }
    }

    private record Cruda(int numero, List<String> columnas, boolean soloTexto) {}

    public Revision revisar(MultipartFile archivo, Long actor) {
        if (archivo.isEmpty() || archivo.getSize() > MAX_BYTES) {
            throw new OperacionInvalidaException("Selecciona un archivo de hasta 1 MB.");
        }
        String nombre = archivo.getOriginalFilename() == null ? "" : archivo.getOriginalFilename();
        nombre = nombre.substring(Math.max(nombre.lastIndexOf('/'), nombre.lastIndexOf('\\')) + 1);
        String extension = nombre.toLowerCase(Locale.ROOT);
        boolean excel = extension.endsWith(".xlsx");
        if (!excel && !extension.endsWith(".csv")) {
            throw new OperacionInvalidaException("Adjunta un archivo CSV (.csv) o Excel (.xlsx).");
        }

        List<Cruda> crudas = leer(archivo, excel);
        if (crudas.isEmpty()) throw new OperacionInvalidaException("Agrega al menos una fila debajo de la cabecera.");

        Map<String, Integer> repeticiones = new HashMap<>();
        for (Cruda fila : crudas) {
            if (!fila.columnas().isEmpty()) {
                repeticiones.merge(UsuarioService.normalizarCorreoInvitacion(fila.columnas().get(0)), 1, Integer::sum);
            }
        }
        var dominio = usuarios.dominioInvitacion();
        List<Fila> filas = crudas.stream().map(fila -> evaluar(fila, repeticiones, dominio)).toList();
        return new Revision(UUID.randomUUID().toString(), actor, abreviar(nombre, 120), excel,
                Instant.now().plusSeconds(900), filas);
    }

    public int confirmar(Revision revision, Long actor) {
        List<UsuarioService.InvitacionEntrada> entradas = revision.filas().stream()
                .filter(Fila::valida)
                .map(fila -> new UsuarioService.InvitacionEntrada(fila.correo(), fila.rol()))
                .toList();
        if (!revision.vigente(actor) || entradas.isEmpty()) {
            throw new OperacionInvalidaException("Vuelve a validar el archivo antes de confirmar.");
        }
        return usuarios.invitarLote(entradas, revision.excel(), actor);
    }

    private Fila evaluar(Cruda fila, Map<String, Integer> repeticiones, UsuarioService.DominioInvitacion dominio) {
        List<String> columnas = fila.columnas();
        String correo = UsuarioService.normalizarCorreoInvitacion(columnas.isEmpty() ? "" : columnas.get(0));
        String rol = columnas.size() < 2 ? "" : columnas.get(1).strip().toLowerCase(Locale.ROOT);
        String error = null;
        if (!fila.soloTexto()) {
            error = "Usa celdas de texto. No se admiten fórmulas, fechas ni números.";
        } else if (columnas.size() != 2) {
            error = "La fila debe contener exactamente las columnas correo y rol.";
        } else if (!ROLES_VALIDOS.contains(rol)) {
            error = "Elige miembro, resource_manager o administrador.";
        } else if (!correo.isEmpty() && repeticiones.getOrDefault(correo, 0) > 1) {
            error = "Correo repetido en el archivo. Corrige todas sus apariciones.";
        } else {
            try {
                usuarios.validarInvitacion(new UsuarioService.InvitacionEntrada(correo, rol), dominio);
            } catch (OperacionInvalidaException ex) {
                error = ex.getMessage();
            }
        }
        return new Fila(fila.numero(), abreviar(correo, 160), abreviar(rol, 60), error);
    }

    private static String abreviar(String texto, int max) {
        return texto.length() <= max ? texto : texto.substring(0, max - 1) + "…";
    }

    private List<Cruda> leer(MultipartFile archivo, boolean excel) {
        try {
            return excel ? leerExcel(archivo) : leerCsv(archivo);
        } catch (OperacionInvalidaException ex) {
            throw ex;
        } catch (IOException | CsvValidationException | RuntimeException ex) {
            throw new OperacionInvalidaException(excel
                    ? "El contenido debe ser un libro Excel .xlsx válido."
                    : "El contenido debe ser un CSV válido guardado en UTF-8.");
        }
    }

    private List<Cruda> leerCsv(MultipartFile archivo) throws IOException, CsvValidationException {
        String texto = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(archivo.getBytes())).toString();
        if (texto.startsWith("\uFEFF")) texto = texto.substring(1);
        String primeraLinea = texto.lines().findFirst().orElse("");
        char separador = primeraLinea.indexOf(';') >= 0 ? ';' : ',';
        try (CSVReader csv = new CSVReaderBuilder(new StringReader(texto))
                .withCSVParser(new RFC4180ParserBuilder().withSeparator(separador).build())
                .withMultilineLimit(3).build()) {
            String[] cabecera = csv.readNext();
            validarCabecera(cabecera == null ? List.of() : List.of(cabecera), true);
            List<Cruda> filas = new ArrayList<>();
            while (true) {
                int numero = (int) csv.getLinesRead() + 1;
                String[] columnas = csv.readNext();
                if (columnas == null) return filas;
                agregar(filas, new Cruda(numero, List.of(columnas), true));
            }
        }
    }

    private List<Cruda> leerExcel(MultipartFile archivo) throws IOException {
        comprobarTamanoExcel(archivo);
        try (var entrada = archivo.getInputStream(); XSSFWorkbook libro = new XSSFWorkbook(entrada)) {
            if (!EXCEL_CONTENT.equals(libro.getPackagePart().getContentType()) || libro.getNumberOfSheets() != 1) {
                throw new OperacionInvalidaException("Usa un libro .xlsx con una sola hoja y la cabecera correo, rol.");
            }
            var hoja = libro.getSheetAt(0);
            Cruda cabecera = leerFila(hoja.getRow(0));
            validarCabecera(cabecera.columnas(), cabecera.soloTexto());
            List<Cruda> filas = new ArrayList<>();
            for (Row fila : hoja) {
                if (fila.getRowNum() > 0) agregar(filas, leerFila(fila));
            }
            return filas;
        }
    }

    private void comprobarTamanoExcel(MultipartFile archivo) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(archivo.getInputStream())) {
            int entradas = 0;
            int bytes = 0;
            byte[] buffer = new byte[8192];
            while (zip.getNextEntry() != null) {
                if (++entradas > 100) throw new IOException("Demasiadas partes en el libro");
                for (int leidos; (leidos = zip.read(buffer)) != -1; ) {
                    bytes += leidos;
                    if (bytes > MAX_XLSX_DESCOMPRIMIDO) throw new IOException("Libro descomprimido demasiado grande");
                }
            }
            if (entradas == 0) throw new IOException("Libro vacío");
        }
    }

    private Cruda leerFila(Row fila) {
        if (fila == null) return new Cruda(1, List.of(), true);
        List<String> columnas = new ArrayList<>(List.of("", ""));
        boolean texto = true;
        for (Cell celda : fila) {
            if (celda.getCellType() == CellType.BLANK) continue;
            if (celda.getCellType() != CellType.STRING) {
                texto = false;
                continue;
            }
            String valor = celda.getStringCellValue();
            if (celda.getColumnIndex() < 2) columnas.set(celda.getColumnIndex(), valor);
            else if (!valor.isBlank() && columnas.size() == 2) columnas.add(valor);
        }
        return new Cruda(fila.getRowNum() + 1, columnas, texto);
    }

    private void validarCabecera(List<String> columnas, boolean soloTexto) {
        if (!soloTexto || columnas.size() != CABECERA.length
                || !CABECERA[0].equalsIgnoreCase(columnas.get(0).strip())
                || !CABECERA[1].equalsIgnoreCase(columnas.get(1).strip())) {
            throw new OperacionInvalidaException("La primera fila debe contener exactamente correo y rol, en ese orden.");
        }
    }

    private void agregar(List<Cruda> filas, Cruda fila) {
        if (fila.soloTexto() && fila.columnas().stream().allMatch(String::isBlank)) return;
        if (filas.size() >= MAX_FILAS) throw new OperacionInvalidaException("El archivo admite hasta 200 filas de datos.");
        filas.add(fila);
    }

    public void escribirPlantilla(boolean excel, OutputStream destino) throws IOException {
        if (!excel) {
            destino.write("\uFEFFcorreo,rol\r\n".getBytes(StandardCharsets.UTF_8));
            return;
        }
        try (XSSFWorkbook libro = new XSSFWorkbook()) {
            var hoja = libro.createSheet("Invitaciones");
            var estilo = libro.createCellStyle();
            var fuente = libro.createFont();
            fuente.setBold(true);
            estilo.setFont(fuente);
            Row cabecera = hoja.createRow(0);
            for (int i = 0; i < CABECERA.length; i++) {
                Cell celda = cabecera.createCell(i);
                celda.setCellValue(CABECERA[i]);
                celda.setCellStyle(estilo);
            }
            hoja.setColumnWidth(0, 44 * 256);
            hoja.setColumnWidth(1, 23 * 256);
            hoja.createFreezePane(0, 1);
            var validacion = hoja.getDataValidationHelper();
            var roles = validacion.createValidation(validacion.createExplicitListConstraint(ROLES),
                    new CellRangeAddressList(1, MAX_FILAS, 1, 1));
            roles.setShowErrorBox(true);
            roles.createErrorBox("Rol inválido", "Selecciona uno de los tres roles admitidos.");
            hoja.addValidationData(roles);
            libro.write(destino);
        }
    }
}
