package com.skillbridge.ai.dto;

import java.util.List;

/** Detalle completo de colaborador/foro-hilo.html. */
public class HiloDetalle {

    private final Long id;
    private final String titulo;
    private final Long proyectoId;
    private final String proyectoNombre;
    private final String autorNombre;
    private final String fechaLabel;
    private final String contenido;
    private final int numVistas;
    private final boolean permiteMarcarSolucion;
    private final List<RespuestaFila> respuestas;
    private final List<String> participantesIniciales;
    private final List<HiloResumen> relacionados;

    public HiloDetalle(Long id, String titulo, Long proyectoId, String proyectoNombre, String autorNombre,
                        String fechaLabel, String contenido, int numVistas, boolean permiteMarcarSolucion,
                        List<RespuestaFila> respuestas, List<String> participantesIniciales, List<HiloResumen> relacionados) {
        this.id = id;
        this.titulo = titulo;
        this.proyectoId = proyectoId;
        this.proyectoNombre = proyectoNombre;
        this.autorNombre = autorNombre;
        this.fechaLabel = fechaLabel;
        this.contenido = contenido;
        this.numVistas = numVistas;
        this.permiteMarcarSolucion = permiteMarcarSolucion;
        this.respuestas = respuestas;
        this.participantesIniciales = participantesIniciales;
        this.relacionados = relacionados;
    }

    public Long getId() {
        return id;
    }

    public String getTitulo() {
        return titulo;
    }

    public Long getProyectoId() {
        return proyectoId;
    }

    public String getProyectoNombre() {
        return proyectoNombre;
    }

    public String getAutorNombre() {
        return autorNombre;
    }

    public String getFechaLabel() {
        return fechaLabel;
    }

    public String getContenido() {
        return contenido;
    }

    public int getNumVistas() {
        return numVistas;
    }

    public boolean isPermiteMarcarSolucion() {
        return permiteMarcarSolucion;
    }

    public List<RespuestaFila> getRespuestas() {
        return respuestas;
    }

    public List<String> getParticipantesIniciales() {
        return participantesIniciales;
    }

    public List<HiloResumen> getRelacionados() {
        return relacionados;
    }
}
