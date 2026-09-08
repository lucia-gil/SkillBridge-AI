package com.skillbridge.ai.dto;

import java.util.LinkedHashMap;
import java.util.Map;

/** Resultado de AuthService.registrar(...): exito, o mapa de errores por campo ("_global" para errores generales). */
public class ResultadoRegistro {

    private final boolean exito;
    private final Map<String, String> errores;

    private ResultadoRegistro(boolean exito, Map<String, String> errores) {
        this.exito = exito;
        this.errores = errores;
    }

    public static ResultadoRegistro exito() {
        return new ResultadoRegistro(true, new LinkedHashMap<>());
    }

    public static ResultadoRegistro conErrores(Map<String, String> errores) {
        return new ResultadoRegistro(false, errores);
    }

    public boolean isExito() {
        return exito;
    }

    public Map<String, String> getErrores() {
        return errores;
    }
}
