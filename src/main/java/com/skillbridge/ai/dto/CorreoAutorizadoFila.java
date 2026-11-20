package com.skillbridge.ai.dto;

/**
 * Fila de la sección "Correos autorizados" (administrador/usuarios.html).
 * Modelo v7 (fase 3): incluye el rol con el que se invitó.
 */
public record CorreoAutorizadoFila(Long id, String correo, String fechaAutorizacion, boolean utilizado, String rolAsignado, String envio) {

    public String estadoLabel() {
        return utilizado ? "Registrado" : "Pendiente";
    }

    public String estadoBadgeClass() {
        return utilizado ? "badge-green" : "badge-amber";
    }

    /** "Administrador" | "Resource Manager" | "Miembro". */
    public String rolLabel() {
        if ("administrador".equals(rolAsignado)) return "Administrador";
        if ("resource_manager".equals(rolAsignado)) return "Resource Manager";
        return "Miembro";
    }

    /** Valor para preseleccionar el <select> de "cambiar rol de la invitación". */
    public String rolValor() {
        return rolAsignado != null ? rolAsignado : "NINGUNO";
    }
}
