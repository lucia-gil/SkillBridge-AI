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
 * API de Mistral (chat completions) y devuelve la respuesta en texto plano.
 *
 * Mismo patron de "consumo de servicio REST" visto en clase (verifica.id /
 * api.atenea): POST + cabecera "Authorization: Bearer {API-KEY}" + cuerpo y
 * respuesta en JSON. Aqui el "servicio externo" es Mistral en vez de un
 * verificador de DNI/RUC.
 *
 * No hay historial guardado en base de datos todavia (cada mensaje se
 * manda solo con el system prompt, sin memoria de turnos anteriores) - ver
 * README/documentacion de esta funcionalidad para el detalle de que falta.
 */
@Service
public class AsistenteIaService {

    private static final Logger log = LoggerFactory.getLogger(AsistenteIaService.class);
    private static final String MISTRAL_URL = "https://api.mistral.ai/v1/chat/completions";
    private static final String MODELO = "mistral-small-latest";

    private final String apiKey;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public AsistenteIaService(@Value("${mistral.api.key:}") String apiKey, ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.create();
    }

    /** true si hay una clave configurada (secretos.properties local o variable de entorno MISTRAL_API_KEY). */
    public boolean configurado() {
        return apiKey != null && !apiKey.isBlank();
    }

    public String responder(UsuarioSesion sesion, String mensajeUsuario) {
        if (!configurado()) {
            log.warn("Asistente IA: no hay mistral.api.key configurada (ver secretos.properties.example)");
            return "El Asistente IA todavía no está configurado en este servidor. Pídele a un administrador que agregue la clave de Mistral.";
        }

        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("model", MODELO);
        cuerpo.put("messages", List.of(
                Map.of("role", "system", "content", promptSistema(sesion)),
                Map.of("role", "user", "content", mensajeUsuario)
        ));
        cuerpo.put("temperature", 0.4);
        cuerpo.put("max_tokens", 500);

        try {
            String respuestaCruda = restClient.post()
                    .uri(MISTRAL_URL)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .body(cuerpo)
                    .retrieve()
                    .body(String.class);

            JsonNode raiz = objectMapper.readTree(respuestaCruda);
            JsonNode contenido = raiz.path("choices").path(0).path("message").path("content");
            if (contenido.isMissingNode() || contenido.asText().isBlank()) {
                log.warn("Asistente IA: respuesta de Mistral sin contenido util: {}", respuestaCruda);
                return "No pude generar una respuesta en este momento. Intenta reformular tu pregunta.";
            }
            return contenido.asText().trim();

        } catch (RestClientResponseException ex) {
            // Error devuelto por Mistral (401 clave invalida, 429 limite de la capa gratuita, etc.)
            log.error("Asistente IA: Mistral respondio {} - {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            if (ex.getStatusCode().value() == 429) {
                return "El asistente alcanzó el límite de solicitudes de la capa gratuita. Intenta de nuevo en un minuto.";
            }
            return "No pude conectarme al asistente en este momento. Intenta de nuevo en unos segundos.";
        } catch (Exception ex) {
            log.error("Asistente IA: error inesperado al consumir Mistral", ex);
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
