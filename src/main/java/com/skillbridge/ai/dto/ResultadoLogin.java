package com.skillbridge.ai.dto;

/** Resultado de AuthService.intentarLogin(...). */
public class ResultadoLogin {

    private final boolean exito;
    private final UsuarioSesion usuarioSesion;
    private final String mensajeError;

    private ResultadoLogin(boolean exito, UsuarioSesion usuarioSesion, String mensajeError) {
        this.exito = exito;
        this.usuarioSesion = usuarioSesion;
        this.mensajeError = mensajeError;
    }

    public static ResultadoLogin exito(UsuarioSesion usuarioSesion) {
        return new ResultadoLogin(true, usuarioSesion, null);
    }

    public static ResultadoLogin error(String mensaje) {
        return new ResultadoLogin(false, null, mensaje);
    }

    public boolean isExito() {
        return exito;
    }

    public UsuarioSesion getUsuarioSesion() {
        return usuarioSesion;
    }

    public String getMensajeError() {
        return mensajeError;
    }
}
