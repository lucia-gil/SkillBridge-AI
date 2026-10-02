package com.skillbridge.ai.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Fila de la lista de hilos (colaborador/foros.html, project-manager/foros.html)
 * y de "actividad reciente en foros" de los inicios.
 *
 * El constructor y los getters originales se conservan tal cual porque los
 * usan ColaboradorInicioController e InicioPmController. Los campos de la
 * v6 (tópico, etiquetas, destacados, búsqueda...) se completan con setters
 * desde ForoService.
 */
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
    private final boolean esFijado;

    // ── v6 ──
    private String topicoCodigo = "general";
    private String topicoNombre = "General";
    private String topicoColor = "neutral";
    private String topicoIcono = "icon-message";
    private List<String> etiquetas = new ArrayList<>();
    private String extracto = "";
    private String textoBusqueda = "";
    private long numDestacados;
    private int numAdjuntos;
    private String fechaIso = "";
    private String ultimaActividadIso = "";
    private String ultimaActividadLabel = "";
    private String ultimoAutorNombre = "";
    private List<String> participantesIniciales = new ArrayList<>();
    private int numParticipantes;
    private boolean esMio;
    private boolean puedeFijar;
    private boolean foroAbierto = true;

    public HiloFila(Long id, String titulo, Long proyectoId, String proyectoNombre, String autorNombre,
                     String fechaLabel, long numRespuestas, int numVistas, boolean tieneAceptada, boolean esFijado) {
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
        this.esFijado = esFijado;
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

    public boolean isEsFijado() {
        return esFijado;
    }

    /** "resuelto" | "sin_responder" | "abierto": estado visible del hilo. */
    public String getEstado() {
        if (tieneAceptada) return "resuelto";
        if (numRespuestas == 0) return "sin_responder";
        return "abierto";
    }

    // ── v6 ──

    public String getTopicoCodigo() {
        return topicoCodigo;
    }

    public void setTopicoCodigo(String topicoCodigo) {
        this.topicoCodigo = topicoCodigo;
    }

    public String getTopicoNombre() {
        return topicoNombre;
    }

    public void setTopicoNombre(String topicoNombre) {
        this.topicoNombre = topicoNombre;
    }

    public String getTopicoColor() {
        return topicoColor;
    }

    public void setTopicoColor(String topicoColor) {
        this.topicoColor = topicoColor;
    }

    public String getTopicoIcono() {
        return topicoIcono;
    }

    public void setTopicoIcono(String topicoIcono) {
        this.topicoIcono = topicoIcono;
    }

    public List<String> getEtiquetas() {
        return etiquetas;
    }

    public void setEtiquetas(List<String> etiquetas) {
        this.etiquetas = etiquetas;
    }

    public String getExtracto() {
        return extracto;
    }

    public void setExtracto(String extracto) {
        this.extracto = extracto;
    }

    public String getTextoBusqueda() {
        return textoBusqueda;
    }

    public void setTextoBusqueda(String textoBusqueda) {
        this.textoBusqueda = textoBusqueda;
    }

    public long getNumDestacados() {
        return numDestacados;
    }

    public void setNumDestacados(long numDestacados) {
        this.numDestacados = numDestacados;
    }

    public int getNumAdjuntos() {
        return numAdjuntos;
    }

    public void setNumAdjuntos(int numAdjuntos) {
        this.numAdjuntos = numAdjuntos;
    }

    public String getFechaIso() {
        return fechaIso;
    }

    public void setFechaIso(String fechaIso) {
        this.fechaIso = fechaIso;
    }

    public String getUltimaActividadIso() {
        return ultimaActividadIso;
    }

    public void setUltimaActividadIso(String ultimaActividadIso) {
        this.ultimaActividadIso = ultimaActividadIso;
    }

    public String getUltimaActividadLabel() {
        return ultimaActividadLabel;
    }

    public void setUltimaActividadLabel(String ultimaActividadLabel) {
        this.ultimaActividadLabel = ultimaActividadLabel;
    }

    public String getUltimoAutorNombre() {
        return ultimoAutorNombre;
    }

    public void setUltimoAutorNombre(String ultimoAutorNombre) {
        this.ultimoAutorNombre = ultimoAutorNombre;
    }

    public List<String> getParticipantesIniciales() {
        return participantesIniciales;
    }

    public void setParticipantesIniciales(List<String> participantesIniciales) {
        this.participantesIniciales = participantesIniciales;
    }

    public int getNumParticipantes() {
        return numParticipantes;
    }

    public void setNumParticipantes(int numParticipantes) {
        this.numParticipantes = numParticipantes;
    }

    public boolean isEsMio() {
        return esMio;
    }

    public void setEsMio(boolean esMio) {
        this.esMio = esMio;
    }

    public boolean isPuedeFijar() {
        return puedeFijar;
    }

    public void setPuedeFijar(boolean puedeFijar) {
        this.puedeFijar = puedeFijar;
    }

    public boolean isForoAbierto() {
        return foroAbierto;
    }

    public void setForoAbierto(boolean foroAbierto) {
        this.foroAbierto = foroAbierto;
    }
}
