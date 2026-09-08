package com.skillbridge.ai.dto;

/** Fila de la lista de hilos en colaborador/foros.html. */
public class HiloFila {

    private final Long id;
    private final String titulo;
    private final Long proyectoId;
    private final String proyectoNombre;
    private final String autorNombre;
    private final String autorIniciales;
    private final String fechaLabel;
    private final long numRespuestas;
    private final int numVistas;
    private final boolean tieneAceptada;

    public HiloFila(Long id, String titulo, Long proyectoId, String proyectoNombre, String autorNombre,
                     String fechaLabel, long numRespuestas, int numVistas, boolean tieneAceptada) {
        this.id = id;
        this.titulo = titulo;
        this.proyectoId = proyectoId;
        this.proyectoNombre = proyectoNombre;
        this.autorNombre = autorNombre;
        this.autorIniciales = InicialesUtil.de(autorNombre);
        this.fechaLabel = fechaLabel;
        this.numRespuestas = numRespuestas;
        this.numVistas = numVistas;
        this.tieneAceptada = tieneAceptada;
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

    public String getAutorIniciales() {
        return autorIniciales;
    }

    public String getFechaLabel() {
        return fechaLabel;
    }

    public long getNumRespuestas() {
        return numRespuestas;
    }

    public int getNumVistas() {
        return numVistas;
    }

    public boolean isTieneAceptada() {
        return tieneAceptada;
    }
}
