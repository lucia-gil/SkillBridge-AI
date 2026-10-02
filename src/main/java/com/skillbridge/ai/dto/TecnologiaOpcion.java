package com.skillbridge.ai.dto;

/**
 * Tecnología del catálogo (tabla habilidades) para el autocompletado del
 * stack en los formularios de proyecto. Clase con getters (no record) para
 * que Jackson la serialice igual con th:inline="javascript".
 */
public class TecnologiaOpcion {

    private final String nombre;
    private final String categoria;

    public TecnologiaOpcion(String nombre, String categoria) {
        this.nombre = nombre;
        this.categoria = categoria;
    }

    public String getNombre() {
        return nombre;
    }

    public String getCategoria() {
        return categoria;
    }
}
