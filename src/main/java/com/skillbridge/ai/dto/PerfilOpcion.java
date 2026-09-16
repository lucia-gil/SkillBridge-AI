package com.skillbridge.ai.dto;

/** Opción de un <select> de colaboradores (perfil activo con su nombre/correo). */
public record PerfilOpcion(Long perfilId, String nombre, String correo) {
}
