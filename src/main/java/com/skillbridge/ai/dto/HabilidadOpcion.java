package com.skillbridge.ai.dto;

/**
 * Habilidad del catálogo (tabla habilidades) con su id, para el selector de
 * "Habilidades requeridas" del formulario de proyecto (RF: extracción con
 * IA). Se diferencia de TecnologiaOpcion en que expone el id: ese catálogo
 * solo sirve para el stack tecnológico (texto), mientras que para guardar
 * proyecto_habilidad_requerida se necesita el id real de la fila.
 */
public class HabilidadOpcion {

    private final Long id;
    private final String nombre;
    private final String categoria;

    public HabilidadOpcion(Long id, String nombre, String categoria) {
        this.id = id;
        this.nombre = nombre;
        this.categoria = categoria;
    }

    public Long getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public String getCategoria() {
        return categoria;
    }
}
