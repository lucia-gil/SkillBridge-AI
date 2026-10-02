package com.skillbridge.ai.dto;

/**
 * Cita de un mensaje del mismo hilo ("responder a"). eliminado=true cuando
 * el mensaje citado ya no existe (respuesta_a_id quedó en NULL por la FK
 * ON DELETE SET NULL): la vista muestra "Mensaje eliminado".
 */
public class CitaForo {

    private final Long id;
    private final String autorNombre;
    private final String extracto;
    private final boolean eliminado;

    public CitaForo(Long id, String autorNombre, String extracto, boolean eliminado) {
        this.id = id;
        this.autorNombre = autorNombre;
        this.extracto = extracto;
        this.eliminado = eliminado;
    }

    public static CitaForo eliminada() {
        return new CitaForo(null, "", "", true);
    }

    public Long getId() {
        return id;
    }

    public String getAutorNombre() {
        return autorNombre;
    }

    public String getExtracto() {
        return extracto;
    }

    public boolean isEliminado() {
        return eliminado;
    }
}
