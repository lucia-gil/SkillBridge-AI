package com.skillbridge.ai.dto;

import java.util.List;

/** Datos enviados por auth/registro.html. */
public record RegistroRequest(
        String dni,
        String correo,
        String cargo,
        String contrasena,
        String contrasena2,
        List<HabilidadDeclarada> habilidades
) {
}
