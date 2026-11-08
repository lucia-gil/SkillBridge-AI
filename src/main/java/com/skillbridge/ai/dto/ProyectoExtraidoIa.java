package com.skillbridge.ai.dto;

import java.util.List;

/**
 * Resultado de "Extraer con IA" (subir un documento con los datos de un
 * proyecto nuevo y que el Asistente IA llene el formulario). Es un
 * BORRADOR: nunca se guarda directo, el Project Manager lo revisa/edita en
 * el propio formulario de "Nuevo proyecto" y solo se persiste cuando él
 * hace clic en "Crear proyecto" (igual que si lo hubiera tipeado a mano).
 *
 * Las fechas van como texto ISO (yyyy-MM-dd) porque el destino es un
 * <input type="date"> en el formulario, no una entidad.
 */
public class ProyectoExtraidoIa {

    /** record con getters (Jackson) para la lista de habilidades resueltas contra el catálogo. */
    public record HabilidadRequeridaExtraida(Long habilidadId, String nombre, Integer nivelRequerido) {}

    private String nombre;
    private String descripcion;
    private List<String> tecnologias;
    private String fechaInicio;
    private String fechaFinEstimada;
    private Integer colaboradoresRequeridos;
    private List<HabilidadRequeridaExtraida> habilidadesRequeridas;
    /** Avisos para el PM: campos que la IA no pudo extraer con confianza, tecnologías/habilidades fuera del catálogo, etc. */
    private List<String> advertencias;

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public List<String> getTecnologias() {
        return tecnologias;
    }

    public void setTecnologias(List<String> tecnologias) {
        this.tecnologias = tecnologias;
    }

    public String getFechaInicio() {
        return fechaInicio;
    }

    public void setFechaInicio(String fechaInicio) {
        this.fechaInicio = fechaInicio;
    }

    public String getFechaFinEstimada() {
        return fechaFinEstimada;
    }

    public void setFechaFinEstimada(String fechaFinEstimada) {
        this.fechaFinEstimada = fechaFinEstimada;
    }

    public Integer getColaboradoresRequeridos() {
        return colaboradoresRequeridos;
    }

    public void setColaboradoresRequeridos(Integer colaboradoresRequeridos) {
        this.colaboradoresRequeridos = colaboradoresRequeridos;
    }

    public List<HabilidadRequeridaExtraida> getHabilidadesRequeridas() {
        return habilidadesRequeridas;
    }

    public void setHabilidadesRequeridas(List<HabilidadRequeridaExtraida> habilidadesRequeridas) {
        this.habilidadesRequeridas = habilidadesRequeridas;
    }

    public List<String> getAdvertencias() {
        return advertencias;
    }

    public void setAdvertencias(List<String> advertencias) {
        this.advertencias = advertencias;
    }
}
