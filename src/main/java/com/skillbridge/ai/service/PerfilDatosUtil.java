package com.skillbridge.ai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillbridge.ai.util.OperacionInvalidaException;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Reglas de los datos de contacto y presentación del perfil (v9): enlaces,
 * teléfono, ubicación y etiquetas. Las usa CuentaService al guardar y
 * PerfilVistaService al mostrar, para que ambos lados apliquen lo mismo.
 */
public final class PerfilDatosUtil {

    public static final int MAX_ETIQUETAS = 10;
    public static final int MAX_LARGO_ETIQUETA = 30;
    public static final int MAX_LARGO_UBICACION = 120;
    public static final int MAX_LARGO_URL = 500;

    private static final Pattern TELEFONO = Pattern.compile("^\\+?[0-9 ()\\-]{6,20}$");
    private static final ObjectMapper JSON = new ObjectMapper();

    private PerfilDatosUtil() {
    }

    /**
     * Normaliza un enlace escrito por el usuario. Vacío = null. Sin esquema se
     * asume https://. Solo se aceptan http/https: así un "javascript:..."
     * nunca llega a un th:href.
     */
    public static String normalizarUrl(String valor, String campo) {
        if (valor == null || valor.isBlank()) return null;
        String url = valor.trim();
        if (!url.matches("(?i)^https?://.*")) {
            if (url.contains(":") && !url.matches("^[^/]+:\\d+.*")) {
                throw new OperacionInvalidaException("El enlace de " + campo + " debe empezar con https://");
            }
            url = "https://" + url;
        }
        if (url.length() > MAX_LARGO_URL) {
            throw new OperacionInvalidaException("El enlace de " + campo + " es demasiado largo.");
        }
        try {
            URI uri = URI.create(url);
            if (uri.getHost() == null || !uri.getHost().contains(".")) throw new IllegalArgumentException();
        } catch (Exception e) {
            throw new OperacionInvalidaException("El enlace de " + campo + " no parece una dirección web válida.");
        }
        return url;
    }

    /** Para mostrar: devuelve el enlace solo si es http/https (datos antiguos sin validar quedan ocultos). */
    public static String urlSegura(String valor) {
        if (valor == null) return null;
        String url = valor.trim();
        return url.matches("(?i)^https?://[^\\s\"'<>]+$") ? url : null;
    }

    /** "https://www.linkedin.com/in/ana/" → "linkedin.com/in/ana" (texto corto para el enlace). */
    public static String urlLegible(String url) {
        if (url == null) return null;
        String s = url.replaceFirst("(?i)^https?://", "").replaceFirst("^www\\.", "");
        if (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s.length() > 42 ? s.substring(0, 40) + "…" : s;
    }

    public static String normalizarTelefono(String valor) {
        if (valor == null || valor.isBlank()) return null;
        String t = valor.trim().replaceAll("\\s+", " ");
        if (!TELEFONO.matcher(t).matches()) {
            throw new OperacionInvalidaException("El teléfono solo puede tener números, espacios, guiones, paréntesis y un + al inicio.");
        }
        return t;
    }

    public static String normalizarUbicacion(String valor) {
        if (valor == null || valor.isBlank()) return null;
        String u = valor.trim().replaceAll("\\s+", " ");
        if (u.length() > MAX_LARGO_UBICACION) {
            throw new OperacionInvalidaException("La ubicación no puede superar los " + MAX_LARGO_UBICACION + " caracteres.");
        }
        return u;
    }

    /**
     * Etiquetas escritas separadas por coma → lista limpia (sin vacías ni
     * repetidas, sin distinguir mayúsculas), con los límites de cantidad y largo.
     */
    public static List<String> normalizarEtiquetas(String texto) {
        List<String> salida = new ArrayList<>();
        if (texto == null || texto.isBlank()) return salida;
        Map<String, String> unicas = new LinkedHashMap<>();
        for (String parte : texto.split("[,\\n]")) {
            String e = parte.trim().replaceAll("\\s+", " ").replaceFirst("^#", "");
            if (e.isEmpty()) continue;
            if (e.length() > MAX_LARGO_ETIQUETA) {
                throw new OperacionInvalidaException("Cada etiqueta puede tener hasta " + MAX_LARGO_ETIQUETA + " caracteres (\"" + e + "\" es más larga).");
            }
            unicas.putIfAbsent(e.toLowerCase(Locale.ROOT), e);
        }
        if (unicas.size() > MAX_ETIQUETAS) {
            throw new OperacionInvalidaException("Puedes agregar hasta " + MAX_ETIQUETAS + " etiquetas.");
        }
        salida.addAll(unicas.values());
        return salida;
    }

    public static List<String> leerEtiquetas(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            List<String> lista = JSON.readValue(json, new TypeReference<List<String>>() {
            });
            return lista == null ? List.of() : lista.stream().filter(s -> s != null && !s.isBlank()).toList();
        } catch (Exception e) {
            return List.of();
        }
    }

    public static String escribirEtiquetas(List<String> etiquetas) {
        if (etiquetas == null || etiquetas.isEmpty()) return null;
        try {
            return JSON.writeValueAsString(etiquetas);
        } catch (Exception e) {
            return null;
        }
    }
}
