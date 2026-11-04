package com.skillbridge.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.util.Roles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.Arrays;
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
    private static final String GEMINI_BASE = "https://generativelanguage.googleapis.com/v1beta/models/";
    /** Pausas entre reintentos ante errores pasajeros (500/502/503/504 o timeout). */
    private static final long[] ESPERAS_MS = {1000, 2500};

    private final String apiKey;
    private final List<String> modelos;
    private final String respaldoUrl;
    private final String respaldoKey;
    private final String respaldoModelo;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public AsistenteIaService(@Value("${gemini.api.key:}") String apiKey,
                              @Value("${gemini.modelos:gemini-3.8-flash,gemini-flash-latest,gemini-flash-lite-latest}") String modelos,
                              @Value("${ia.respaldo.url:https://api.groq.com/openai/v1/chat/completions}") String respaldoUrl,
                              @Value("${ia.respaldo.api-key:}") String respaldoKey,
                              @Value("${ia.respaldo.modelo:llama-3.3-70b-versatile}") String respaldoModelo,
                              ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        this.modelos = Arrays.stream(modelos.split(",")).map(String::trim).filter(m -> !m.isEmpty()).distinct().toList();
        this.respaldoUrl = respaldoUrl;
        this.respaldoKey = respaldoKey;
        this.respaldoModelo = respaldoModelo;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory http = new SimpleClientHttpRequestFactory();
        http.setConnectTimeout(Duration.ofSeconds(10));
        http.setReadTimeout(Duration.ofSeconds(40));
        this.restClient = RestClient.builder().requestFactory(http).build();
    }

    /** true si hay una clave configurada (secretos.properties local o variable de entorno GEMINI_API_KEY). */
    public boolean configurado() {
        return tieneGemini() || tieneRespaldo();
    }

    private boolean tieneGemini() {
        return apiKey != null && !apiKey.isBlank();
    }

    private boolean tieneRespaldo() {
        return respaldoKey != null && !respaldoKey.isBlank() && respaldoUrl != null && !respaldoUrl.isBlank();
    }

    /** Resultado de una llamada a Gemini: el texto y si es una respuesta real (ok) o un aviso de error. */
    public record Resultado(String texto, boolean ok) {}

    /** Chat del asistente: si falla, devuelve igual un aviso amable para mostrar en la conversación. */
    public String responder(UsuarioSesion sesion, String mensajeUsuario) {
        return llamarGemini(promptSistema(sesion), mensajeUsuario, 800).texto();
    }

    /**
     * Llamada genérica para otras funciones con IA (AI Talent Matching):
     * devuelve el texto y si es una respuesta real, sin lanzar excepción,
     * para que quien llama decida su plan B.
     */
    public Resultado consultar(String promptSistema, String mensaje, int maxOutputTokens) {
        return llamarGemini(promptSistema, mensaje, maxOutputTokens);
    }

    /**
     * Para lo que se GUARDA (resúmenes): si la IA no está disponible lanza
     * OperacionInvalidaException con el aviso, así nunca se persiste un
     * mensaje de error como si fuera un resumen.
     */
    private String exigirRespuesta(Resultado r) {
        if (!r.ok()) throw new com.skillbridge.ai.util.OperacionInvalidaException(r.texto());
        return r.texto();
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
        return exigirRespuesta(llamarGemini(prompt, contenidoCompleto, 600));
    }

    /**
     * Resume la conversación reciente del chat de un proyecto (ChatService).
     * No se guarda: se muestra al momento. Si la IA no responde, lanza el aviso.
     */
    public String resumirConversacion(String proyecto, String conversacion) {
        String prompt = "Eres el Asistente IA de SkillBridge AI. Te paso los últimos mensajes del chat del proyecto \"" + proyecto + "\" "
                + "(cada línea: fecha y hora, autor y mensaje). Responde en español, en 3 a 5 viñetas cortas que empiecen con \"- \": "
                + "los temas principales, lo que se decidió o quedó acordado y lo que sigue pendiente (con responsable si aparece). "
                + "No inventes nada que no esté en los mensajes. Sin saludos ni frases de relleno.";
        return exigirRespuesta(llamarGemini(prompt, conversacion, 600));
    }

    /**
     * Cadena de proveedores: prueba cada modelo de Gemini (gemini.modelos, en
     * orden) y, si todos fallan, el proveedor de respaldo compatible con la
     * API de OpenAI (Groq, OpenRouter, Mistral...; ia.respaldo.*), si tiene
     * clave. Ante un error pasajero (500/502/503/504 o timeout) reintenta el
     * mismo modelo con una pausa; ante 429 (cuota de la capa gratuita, que
     * en Gemini es por modelo) o 404 pasa directo al siguiente.
     */
    private Resultado llamarGemini(String promptSistema, String mensajeUsuario, int maxOutputTokens) {
        if (!configurado()) {
            log.warn("Asistente IA: no hay gemini.api.key ni ia.respaldo.api-key configuradas (ver secretos.properties.example)");
            return new Resultado("El Asistente IA todavía no está configurado en este servidor. Pídele a un administrador que agregue la clave de Gemini.", false);
        }

        boolean huboLimite = false;
        if (tieneGemini()) {
            for (String modelo : modelos) {
                Intento r = conReintentos("Gemini " + modelo, () -> pedirGemini(modelo, promptSistema, mensajeUsuario, maxOutputTokens));
                if (r.texto() != null) return new Resultado(r.texto(), true);
                huboLimite |= r.limite();
            }
        }
        if (tieneRespaldo()) {
            Intento r = conReintentos("respaldo " + respaldoModelo, () -> pedirRespaldo(promptSistema, mensajeUsuario, maxOutputTokens));
            if (r.texto() != null) return new Resultado(r.texto(), true);
            huboLimite |= r.limite();
        }
        return new Resultado(huboLimite
                ? "El asistente alcanzó el límite de solicitudes de la capa gratuita. Intenta de nuevo en un minuto."
                : "No pude conectarme al asistente en este momento. Intenta de nuevo en unos segundos.", false);
    }

    /** Resultado de un proveedor: el texto (null si falló) y si el fallo fue por límite de cuota. */
    private record Intento(String texto, boolean limite) {}

    @FunctionalInterface
    private interface Peticion { String enviar() throws Exception; }

    private Intento conReintentos(String proveedor, Peticion peticion) {
        for (int i = 0; ; i++) {
            try {
                String texto = peticion.enviar();
                if (texto != null && !texto.isBlank()) {
                    if (i > 0) log.info("Asistente IA: {} respondio al reintento {}", proveedor, i);
                    return new Intento(texto.trim(), false);
                }
                log.warn("Asistente IA: {} devolvio una respuesta vacia", proveedor);
                return new Intento(null, false);
            } catch (RestClientResponseException ex) {
                int status = ex.getStatusCode().value();
                log.warn("Asistente IA: {} respondio {} - {}", proveedor, status, recortar(ex.getResponseBodyAsString()));
                if (status == 429) return new Intento(null, true);
                boolean pasajero = status == 500 || status == 502 || status == 503 || status == 504;
                if (!pasajero || i >= ESPERAS_MS.length) return new Intento(null, false);
            } catch (ResourceAccessException ex) {
                log.warn("Asistente IA: {} sin conexion o sin respuesta a tiempo: {}", proveedor, ex.getMessage());
                if (i >= ESPERAS_MS.length) return new Intento(null, false);
            } catch (Exception ex) {
                log.error("Asistente IA: error inesperado con " + proveedor, ex);
                return new Intento(null, false);
            }
            try {
                Thread.sleep(ESPERAS_MS[i]);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return new Intento(null, false);
            }
        }
    }

    private String pedirGemini(String modelo, String promptSistema, String mensajeUsuario, int maxOutputTokens) throws Exception {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("systemInstruction", Map.of(
                "parts", List.of(Map.of("text", promptSistema))
        ));
        cuerpo.put("contents", List.of(
                Map.of("role", "user", "parts", List.of(Map.of("text", mensajeUsuario)))
        ));
        // "thinkingLevel": "LOW" porque son tareas simples (responder una
        // pregunta corta o resumir un hilo), no razonamiento complejo - sin
        // esto, el modelo gasta buena parte de maxOutputTokens "pensando"
        // antes de escribir la respuesta visible y esta sale cortada.
        cuerpo.put("generationConfig", Map.of(
                "temperature", 0.4,
                "maxOutputTokens", maxOutputTokens,
                "thinkingConfig", Map.of("thinkingLevel", "LOW")
        ));

        String respuestaCruda = restClient.post()
                .uri(GEMINI_BASE + modelo + ":generateContent")
                .header("x-goog-api-key", apiKey)
                .header("Content-Type", "application/json")
                .body(cuerpo)
                .retrieve()
                .body(String.class);

        JsonNode partes = objectMapper.readTree(respuestaCruda).path("candidates").path(0).path("content").path("parts");
        StringBuilder texto = new StringBuilder();
        for (JsonNode parte : partes) {
            if (!parte.path("thought").asBoolean(false)) texto.append(parte.path("text").asText(""));
        }
        return texto.toString();
    }

    /** Proveedor de respaldo con la API de OpenAI (POST /chat/completions, cabecera Authorization: Bearer). */
    private String pedirRespaldo(String promptSistema, String mensajeUsuario, int maxOutputTokens) throws Exception {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("model", respaldoModelo);
        cuerpo.put("messages", List.of(
                Map.of("role", "system", "content", promptSistema),
                Map.of("role", "user", "content", mensajeUsuario)
        ));
        cuerpo.put("temperature", 0.4);
        cuerpo.put("max_tokens", maxOutputTokens);

        String respuestaCruda = restClient.post()
                .uri(respaldoUrl)
                .header("Authorization", "Bearer " + respaldoKey)
                .header("Content-Type", "application/json")
                .body(cuerpo)
                .retrieve()
                .body(String.class);
        return objectMapper.readTree(respuestaCruda).path("choices").path(0).path("message").path("content").asText("");
    }

    private static String recortar(String s) {
        if (s == null) return "";
        s = s.replaceAll("\\s+", " ");
        return s.length() > 300 ? s.substring(0, 300) + "..." : s;
    }

    /** Contexto minimo: quien es, su rol y el alcance de lo que puede/no puede saber. */
    private String promptSistema(UsuarioSesion sesion) {
        String nombre = sesion != null ? sesion.getNombreCompleto() : "un usuario";
        String rolEtiqueta = sesion != null ? Roles.etiqueta(sesion.getRolEfectivo()) : "desconocido";
        return "Eres el Asistente IA de SkillBridge AI, una plataforma de gestion de talento y proyectos para " +
                "equipos de practicantes. Le hablas a " + nombre + ", cuyo rol en la plataforma es " + rolEtiqueta + ". " +
                "Responde siempre en español, de forma breve, clara y profesional (maximo 5 lineas salvo que te pidan mas detalle). " +
                "\n\n" +
                "ALCANCE: solo respondes temas relacionados con SkillBridge AI y el trabajo del usuario en la plataforma: " +
                "como usar sus funciones (proyectos, asignaciones, habilidades, foros, reportes, AI Talent Matching), " +
                "y consejos de gestion de proyectos o desarrollo de software aplicados a su rol. " +
                "Si te preguntan algo que no tiene relacion con eso (cultura general, matematicas, geografia, noticias, " +
                "trivia, o cualquier otro tema fuera de la plataforma y la gestion de proyectos/software), NO lo respondas: " +
                "indica brevemente y con amabilidad que solo puedes ayudar con temas de SkillBridge AI y gestion de " +
                "proyectos, y pregunta en que puedes ayudar dentro de ese alcance. " +
                "No inventes datos especificos del usuario (numeros de proyectos, nombres de companeros, cifras) que no " +
                "te hayan sido proporcionados en este mensaje: si te preguntan algo asi, indica honestamente que no tienes " +
                "acceso a esa informacion en tiempo real todavia.";
    }
}
