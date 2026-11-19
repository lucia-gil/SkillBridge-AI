package com.skillbridge.ai.dto;

import java.util.List;

/**
 * Resultado de "Extraer con IA" para el formulario de "Nuevo evento" del
 * calendario (mismo patrón que ProyectoExtraidoIa). "tipo" y "audiencia"
 * ya vienen resueltos contra el catálogo real (tipos_evento/tipos_audiencia)
 * como "codigo", listos para seleccionarse en el &lt;select&gt; del formulario.
 * "fechaInicio" va como texto ISO (yyyy-MM-ddTHH:mm).
 */
public class EventoExtraidoIa {

    private String tipo;
    private String titulo;
    private String fechaInicio;
    private String audiencia;
    private String ubicacion;
    private String enlace;
    private String descripcion;
    private List<String> advertencias;

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }
    public String getFechaInicio() { return fechaInicio; }
    public void setFechaInicio(String fechaInicio) { this.fechaInicio = fechaInicio; }
    public String getAudiencia() { return audiencia; }
    public void setAudiencia(String audiencia) { this.audiencia = audiencia; }
    public String getUbicacion() { return ubicacion; }
    public void setUbicacion(String ubicacion) { this.ubicacion = ubicacion; }
    public String getEnlace() { return enlace; }
    public void setEnlace(String enlace) { this.enlace = enlace; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public List<String> getAdvertencias() { return advertencias; }
    public void setAdvertencias(List<String> advertencias) { this.advertencias = advertencias; }
}
