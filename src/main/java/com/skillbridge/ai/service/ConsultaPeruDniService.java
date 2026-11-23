package com.skillbridge.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillbridge.ai.util.OperacionInvalidaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.text.Normalizer;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class ConsultaPeruDniService {

    static final String ENDPOINT = "https://api.consultaperuapi.com/api/v1/consultas-dni";
    private static final Logger LOG = LoggerFactory.getLogger(ConsultaPeruDniService.class);
    private static final Pattern FORMATO_DNI = Pattern.compile("[0-9]{8}");
    public static final String NO_ENCONTRADO = "No se encuentra en nuestros registros";
    public static final String NO_DISPONIBLE = "La consulta de DNI no está disponible en este momento.";
    public static final String LIMITE = "El servicio de DNI alcanzó su límite de consultas. Inténtalo más tarde.";

    public record Persona(String nombres, String apellidos) {}

    public static final class ConsultaNoDisponible extends OperacionInvalidaException {
        private final boolean limite;

        ConsultaNoDisponible(boolean limite) {
            super(limite ? LIMITE : NO_DISPONIBLE);
            this.limite = limite;
        }

        public boolean limite() { return limite; }
    }

    private final RestClient cliente;
    private final String token;
    private final ObjectMapper json;

    @Autowired
    public ConsultaPeruDniService(@Value("${app.registro.consulta-peru-api-key:}") String token, ObjectMapper json) {
        this(token, json, crearCliente());
    }

    ConsultaPeruDniService(String token, ObjectMapper json, RestClient cliente) {
        this.cliente = cliente;
        this.token = token.strip();
        this.json = json;
    }

    private static RestClient crearCliente() {
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        var solicitudes = new JdkClientHttpRequestFactory(http);
        solicitudes.setReadTimeout(Duration.ofSeconds(4));
        return RestClient.builder().requestFactory(solicitudes).build();
    }

    public boolean formatoValido(String dni) {
        return dni != null && FORMATO_DNI.matcher(dni).matches();
    }

    public boolean disponible() {
        return !token.isBlank();
    }

    public Optional<Persona> buscar(String dni) {
        if (!disponible()) throw new ConsultaNoDisponible(false);
        if (!formatoValido(dni)) return Optional.empty();
        try {
            return cliente.post().uri(ENDPOINT).contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                    .body(Map.of("token", token, "dni", dni))
                    .exchange((peticion, respuesta) -> {
                        int estado = respuesta.getStatusCode().value();
                        if (estado == 429) throw new ConsultaNoDisponible(true);
                        if (estado != 200 && estado != 404 && estado != 422) {
                            LOG.warn("Consulta Perú respondió con HTTP {}.", estado);
                            throw new ConsultaNoDisponible(false);
                        }
                        // Solo se leen los campos de identidad necesarios. No se registra la respuesta del proveedor.
                        byte[] contenido = respuesta.getBody().readNBytes(32_769);
                        if (contenido.length > 32_768) throw new ConsultaNoDisponible(false);
                        JsonNode cuerpo = json.readTree(contenido);
                        if (cuerpo == null || !cuerpo.isObject()) throw new ConsultaNoDisponible(false);
                        if (estado == 200 && cuerpo.path("success").asBoolean(false)) {
                            return Optional.of(extraerPersona(cuerpo.path("data"), dni));
                        }
                        if (noEncontrado(cuerpo)) return Optional.empty();
                        throw new ConsultaNoDisponible(false);
                    });
        } catch (RestClientException ex) {
            LOG.warn("Falló la comunicación con Consulta Perú.");
            throw new ConsultaNoDisponible(false);
        }
    }

    private Persona extraerPersona(JsonNode datos, String dni) {
        String documento = datos.path("dni").asText();
        String nombres = campo(datos, "nombres");
        String paterno = campo(datos, "apellidoPaterno");
        String materno = campo(datos, "apellidoMaterno");
        if (materno.isEmpty()) materno = completarApellido(datos, nombres, paterno);
        String apellidos = (paterno + " " + materno).strip();
        if (!dni.equals(documento) || nombres.isEmpty() || apellidos.isEmpty()
                || nombres.length() + apellidos.length() + 1 > 150) {
            throw new ConsultaNoDisponible(false);
        }
        return new Persona(nombres, apellidos);
    }

    private String completarApellido(JsonNode datos, String nombres, String paterno) {
        if (nombres.isEmpty() || paterno.isEmpty()) return "";
        // El ejemplo del proveedor omite apellidoMaterno, pero incluye «apellidos nombres».
        String completo = campo(datos, "nombreCompleto");
        String prefijo = paterno + " ";
        String sufijo = " " + nombres;
        int fin = completo.length() - sufijo.length();
        if (fin <= prefijo.length() || !completo.regionMatches(true, 0, prefijo, 0, prefijo.length())
                || !completo.regionMatches(true, fin, sufijo, 0, sufijo.length())) return "";
        return completo.substring(prefijo.length(), fin).strip();
    }

    private String campo(JsonNode datos, String nombre) {
        JsonNode campo = datos.path(nombre);
        return campo.isTextual() ? campo.textValue().strip().replaceAll("\\s+", " ") : "";
    }

    private boolean noEncontrado(JsonNode respuesta) {
        String codigo = respuesta.path("code").asText().toUpperCase(Locale.ROOT);
        if ("DNI_NOT_FOUND".equals(codigo) || "DNI_NO_ENCONTRADO".equals(codigo)) return true;
        String mensaje = respuesta.path("message").asText(respuesta.path("error").asText(""));
        mensaje = Normalizer.normalize(mensaje, Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT);
        return (mensaje.contains("dni") || mensaje.contains("persona") || mensaje.contains("documento"))
                && (mensaje.contains("no encontrado") || mensaje.contains("no se encontro")
                || mensaje.contains("no existe") || mensaje.contains("no se encuentra"));
    }
}
