package com.skillbridge.ai.dto;

/** Fila de la sección "Correos autorizados" (administrador/usuarios.html). */
public record CorreoAutorizadoFila(Long id, String correo, String fechaAutorizacion, boolean utilizado) {

    public String estadoLabel() {
        return utilizado ? "Registrado" : "Pendiente";
    }

    public String estadoBadgeClass() {
        return utilizado ? "badge-green" : "badge-amber";
    }
}
