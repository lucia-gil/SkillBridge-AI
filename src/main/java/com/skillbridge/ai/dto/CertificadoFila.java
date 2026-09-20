package com.skillbridge.ai.dto;

/**
 * Un certificado de respaldo para una habilidad, dentro del perfil de un
 * colaborador. Puede ser un link externo (urlArchivo) o un PDF subido
 * como archivo real (tieneArchivo=true, se sirve por /certificados/{id}/archivo).
 */
public record CertificadoFila(Long id, String nombreArchivo, String urlArchivo, boolean tieneArchivo) {
}
