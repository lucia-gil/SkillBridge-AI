package com.skillbridge.ai.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Detalle completo de foro-hilo.html (colaborador y project-manager).
 * Desde la v6 el mensaje inicial también es una RespuestaFila
 * (mensajeInicial), así la vista pinta todos los mensajes igual: cita,
 * editado, destacados y adjuntos.
 */
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
    private final boolean permiteEditarHilo;
    private final boolean permiteEliminarHilo;
    private final List<RespuestaFila> respuestas;
    private final List<String> participantesIniciales;
    private final List<HiloResumen> relacionados;

    // ── v6 ──
    private RespuestaFila mensajeInicial;
    private RespuestaFila solucion;
    private TopicoFila topico;
    private List<String> etiquetas = new ArrayList<>();
    private boolean foroAbierto = true;
    private String estadoProyecto = "";
    private List<ParticipanteFila> participantes = new ArrayList<>();
    private List<ForoAdjuntoInfo> adjuntos = new ArrayList<>();
    private long numDestacadosTotal;
    private String ultimaActividadLabel = "";
    private int maxEdiciones;
    private boolean esFijado;
    private boolean permiteFijar;
    private boolean esModerador;

    public HiloDetalle(Long id, String titulo, Long proyectoId, String proyectoNombre, String autorNombre,
                        String fechaLabel, String contenido, int numVistas, boolean permiteMarcarSolucion,
                        boolean permiteEditarHilo, boolean permiteEliminarHilo,
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
        this.permiteEditarHilo = permiteEditarHilo;
        this.permiteEliminarHilo = permiteEliminarHilo;
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

    public boolean isPermiteEditarHilo() {
        return permiteEditarHilo;
    }

    public boolean isPermiteEliminarHilo() {
        return permiteEliminarHilo;
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

    /** "resuelto" | "sin_responder" | "abierto". */
    public String getEstado() {
        if (solucion != null) return "resuelto";
        if (respuestas.isEmpty()) return "sin_responder";
        return "abierto";
    }

    // ── v6 ──

    public RespuestaFila getMensajeInicial() {
        return mensajeInicial;
    }

    public void setMensajeInicial(RespuestaFila mensajeInicial) {
        this.mensajeInicial = mensajeInicial;
    }

    public RespuestaFila getSolucion() {
        return solucion;
    }

    public void setSolucion(RespuestaFila solucion) {
        this.solucion = solucion;
    }

    public TopicoFila getTopico() {
        return topico;
    }

    public void setTopico(TopicoFila topico) {
        this.topico = topico;
    }

    public List<String> getEtiquetas() {
        return etiquetas;
    }

    public void setEtiquetas(List<String> etiquetas) {
        this.etiquetas = etiquetas;
    }

    public String getEtiquetasTexto() {
        return String.join(", ", etiquetas);
    }

    public boolean isForoAbierto() {
        return foroAbierto;
    }

    public void setForoAbierto(boolean foroAbierto) {
        this.foroAbierto = foroAbierto;
    }

    public String getEstadoProyecto() {
        return estadoProyecto;
    }

    public void setEstadoProyecto(String estadoProyecto) {
        this.estadoProyecto = estadoProyecto;
    }

    public List<ParticipanteFila> getParticipantes() {
        return participantes;
    }

    public void setParticipantes(List<ParticipanteFila> participantes) {
        this.participantes = participantes;
    }

    public List<ForoAdjuntoInfo> getAdjuntos() {
        return adjuntos;
    }

    public void setAdjuntos(List<ForoAdjuntoInfo> adjuntos) {
        this.adjuntos = adjuntos;
    }

    public long getNumDestacadosTotal() {
        return numDestacadosTotal;
    }

    public void setNumDestacadosTotal(long numDestacadosTotal) {
        this.numDestacadosTotal = numDestacadosTotal;
    }

    public String getUltimaActividadLabel() {
        return ultimaActividadLabel;
    }

    public void setUltimaActividadLabel(String ultimaActividadLabel) {
        this.ultimaActividadLabel = ultimaActividadLabel;
    }

    public int getMaxEdiciones() {
        return maxEdiciones;
    }

    public void setMaxEdiciones(int maxEdiciones) {
        this.maxEdiciones = maxEdiciones;
    }

    public boolean isEsFijado() {
        return esFijado;
    }

    public void setEsFijado(boolean esFijado) {
        this.esFijado = esFijado;
    }

    public boolean isPermiteFijar() {
        return permiteFijar;
    }

    public void setPermiteFijar(boolean permiteFijar) {
        this.permiteFijar = permiteFijar;
    }

    public boolean isEsModerador() {
        return esModerador;
    }

    public void setEsModerador(boolean esModerador) {
        this.esModerador = esModerador;
    }
}
