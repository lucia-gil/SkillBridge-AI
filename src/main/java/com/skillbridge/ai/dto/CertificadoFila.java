package com.skillbridge.ai.dto;

/**
 * La constancia de respaldo de una habilidad, dentro del perfil de un
 * colaborador. Puede tener un PDF subido (tieneArchivo=true, se sirve por
 * /certificados/{id}/archivo), un link externo (urlArchivo) o ambos.
 */
public record CertificadoFila(Long id, String nombreArchivo, String urlArchivo, boolean tieneArchivo) {

    public boolean tieneEnlace() {
        return urlArchivo != null && !urlArchivo.isBlank();
    }

    /** Texto corto para el link: el dominio (p. ej. "coursera.org") en vez de la URL completa. */
    public String enlaceEtiqueta() {
        if (!tieneEnlace()) return "";
        try {
            String host = java.net.URI.create(urlArchivo.trim()).getHost();
            if (host != null && !host.isBlank()) return host.startsWith("www.") ? host.substring(4) : host;
        } catch (Exception ignored) {
            // URL sin formato estándar: se muestra tal cual, recortada
        }
        String u = urlArchivo.trim();
        return u.length() > 40 ? u.substring(0, 37) + "…" : u;
    }
}
