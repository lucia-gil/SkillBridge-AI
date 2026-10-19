package com.skillbridge.ai.util;

public final class SesionKeys {
    private SesionKeys() {
    }

    /** Mismo nombre de atributo de sesion que usa QuintaOla-SGA (UsuarioDTO en sesion). */
    public static final String USUARIO = "usuarioLogueado";

    /** Contraseña correcta, falta el código TOTP (todavía NO autenticado en Spring Security). */
    public static final String PENDIENTE_2FA = "PENDIENTE_2FA";
    public static final String INTENTOS_2FA = "INTENTOS_2FA";
}
