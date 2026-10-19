package com.skillbridge.ai.util;

/**
 * Mapea el nombre libre de una tecnologia (texto que el PM escribe al crear
 * el proyecto, ej. "Java", "React", "MySQL") a un icono generico ya existente
 * en icons.svg. No son logos reales de cada marca (no se descargan iconos
 * externos), son iconos por categoria (base de datos, frontend, movil,
 * infraestructura, API) para dar una pista visual rapida en el detalle del
 * proyecto.
 */
public final class TecnologiaIconUtil {

    private TecnologiaIconUtil() {
    }

    public static String icono(String tecnologia) {
        if (tecnologia == null) return "icon-cpu";
        String t = tecnologia.toLowerCase();
        if (t.contains("sql") || t.contains("mongo") || t.contains("oracle") || t.contains("redis")
                || t.contains("database") || t.contains("base de datos")) {
            return "icon-database";
        }
        if (t.contains("docker") || t.contains("kubernetes") || t.contains("k8s") || t.contains("aws")
                || t.contains("azure") || t.contains("linux") || t.contains("nginx") || t.contains("devops")) {
            return "icon-layers";
        }
        if (t.contains("android") || t.contains("ios") || t.contains("flutter") || t.contains("react native")
                || t.contains("kotlin") || t.contains("swift")) {
            return "icon-phone";
        }
        if (t.contains("react") || t.contains("angular") || t.contains("vue") || t.contains("html")
                || t.contains("css") || t.contains("javascript") || t.contains("typescript") || t.contains("next")) {
            return "icon-monitor";
        }
        if (t.contains("api") || t.contains("rest") || t.contains("graphql") || t.contains("http")) {
            return "icon-globe";
        }
        return "icon-cpu"; // lenguajes/backend general: Java, Python, Spring, Node, C#, PHP, etc.
    }
}
