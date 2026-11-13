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
 * API de Claude (Anthropic Messages API, /v1/messages) y devuelve la
 * respuesta en texto plano.
 *
 * Mismo patron de "consumo de servicio REST" visto en clase (verifica.id /
 * api.atenea): POST + clave de API + cuerpo y respuesta en JSON. Aqui el
 * "servicio externo" es Claude en vez de un verificador de DNI/RUC. La
 * clave viaja en la cabecera "x-api-key" (no "Authorization: Bearer"), y
 * ademas hay que mandar "anthropic-version" con la version de la API.
 *
 * Antes la plataforma usaba Gemini (capa gratuita); se migro a Claude
 * (Anthropic) porque el equipo compro creditos de la API de Anthropic
 * directamente. Se conserva la misma arquitectura de cadena de proveedores
 * (varios modelos en orden + un respaldo opcional compatible con la API de
 * OpenAI) para no depender de un solo proveedor si falla.
 *
 * No hay historial guardado en base de datos todavia (cada mensaje se
 * manda solo con el system prompt, sin memoria de turnos anteriores) - ver
 * ASISTENTE_IA.md para el detalle de que falta.
 */
@Service
public class AsistenteIaService {

    private static final Logger log = LoggerFactory.getLogger(AsistenteIaService.class);
    /** Version de la API de Claude (cabecera "anthropic-version"), la que muestra la Consola de Anthropic. */
    private static final String ANTHROPIC_VERSION = "2026-10-21";
    /** Pausas entre reintentos ante errores pasajeros (500/502/503/504/529 o timeout). */
    private static final long[] ESPERAS_MS = {1000, 2500};

    private final String apiKey;
    private final List<String> modelos;
    private final String respaldoUrl;
    private final String respaldoKey;
    private final String respaldoModelo;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    /** Mismo cliente pero con más tiempo de espera: leer varios PDF completos tarda más que un mensaje de chat. */
    private final RestClient restClientDocumentos;
    private final String claudeUrl;

    public AsistenteIaService(@Value("${anthropic.api.key:}") String apiKey,
                              @Value("${claude.modelos:claude-sonnet-5}") String modelos,
                              @Value("${ia.respaldo.url:https://api.groq.com/openai/v1/chat/completions}") String respaldoUrl,
                              @Value("${ia.respaldo.api-key:}") String respaldoKey,
                              @Value("${ia.respaldo.modelo:llama-3.3-70b-versatile}") String respaldoModelo,
                              @Value("${anthropic.api.url:https://api.anthropic.com/v1/messages}") String claudeUrl,
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
        SimpleClientHttpRequestFactory httpDocs = new SimpleClientHttpRequestFactory();
        httpDocs.setConnectTimeout(Duration.ofSeconds(10));
        httpDocs.setReadTimeout(Duration.ofSeconds(150));
        this.restClientDocumentos = RestClient.builder().requestFactory(httpDocs).build();
        this.claudeUrl = claudeUrl;
    }

    /** true si hay una clave configurada (secretos.properties local o variable de entorno ANTHROPIC_API_KEY). */
    public boolean configurado() {
        return tieneClaude() || tieneRespaldo();
    }

    private boolean tieneClaude() {
        return apiKey != null && !apiKey.isBlank();
    }

    private boolean tieneRespaldo() {
        return respaldoKey != null && !respaldoKey.isBlank() && respaldoUrl != null && !respaldoUrl.isBlank();
    }

    /** Resultado de una llamada a Claude: el texto y si es una respuesta real (ok) o un aviso de error. */
    public record Resultado(String texto, boolean ok) {}

    /** Un turno de la conversación: deUsuario=true si lo escribió la persona, false si fue el asistente. */
    public record Turno(boolean deUsuario, String texto, List<Archivo> archivos) {
        public Turno(boolean deUsuario, String texto) { this(deUsuario, texto, List.of()); }
    }

    /** Imagen o PDF que viaja junto a un turno (Claude los lee como bloques "image"/"document" en base64). */
    public record Archivo(String nombre, String mime, byte[] datos) {}

    /**
     * Chatbot con memoria (AsistenteConversacionService): prompt de sistema
     * con el contexto real del usuario + los turnos anteriores + el mensaje nuevo.
     */
    public Resultado conversar(String promptSistema, List<Turno> turnos, int maxOutputTokens) {
        return llamar(promptSistema, turnos, maxOutputTokens);
    }

    /** Chat del asistente: si falla, devuelve igual un aviso amable para mostrar en la conversación. */
    public String responder(UsuarioSesion sesion, String mensajeUsuario) {
        return llamarSimple(promptSistema(sesion), mensajeUsuario, 800).texto();
    }

    /**
     * Llamada genérica para otras funciones con IA (AI Talent Matching,
     * extracción de proyectos con IA): devuelve el texto y si es una
     * respuesta real, sin lanzar excepción, para que quien llama decida su
     * plan B.
     */
    public Resultado consultar(String promptSistema, String mensaje, int maxOutputTokens) {
        return llamarSimple(promptSistema, mensaje, maxOutputTokens);
    }

    /**
     * Igual que consultar(...), pero adjuntando documentos (PDF) que Claude
     * lee completos, y con un tiempo de espera mayor. Lo usa el Resumen
     * profesional con IA del colaborador (ResumenProfesionalIaService).
     */
    public Resultado consultarConDocumentos(String promptSistema, String mensaje, List<Archivo> archivos, int maxOutputTokens) {
        return llamar(promptSistema, List.of(new Turno(true, mensaje, archivos)), maxOutputTokens, true);
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
     * Claude cada vez que alguien abre el hilo.
     */
    public String resumirHilo(String tituloHilo, String contenidoCompleto) {
        String prompt = "Eres el Asistente IA de SkillBridge AI. Te paso el contenido completo de un hilo del foro " +
                "de un proyecto (titulo \"" + tituloHilo + "\", seguido del mensaje inicial y las respuestas en orden). " +
                "Responde siempre en español, en un maximo de 4-5 lineas, con un resumen util para alguien que no leyo " +
                "el hilo: de que trata, que se acordo o decidio (si algo quedo claro), y que queda pendiente o sin responder " +
                "(si aplica). No inventes nada que no este en el texto. No agregues saludos ni frases de relleno, ve directo " +
                "al resumen.";
        return exigirRespuesta(llamarSimple(prompt, contenidoCompleto, 600));
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
        return exigirRespuesta(llamarSimple(prompt, conversacion, 600));
    }

    /**
     * Cadena de proveedores: prueba cada modelo de Claude (claude.modelos, en
     * orden) y, si todos fallan, el proveedor de respaldo compatible con la
     * API de OpenAI (Groq, OpenRouter, Mistral...; ia.respaldo.*), si tiene
     * clave. Ante un error pasajero (500/502/503/504/529 o timeout) reintenta
     * el mismo modelo con una pausa; ante 429 (límite de la cuenta) o 404
     * pasa directo al siguiente.
     */
    private Resultado llamarSimple(String promptSistema, String mensajeUsuario, int maxOutputTokens) {
        return llamar(promptSistema, List.of(new Turno(true, mensajeUsuario)), maxOutputTokens);
    }

    private Resultado llamar(String promptSistema, List<Turno> turnos, int maxOutputTokens) {
        return llamar(promptSistema, turnos, maxOutputTokens, false);
    }

    private Resultado llamar(String promptSistema, List<Turno> turnos, int maxOutputTokens, boolean conDocumentos) {
        RestClient cliente = conDocumentos ? restClientDocumentos : restClient;
        if (!configurado()) {
            log.warn("Asistente IA: no hay anthropic.api.key ni ia.respaldo.api-key configuradas (ver secretos.properties.example)");
            return new Resultado("El Asistente IA todavía no está configurado en este servidor. Pídele a un administrador que agregue la clave de Claude.", false);
        }

        boolean huboLimite = false;
        if (tieneClaude()) {
            for (String modelo : modelos) {
                Intento r = conReintentos("Claude " + modelo, () -> pedirClaude(cliente, modelo, promptSistema, turnos, maxOutputTokens));
                if (r.texto() != null) return new Resultado(r.texto(), true);
                huboLimite |= r.limite();
            }
        }
        if (tieneRespaldo()) {
            Intento r = conReintentos("respaldo " + respaldoModelo, () -> pedirRespaldo(promptSistema, turnos, maxOutputTokens));
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
                // 529 = "overloaded_error" propio de Claude (sobrecarga pasajera del modelo).
                boolean pasajero = status == 500 || status == 502 || status == 503 || status == 504 || status == 529;
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

    /** POST a la API de Claude (Anthropic Messages API): https://docs.claude.com/en/api/messages */
    private String pedirClaude(RestClient cliente, String modelo, String promptSistema, List<Turno> turnos, int maxOutputTokens) throws Exception {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("model", modelo);
        cuerpo.put("max_tokens", maxOutputTokens);
        cuerpo.put("system", promptSistema);
        // Sin "temperature": claude-sonnet-5 rechaza ese campo con 400
        // ("Extra inputs are not permitted") cuando el razonamiento extendido
        // esta activo por defecto, asi que se deja la temperatura por defecto del modelo.
        // Claude: roles "user" y "assistant" (la memoria va como turnos previos).
        cuerpo.put("messages", turnos.stream().map(t -> {
            List<Map<String, Object>> bloques = new java.util.ArrayList<>();
            bloques.add(Map.of("type", "text", "text", t.texto()));
            for (Archivo a : t.archivos()) {
                String mime = a.mime() == null ? "" : a.mime();
                String datos = java.util.Base64.getEncoder().encodeToString(a.datos());
                if (mime.startsWith("image/")) {
                    bloques.add(Map.of("type", "image", "source",
                            Map.of("type", "base64", "media_type", mime, "data", datos)));
                } else if (mime.equals("application/pdf")) {
                    bloques.add(Map.of("type", "document", "source",
                            Map.of("type", "base64", "media_type", mime, "data", datos)));
                } else {
                    bloques.add(Map.of("type", "text", "text", "[El usuario adjuntó \"" + a.nombre() + "\", un archivo que no se puede mostrar directamente; avísalo con amabilidad.]"));
                }
            }
            return Map.of("role", t.deUsuario() ? "user" : "assistant", "content", bloques);
        }).toList());

        String respuestaCruda = cliente.post()
                .uri(claudeUrl)
                .header("x-api-key", apiKey)
                .header("anthropic-version", ANTHROPIC_VERSION)
                .header("Content-Type", "application/json")
                .body(cuerpo)
                .retrieve()
                .body(String.class);

        JsonNode bloques = objectMapper.readTree(respuestaCruda).path("content");
        StringBuilder texto = new StringBuilder();
        for (JsonNode bloque : bloques) {
            if ("text".equals(bloque.path("type").asText(""))) texto.append(bloque.path("text").asText(""));
        }
        return texto.toString();
    }

    /** Proveedor de respaldo con la API de OpenAI (POST /chat/completions, cabecera Authorization: Bearer). */
    private String pedirRespaldo(String promptSistema, List<Turno> turnos, int maxOutputTokens) throws Exception {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("model", respaldoModelo);
        List<Map<String, String>> mensajes = new java.util.ArrayList<>();
        mensajes.add(Map.of("role", "system", "content", promptSistema));
        // El respaldo (API de OpenAI) solo recibe texto: se le avisa qué se adjuntó.
        turnos.forEach(t -> mensajes.add(Map.of("role", t.deUsuario() ? "user" : "assistant", "content",
                t.archivos().isEmpty() ? t.texto() : t.texto() + "\n\n[El usuario adjuntó " + t.archivos().stream().map(Archivo::nombre)
                        .collect(java.util.stream.Collectors.joining(", ")) + ", pero este proveedor no puede ver imágenes ni PDF: "
                        + "dilo con amabilidad y responde con lo que puedas.]")));
        cuerpo.put("messages", mensajes);
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
