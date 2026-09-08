package com.skillbridge.ai.dto;

/** Una respuesta (o el post raíz) dentro del detalle de un hilo. */
public class RespuestaFila {

    private final Long id;
    private final String autorNombre;
    private final String autorCargo;
    private final String iniciales;
    private final String fechaLabel;
    private final String contenido;
    private final boolean esSolucion;

    public RespuestaFila(Long id, String autorNombre, String autorCargo, String fechaLabel, String contenido, boolean esSolucion) {
        this.id = id;
        this.autorNombre = autorNombre;
        this.autorCargo = autorCargo != null ? autorCargo : "";
        this.iniciales = InicialesUtil.de(autorNombre);
        this.fechaLabel = fechaLabel;
        this.contenido = contenido;
        this.esSolucion = esSolucion;
    }

    public Long getId() {
        return id;
    }

    public String getAutorNombre() {
        return autorNombre;
    }

    public String getAutorCargo() {
        return autorCargo;
    }

    public String getIniciales() {
        return iniciales;
    }

    public String getFechaLabel() {
        return fechaLabel;
    }

    public String getContenido() {
        return contenido;
    }

    public boolean isEsSolucion() {
        return esSolucion;
    }
}
