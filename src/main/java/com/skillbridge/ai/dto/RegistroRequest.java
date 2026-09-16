package com.skillbridge.ai.dto;

import java.util.List;

/** Datos crudos (ya recortados/trim) enviados por auth/registro.html. */
public record RegistroRequest(
        String nombres,
        String apellidos,
        String correo,
        String cargo,
        String contrasena,
        String contrasena2,
        List<HabilidadDeclarada> habilidades
) {
}
