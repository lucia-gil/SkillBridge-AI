package com.skillbridge.ai.dto;

import java.util.List;

/**
 * Resultado de "Extraer con IA" para el formulario de "Nuevo entregable"
 * (mismo patrón que ProyectoExtraidoIa): BORRADOR editable, nunca se guarda
 * directo. "fechaApertura"/"fechaCierre" van como texto ISO
 * (yyyy-MM-ddTHH:mm) porque el destino es un &lt;input type="datetime-local"&gt;.
 */
public class EntregableExtraidoIa {

    private String titulo;
    private String descripcion;
    private String fechaApertura;
    private String fechaCierre;
    private String puntajeMaximo;
    private List<String> advertencias;

    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public String getFechaApertura() { return fechaApertura; }
    public void setFechaApertura(String fechaApertura) { this.fechaApertura = fechaApertura; }
    public String getFechaCierre() { return fechaCierre; }
    public void setFechaCierre(String fechaCierre) { this.fechaCierre = fechaCierre; }
    public String getPuntajeMaximo() { return puntajeMaximo; }
    public void setPuntajeMaximo(String puntajeMaximo) { this.puntajeMaximo = puntajeMaximo; }
    public List<String> getAdvertencias() { return advertencias; }
    public void setAdvertencias(List<String> advertencias) { this.advertencias = advertencias; }
}
