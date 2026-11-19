package com.skillbridge.ai.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillbridge.ai.dto.EventoExtraidoIa;
import com.skillbridge.ai.model.TipoAudiencia;
import com.skillbridge.ai.model.TipoEvento;
import com.skillbridge.ai.repository.TipoAudienciaRepository;
import com.skillbridge.ai.repository.TipoEventoRepository;
import com.skillbridge.ai.util.OperacionInvalidaException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * "Subir documento con los datos de un evento y extraerlos con IA" (mismo
 * patrón que ExtraccionProyectoIaService, aplicado al formulario de "Nuevo
 * evento" del calendario). Sube una agenda/convocatoria y la IA llena el
 * formulario como BORRADOR editable; nunca se guarda nada aquí.
 *
 * "tipo" y "audiencia" se resuelven contra tipos_evento/tipos_audiencia
 * (misma idea que el catálogo de tecnologías en ExtraccionProyectoIaService):
 * la IA solo puede elegir un código que ya exista en esas tablas.
 */
@Service
public class ExtraccionEventoIaService {

    private static final int MAX_CARACTERES_DOCUMENTO = 12_000;
    private static final int MAX_OUTPUT_TOKENS = 1024;

    private final AsistenteIaService asistenteIaService;
    private final TipoEventoRepository tipoEventoRepository;
    private final TipoAudienciaRepository tipoAudienciaRepository;
    private final ObjectMapper objectMapper;

    public ExtraccionEventoIaService(AsistenteIaService asistenteIaService, TipoEventoRepository tipoEventoRepository,
                                      TipoAudienciaRepository tipoAudienciaRepository, ObjectMapper objectMapper) {
        this.asistenteIaService = asistenteIaService;
        this.tipoEventoRepository = tipoEventoRepository;
        this.tipoAudienciaRepository = tipoAudienciaRepository;
        this.objectMapper = objectMapper;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ExtraccionCruda(String tipo, String titulo, String fechaInicio, String audiencia,
                                    String ubicacion, String enlace, String descripcion) {}

    public EventoExtraidoIa extraer(MultipartFile archivo) {
        String texto = ExtractorDocumentoUtil.extraerTexto(archivo);
        if (texto.length() > MAX_CARACTERES_DOCUMENTO) {
            texto = texto.substring(0, MAX_CARACTERES_DOCUMENTO);
        }

        List<TipoEvento> tiposEvento = tipoEventoRepository.findAll();
        List<TipoAudiencia> tiposAudiencia = tipoAudienciaRepository.findAll();
        String codigosTipo = tiposEvento.stream().map(t -> t.getCodigo() + " (" + t.getNombre() + ")")
                .reduce((a, b) -> a + ", " + b).orElse("");
        String codigosAudiencia = tiposAudiencia.stream().map(t -> t.getCodigo() + " (" + t.getNombre() + ")")
                .reduce((a, b) -> a + ", " + b).orElse("");

        String promptSistema = "Eres un asistente que extrae datos de un evento de proyecto (reunión, hito o "
                + "entregable) a partir de un documento (convocatoria, correo o agenda) para pre-llenar un "
                + "formulario. Responde SOLO con un JSON valido (sin markdown, sin texto adicional, sin ```), "
                + "con esta forma exacta:\n"
                + "{\"tipo\": string|null, \"titulo\": string|null, \"fechaInicio\": \"yyyy-MM-ddTHH:mm\"|null, "
                + "\"audiencia\": string|null, \"ubicacion\": string|null, \"enlace\": string|null, "
                + "\"descripcion\": string|null}\n"
                + "Reglas: \"tipo\" DEBE ser textualmente uno de estos códigos (elige el más cercano al "
                + "contenido del documento; si ninguno aplica, usa null): " + codigosTipo + ". "
                + "\"audiencia\" DEBE ser textualmente uno de estos códigos (si ninguno aplica, usa null): "
                + codigosAudiencia + ". La fecha debe ir en formato yyyy-MM-ddTHH:mm (24 horas); si el "
                + "documento no da una hora exacta, usa 09:00. \"enlace\" es una URL de reunión virtual (Meet, "
                + "Zoom, Teams) si la hay. Si el documento no trae un dato, usa null. No inventes datos que no "
                + "estén en el texto.";

        AsistenteIaService.Resultado r = asistenteIaService.consultar(promptSistema, texto, MAX_OUTPUT_TOKENS);
        if (!r.ok()) {
            throw new OperacionInvalidaException("El Asistente IA no pudo procesar el documento ahora mismo. "
                    + "Intenta de nuevo en un momento o completa el formulario manualmente.");
        }

        return resolver(parsear(r.texto()), tiposEvento, tiposAudiencia);
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

    private EventoExtraidoIa resolver(ExtraccionCruda cruda, List<TipoEvento> tiposEvento, List<TipoAudiencia> tiposAudiencia) {
        EventoExtraidoIa salida = new EventoExtraidoIa();
        List<String> advertencias = new ArrayList<>();

        salida.setTitulo(ExtractorDocumentoUtil.limpiarTexto(cruda.titulo()));
        salida.setUbicacion(ExtractorDocumentoUtil.limpiarTexto(cruda.ubicacion()));
        salida.setEnlace(ExtractorDocumentoUtil.limpiarTexto(cruda.enlace()));
        salida.setDescripcion(ExtractorDocumentoUtil.limpiarTexto(cruda.descripcion()));

        String tipoCodigo = resolverCodigo(cruda.tipo(), tiposEvento.stream().map(TipoEvento::getCodigo).toList());
        if (cruda.tipo() != null && !cruda.tipo().isBlank() && tipoCodigo == null) {
            advertencias.add("No se reconoció el tipo de evento detectado (\"" + cruda.tipo().trim() + "\"); elígelo manualmente.");
        }
        salida.setTipo(tipoCodigo);

        String audienciaCodigo = resolverCodigo(cruda.audiencia(), tiposAudiencia.stream().map(TipoAudiencia::getCodigo).toList());
        if (cruda.audiencia() != null && !cruda.audiencia().isBlank() && audienciaCodigo == null) {
            advertencias.add("No se reconoció la audiencia detectada (\"" + cruda.audiencia().trim() + "\"); elígela manualmente.");
        }
        salida.setAudiencia(audienciaCodigo);

        LocalDateTime fecha = validarFechaHora(cruda.fechaInicio(), advertencias);
        salida.setFechaInicio(fecha == null ? null : fecha.toString());

        if (salida.getTitulo() == null) advertencias.add("No se encontró un título claro para el evento; complétalo manualmente.");
        salida.setAdvertencias(advertencias);
        return salida;
    }

    private static String resolverCodigo(String valor, List<String> codigosValidos) {
        if (valor == null || valor.isBlank()) return null;
        String clave = valor.trim().toLowerCase(Locale.ROOT);
        for (String codigo : codigosValidos) {
            if (codigo.equalsIgnoreCase(clave)) return codigo;
        }
        return null;
    }

    private static LocalDateTime validarFechaHora(String fecha, List<String> advertencias) {
        if (fecha == null || fecha.isBlank()) return null;
        try {
            return LocalDateTime.parse(fecha.trim());
        } catch (DateTimeParseException ex) {
            advertencias.add("No se pudo interpretar la fecha/hora detectada (\"" + fecha + "\"); complétala manualmente.");
            return null;
        }
    }
}
