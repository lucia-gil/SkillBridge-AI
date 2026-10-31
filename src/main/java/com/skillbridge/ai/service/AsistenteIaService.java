package com.skillbridge.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.util.Roles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Asistente IA (administrador/, colaborador/, project-manager/ y
 * resource-manager/asistente-ia.html): le manda el mensaje del usuario a la
 * API de Google Gemini (generateContent) y devuelve la respuesta en texto
 * plano.
 *
 * Mismo patron de "consumo de servicio REST" visto en clase (verifica.id /
 * api.atenea): POST + clave de API + cuerpo y respuesta en JSON. Aqui el
 * "servicio externo" es Gemini en vez de un verificador de DNI/RUC. A
 * diferencia de otras APIs que usan la cabecera "Authorization: Bearer",
 * Gemini recibe la clave en la cabecera "x-goog-api-key".
 *
 * Se eligio Gemini (capa gratuita, sin tarjeta) en vez de Mistral porque la
 * API de Mistral solo da acceso real con un plan de pago (Pay-as-you-go o
 * Pro) incluso con cuenta gratuita de la app Mistral.
 *
 * No hay historial guardado en base de datos todavia (cada mensaje se
 * manda solo con el system prompt, sin memoria de turnos anteriores) - ver
 * ASISTENTE_IA.md para el detalle de que falta.
 */
@Service
public class AsistenteIaService {

    private static final Logger log = LoggerFactory.getLogger(AsistenteIaService.class);
    private static final String MODELO = "gemini-3.8-flash";
    private static final String GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/" + MODELO + ":generateContent";

    private final String apiKey;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public AsistenteIaService(@Value("${gemini.api.key:}") String apiKey, ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.create();
    }

    /** true si hay una clave configurada (secretos.properties local o variable de entorno GEMINI_API_KEY). */
    public boolean configurado() {
        return apiKey != null && !apiKey.isBlank();
    }

    public String responder(UsuarioSesion sesion, String mensajeUsuario) {
        return llamarGemini(promptSistema(sesion), mensajeUsuario, 500);
    }

    /**
     * Resume un hilo completo del foro (titulo + mensaje inicial + respuestas,
     * ya concatenados por ForoService) usando el mismo modelo del Asistente
     * IA, con un prompt de sistema distinto. Lo usa
     * ForoService.generarResumenIa, que ademas guarda el resultado en
     * foro_publicaciones.resumen_ia para no volver a gastar una llamada a
     * Gemini cada vez que alguien abre el hilo.
     */
    public String resumirHilo(String tituloHilo, String contenidoCompleto) {
        String prompt = "Eres el Asistente IA de SkillBridge AI. Te paso el contenido completo de un hilo del foro " +
                "de un proyecto (titulo \"" + tituloHilo + "\", seguido del mensaje inicial y las respuestas en orden). " +
                "Responde siempre en español, en un maximo de 4-5 lineas, con un resumen util para alguien que no leyo " +
                "el hilo: de que trata, que se acordo o decidio (si algo quedo claro), y que queda pendiente o sin responder " +
                "(si aplica). No inventes nada que no este en el texto. No agregues saludos ni frases de relleno, ve directo " +
                "al resumen.";
        return llamarGemini(prompt, contenidoCompleto, 350);
    }

    private String llamarGemini(String promptSistema, String mensajeUsuario, int maxOutputTokens) {
        if (!configurado()) {
            log.warn("Asistente IA: no hay gemini.api.key configurada (ver secretos.properties.example)");
            return "El Asistente IA todavía no está configurado en este servidor. Pídele a un administrador que agregue la clave de Gemini.";
        }

        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("systemInstruction", Map.of(
                "parts", List.of(Map.of("text", promptSistema))
        ));
        cuerpo.put("contents", List.of(
                Map.of("role", "user", "parts", List.of(Map.of("text", mensajeUsuario)))
        ));
        cuerpo.put("generationConfig", Map.of(
                "temperature", 0.4,
                "maxOutputTokens", maxOutputTokens
        ));

        try {
            String respuestaCruda = restClient.post()
                    .uri(GEMINI_URL)
                    .header("x-goog-api-key", apiKey)
                    .header("Content-Type", "application/json")
                    .body(cuerpo)
                    .retrieve()
                    .body(String.class);

            JsonNode raiz = objectMapper.readTree(respuestaCruda);
            JsonNode contenido = raiz.path("candidates").path(0).path("content").path("parts").path(0).path("text");
            if (contenido.isMissingNode() || contenido.asText().isBlank()) {
                log.warn("Asistente IA: respuesta de Gemini sin contenido util: {}", respuestaCruda);
                return "No pude generar una respuesta en este momento. Intenta reformular tu pregunta.";
            }
            return contenido.asText().trim();

        } catch (RestClientResponseException ex) {
            // Error devuelto por Gemini (401/403 clave invalida, 429 limite de la capa gratuita, etc.)
            log.error("Asistente IA: Gemini respondio {} - {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            if (ex.getStatusCode().value() == 429) {
                return "El asistente alcanzó el límite de solicitudes de la capa gratuita. Intenta de nuevo en un minuto.";
            }
            return "No pude conectarme al asistente en este momento. Intenta de nuevo en unos segundos.";
        } catch (Exception ex) {
            log.error("Asistente IA: error inesperado al consumir Gemini", ex);
            return "Ocurrió un error inesperado al conectar con el asistente. Intenta de nuevo.";
        }
    }

    /** Contexto minimo: quien es, su rol y el alcance de lo que puede/no puede saber. */
    private String promptSistema(UsuarioSesion sesion) {
        String nombre = sesion != null ? sesion.getNombreCompleto() : "un usuario";
        String rolEtiqueta = sesion != null ? Roles.etiqueta(sesion.getRolEfectivo()) : "desconocido";
        return "Eres el Asistente IA de SkillBridge AI, una plataforma de gestion de talento y proyectos para " +
                "equipos de practicantes. Le hablas a " + nombre + ", cuyo rol en la plataforma es " + rolEtiqueta + ". " +
                "Responde siempre en español, de forma breve, clara y profesional (maximo 5 lineas salvo que te pidan mas detalle). " +
                "Puedes explicar como usar la plataforma (proyectos, asignaciones, habilidades, foros, reportes) y dar " +
                "consejos generales de gestion de proyectos y desarrollo de software. " +
                "No inventes datos especificos del usuario (numeros de proyectos, nombres de companeros, cifras) que no " +
                "te hayan sido proporcionados en este mensaje: si te preguntan algo asi, indica honestamente que no tienes " +
                "acceso a esa informacion en tiempo real todavia.";
    }
}
