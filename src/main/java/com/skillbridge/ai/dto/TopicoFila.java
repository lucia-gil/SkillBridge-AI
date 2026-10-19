package com.skillbridge.ai.dto;

/**
 * Tópico del foro tal como lo usa la vista (filtros, chips, búsqueda).
 * palabrasClave viaja normalizada (minúscula, sin tildes) para que la
 * búsqueda inteligente del navegador use el mismo criterio que el
 * clasificador del servidor (ForoClasificadorService).
 */
public class TopicoFila {

    private final Long id;
    private final String codigo;
    private final String nombre;
    private final String descripcion;
    private final String color;
    private final String icono;
    private final String palabrasClave;
    private long numHilos;

    public TopicoFila(Long id, String codigo, String nombre, String descripcion, String color, String icono,
                      String palabrasClave) {
        this.id = id;
        this.codigo = codigo;
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.color = color;
        this.icono = icono;
        this.palabrasClave = palabrasClave;
    }

    public Long getId() {
        return id;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getNombre() {
        return nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public String getColor() {
        return color;
    }

    public String getIcono() {
        return icono;
    }

    public String getPalabrasClave() {
        return palabrasClave;
    }

    public long getNumHilos() {
        return numHilos;
    }

    public void setNumHilos(long numHilos) {
        this.numHilos = numHilos;
    }
}
