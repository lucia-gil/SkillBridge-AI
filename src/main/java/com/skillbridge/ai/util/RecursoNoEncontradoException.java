package com.skillbridge.ai.util;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * El recurso pedido (sala, conversación, adjunto, proyecto...) no existe o
 * no le pertenece a quien lo pide. Hereda de OperacionInvalidaException
 * para que las pantallas MVC lo sigan mostrando como mensaje "error";
 * los servicios REST (/api/**) lo responden con 404 Not Found.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class RecursoNoEncontradoException extends OperacionInvalidaException {
    public RecursoNoEncontradoException(String mensaje) {
        super(mensaje);
    }
}
