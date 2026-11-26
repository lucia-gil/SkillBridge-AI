package com.skillbridge.ai.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillbridge.ai.dto.EntregableExtraidoIa;
import com.skillbridge.ai.util.OperacionInvalidaException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * "Subir documento con los datos de un entregable y extraerlos con IA"
 * (mismo patrón que ExtraccionProyectoIaService, aplicado al formulario de
 * "Nuevo entregable" del PM). El PM sube un PDF/Word/txt con el brief del
 * entregable (rúbrica, correo, instrucciones) y la IA llena el formulario
 * como BORRADOR editable; nunca se guarda nada aquí.
 */
@Service
public class ExtraccionEntregableIaService {

    private static final int MAX_CARACTERES_DOCUMENTO = 12_000;
    private static final int MAX_OUTPUT_TOKENS = 1024;

    private final AsistenteIaService asistenteIaService;
    private final ObjectMapper objectMapper;

    public ExtraccionEntregableIaService(AsistenteIaService asistenteIaService, ObjectMapper objectMapper) {
        this.asistenteIaService = asistenteIaService;
        this.objectMapper = objectMapper;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ExtraccionCruda(String titulo, String descripcion, String fechaApertura,
                                    String fechaCierre) {}

    public EntregableExtraidoIa extraer(MultipartFile archivo) {
        String texto = ExtractorDocumentoUtil.extraerTexto(archivo);
        if (texto.length() > MAX_CARACTERES_DOCUMENTO) {
            texto = texto.substring(0, MAX_CARACTERES_DOCUMENTO);
        }

        String promptSistema = "Eres un asistente que extrae datos de un entregable (una tarea o hito que un "
                + "equipo debe cumplir) a partir de un documento (rúbrica, correo o instrucciones) para "
                + "pre-llenar un formulario. Responde SOLO con un JSON valido (sin markdown, sin texto "
                + "adicional, sin ```), con esta forma exacta:\n"
                + "{\"titulo\": string|null, \"descripcion\": string|null, "
                + "\"fechaApertura\": \"yyyy-MM-ddTHH:mm\"|null, \"fechaCierre\": \"yyyy-MM-ddTHH:mm\"|null}\n"
                + "Reglas: las fechas deben ir en formato yyyy-MM-ddTHH:mm (24 horas); si el documento no da "
                + "una hora exacta, usa 23:59. \"fechaApertura\" es cuando se habilita el entregable y "
                + "\"fechaCierre\" es la fecha límite de entrega; fechaCierre siempre debe ser posterior a "
                + "fechaApertura. No extraigas notas ni puntajes: el entregable se revisa por "
                + "estados (Completado, Requiere ajustes, Rehacer), no con nota. Si el documento no trae un dato, usa null. No inventes datos que no estén en el texto.";

        AsistenteIaService.Resultado r = asistenteIaService.consultar(promptSistema, texto, MAX_OUTPUT_TOKENS);
        if (!r.ok()) {
            throw new OperacionInvalidaException("El Asistente IA no pudo procesar el documento ahora mismo. "
                    + "Intenta de nuevo en un momento o completa el formulario manualmente.");
        }

        return resolver(parsear(r.texto()));
    }

    private ExtraccionCruda parsear(String textoJson) {
        String limpio = ExtractorDocumentoUtil.limpiarJson(textoJson);
        try {
            return objectMapper.readValue(limpio, ExtraccionCruda.class);
        } catch (IOException ex) {
            throw new OperacionInvalidaException("El Asistente IA devolvió una respuesta que no se pudo interpretar. "
                    + "Intenta de nuevo o completa el formulario manualmente.");
        }
    }

    private EntregableExtraidoIa resolver(ExtraccionCruda cruda) {
        EntregableExtraidoIa salida = new EntregableExtraidoIa();
        List<String> advertencias = new ArrayList<>();

        salida.setTitulo(ExtractorDocumentoUtil.limpiarTexto(cruda.titulo()));
        salida.setDescripcion(ExtractorDocumentoUtil.limpiarTexto(cruda.descripcion()));

        LocalDateTime apertura = validarFechaHora(cruda.fechaApertura(), advertencias, "fecha de apertura");
        LocalDateTime cierre = validarFechaHora(cruda.fechaCierre(), advertencias, "fecha de cierre");
        if (apertura != null && cierre != null && !cierre.isAfter(apertura)) {
            advertencias.add("La fecha de cierre detectada no es posterior a la de apertura; revísalas manualmente.");
            apertura = null;
            cierre = null;
        }
        salida.setFechaApertura(apertura == null ? null : apertura.toString());
        salida.setFechaCierre(cierre == null ? null : cierre.toString());

        if (salida.getTitulo() == null) advertencias.add("No se encontró un título claro para el entregable; complétalo manualmente.");
        salida.setAdvertencias(advertencias);
        return salida;
    }

    private static LocalDateTime validarFechaHora(String fecha, List<String> advertencias, String etiqueta) {
        if (fecha == null || fecha.isBlank()) return null;
        try {
            return LocalDateTime.parse(fecha.trim());
        } catch (DateTimeParseException ex) {
            advertencias.add("No se pudo interpretar la " + etiqueta + " (\"" + fecha + "\"); complétala manualmente.");
            return null;
        }
    }
}
