package com.skillbridge.ai.util;

/** Error de negocio con mensaje listo para mostrar al usuario (se captura en el controlador y se manda como flash "error"). */
public class OperacionInvalidaException extends RuntimeException {
    public OperacionInvalidaException(String mensaje) {
        super(mensaje);
    }
}
