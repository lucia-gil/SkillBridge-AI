package com.skillbridge.ai.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillbridge.ai.dto.ProyectoExtraidoIa;
import com.skillbridge.ai.model.Habilidad;
import com.skillbridge.ai.repository.HabilidadRepository;
import com.skillbridge.ai.util.OperacionInvalidaException;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * "Subir documento con los datos de un proyecto nuevo y extraerlos con IA"
 * (idea de Lucia, modelo v7 fase 5): el Project Manager sube un PDF/Word/txt
 * con la info del proyecto (una ficha, un correo, un brief) y la IA llena el
 * formulario de "Nuevo proyecto" como BORRADOR editable. Nunca se guarda
 * nada aquí: solo se arma el ProyectoExtraidoIa que la vista usa para
 * pre-llenar los campos; el PM confirma con "Crear proyecto" como siempre.
 *
 * Reutiliza AsistenteIaService.consultar(...) (mismo Gemini con reintentos y
 * respaldo que el Asistente IA) y el catálogo de habilidades/tecnologías del
 * Administrador: la IA solo puede proponer tecnologías/habilidades que ya
 * existen en el catálogo (igual regla que TecnologiaCatalogoService), para
 * no inventar texto libre que luego el formulario rechazaría.
 */
@Service
public class ExtraccionProyectoIaService {

    private static final long MAX_BYTES = 5L * 1024 * 1024;
    /** Límite de texto que se manda a Gemini (fichas/briefs no suelen pasar de esto; evita prompts gigantes). */
    private static final int MAX_CARACTERES_DOCUMENTO = 12_000;
    private static final int MAX_OUTPUT_TOKENS = 2048;

    private final AsistenteIaService asistenteIaService;
    private final HabilidadRepository habilidadRepository;
    private final ObjectMapper objectMapper;

    public ExtraccionProyectoIaService(AsistenteIaService asistenteIaService, HabilidadRepository habilidadRepository,
                                        ObjectMapper objectMapper) {
        this.asistenteIaService = asistenteIaService;
        this.habilidadRepository = habilidadRepository;
        this.objectMapper = objectMapper;
    }

    /** Lo que Gemini debe devolver; nombres sueltos (se resuelven contra el catálogo real después). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ExtraccionCruda(String nombre, String descripcion, List<String> tecnologias,
                                    String fechaInicio, String fechaFinEstimada, Integer colaboradoresRequeridos,
                                    List<HabilidadCruda> habilidadesRequeridas) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record HabilidadCruda(String nombre, Integer nivelRequerido) {}

    @Transactional(readOnly = true)
    public ProyectoExtraidoIa extraer(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new OperacionInvalidaException("Selecciona un archivo (PDF, Word o texto) para extraer los datos.");
        }
        if (archivo.getSize() > MAX_BYTES) {
            throw new OperacionInvalidaException("El archivo pesa más de 5 MB.");
        }

        String texto = extraerTexto(archivo);
        if (texto == null || texto.isBlank()) {
            throw new OperacionInvalidaException("No se pudo leer texto de \"" + archivo.getOriginalFilename()
                    + "\". Revisa que no sea un documento escaneado (solo imágenes) o vacío.");
        }
        if (texto.length() > MAX_CARACTERES_DOCUMENTO) {
            texto = texto.substring(0, MAX_CARACTERES_DOCUMENTO);
        }

        List<Habilidad> catalogo = habilidadRepository.findAllConCategoriaOrderByNombre();
        String nombresCatalogo = catalogo.stream().map(Habilidad::getNombre)
                .reduce((a, b) -> a + ", " + b).orElse("");

        String promptSistema = "Eres un asistente que extrae datos de un proyecto de software a partir de un "
                + "documento (ficha, correo o brief) para pre-llenar un formulario. Responde SOLO con un JSON "
                + "valido (sin markdown, sin texto adicional, sin ```), con esta forma exacta:\n"
                + "{\"nombre\": string|null, \"descripcion\": string|null, \"tecnologias\": [string], "
                + "\"fechaInicio\": \"yyyy-MM-dd\"|null, \"fechaFinEstimada\": \"yyyy-MM-dd\"|null, "
                + "\"colaboradoresRequeridos\": number|null, "
                + "\"habilidadesRequeridas\": [{\"nombre\": string, \"nivelRequerido\": 1-5}]}\n"
                + "Reglas: \"tecnologias\" y los \"nombre\" de \"habilidadesRequeridas\" DEBEN ser textualmente "
                + "nombres de este catalogo (elige los mas cercanos al texto, ignora mayusculas/acentos; si "
                + "ninguno aplica, omite el elemento): " + nombresCatalogo + ". "
                + "Las fechas deben ir en formato yyyy-MM-dd; si el documento no da una fecha exacta, usa null. "
                + "\"nivelRequerido\" es del 1 (basico) al 5 (experto); si no se menciona, usa 3. "
                + "Si el documento no trae un dato, usa null (o lista vacia). No inventes datos que no estén en el texto.";

        AsistenteIaService.Resultado r = asistenteIaService.consultar(promptSistema, texto, MAX_OUTPUT_TOKENS);
        if (!r.ok()) {
            throw new OperacionInvalidaException("El Asistente IA no pudo procesar el documento ahora mismo. "
                    + "Intenta de nuevo en un momento o completa el formulario manualmente.");
        }

        ExtraccionCruda cruda = parsear(r.texto());
        return resolver(cruda, catalogo);
    }

    private ExtraccionCruda parsear(String textoJson) {
        String limpio = limpiarJson(textoJson);
        try {
            return objectMapper.readValue(limpio, ExtraccionCruda.class);
        } catch (IOException ex) {
            throw new OperacionInvalidaException("El Asistente IA devolvió una respuesta que no se pudo interpretar. "
                    + "Intenta de nuevo o completa el formulario manualmente.");
        }
    }

    /** Gemini a veces envuelve el JSON en ```json ... ``` aunque se le pida que no lo haga. */
    private static String limpiarJson(String texto) {
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

    private ProyectoExtraidoIa resolver(ExtraccionCruda cruda, List<Habilidad> catalogo) {
        Map<String, Habilidad> porClave = new LinkedHashMap<>();
        for (Habilidad h : catalogo) {
            porClave.putIfAbsent(TecnologiaCatalogoService.clave(h.getNombre()), h);
        }

        ProyectoExtraidoIa salida = new ProyectoExtraidoIa();
        List<String> advertencias = new ArrayList<>();

        salida.setNombre(limpiarTexto(cruda.nombre()));
        salida.setDescripcion(limpiarTexto(cruda.descripcion()));
        salida.setColaboradoresRequeridos(cruda.colaboradoresRequeridos());

        List<String> tecnologiasResueltas = new ArrayList<>();
        if (cruda.tecnologias() != null) {
            for (String t : cruda.tecnologias()) {
                if (t == null || t.isBlank()) continue;
                Habilidad h = porClave.get(TecnologiaCatalogoService.clave(t));
                if (h != null) {
                    if (!tecnologiasResueltas.contains(h.getNombre())) tecnologiasResueltas.add(h.getNombre());
                } else {
                    advertencias.add("\"" + t.trim() + "\" no está en el catálogo de tecnologías; revísalo manualmente.");
                }
            }
        }
        salida.setTecnologias(tecnologiasResueltas);

        salida.setFechaInicio(validarFecha(cruda.fechaInicio(), advertencias, "fecha de inicio"));
        salida.setFechaFinEstimada(validarFecha(cruda.fechaFinEstimada(), advertencias, "fecha de fin estimada"));

        List<ProyectoExtraidoIa.HabilidadRequeridaExtraida> habilidadesResueltas = new ArrayList<>();
        if (cruda.habilidadesRequeridas() != null) {
            for (HabilidadCruda hc : cruda.habilidadesRequeridas()) {
                if (hc == null || hc.nombre() == null || hc.nombre().isBlank()) continue;
                Habilidad h = porClave.get(TecnologiaCatalogoService.clave(hc.nombre()));
                if (h == null) {
                    advertencias.add("\"" + hc.nombre().trim() + "\" no está en el catálogo de habilidades; agrégala manualmente si corresponde.");
                    continue;
                }
                int nivel = hc.nivelRequerido() == null ? 3 : Math.max(1, Math.min(5, hc.nivelRequerido()));
                boolean yaEsta = habilidadesResueltas.stream().anyMatch(e -> e.habilidadId().equals(h.getId()));
                if (!yaEsta) {
                    habilidadesResueltas.add(new ProyectoExtraidoIa.HabilidadRequeridaExtraida(h.getId(), h.getNombre(), nivel));
                }
            }
        }
        salida.setHabilidadesRequeridas(habilidadesResueltas);

        if (salida.getNombre() == null) advertencias.add("No se encontró un nombre claro para el proyecto; complétalo manualmente.");
        salida.setAdvertencias(advertencias);
        return salida;
    }

    private static String limpiarTexto(String texto) {
        if (texto == null) return null;
        String t = texto.trim();
        return (t.isEmpty() || t.equalsIgnoreCase("null")) ? null : t;
    }

    private static String validarFecha(String fecha, List<String> advertencias, String etiqueta) {
        if (fecha == null || fecha.isBlank()) return null;
        try {
            return LocalDate.parse(fecha.trim()).toString();
        } catch (DateTimeParseException ex) {
            advertencias.add("No se pudo interpretar la " + etiqueta + " (\"" + fecha + "\"); complétala manualmente.");
            return null;
        }
    }

    // ───────────────────────── Extracción de texto por formato ─────────────────────────

    private String extraerTexto(MultipartFile archivo) {
        String nombre = archivo.getOriginalFilename() == null ? "" : archivo.getOriginalFilename().toLowerCase(Locale.ROOT);
        String tipo = archivo.getContentType() == null ? "" : archivo.getContentType().toLowerCase(Locale.ROOT);
        try {
            if (nombre.endsWith(".pdf") || tipo.equals("application/pdf")) {
                return extraerPdf(archivo.getBytes());
            }
            if (nombre.endsWith(".docx") || tipo.contains("wordprocessingml")) {
                return extraerDocx(archivo.getBytes());
            }
            if (nombre.endsWith(".txt") || tipo.startsWith("text/")) {
                return new String(archivo.getBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception ex) {
            throw new OperacionInvalidaException("No se pudo leer \"" + archivo.getOriginalFilename()
                    + "\". Verifica que el archivo no esté dañado.");
        }
        throw new OperacionInvalidaException("Formato no soportado. Sube un archivo PDF, Word (.docx) o texto (.txt).");
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
}
