package com.skillbridge.ai.dto;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Fila de "Entregables" de un proyecto, para PM/Admin (gestión) y Colaborador (lista). */
public class EntregableFila {

    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final Long id;
    private final Long proyectoId;
    private final String proyectoNombre;
    private final String titulo;
    private final String descripcion;
    private final String fechaAperturaLabel;
    private final String fechaCierreLabel;
    private final String puntajeMaximoLabel;
    private final String estadoCrudo;
    private final boolean vencido;
    // Para PM/Admin: cobertura del equipo. Para Colaborador: su propia entrega (o null si no entregó).
    private final int totalEquipo;
    private final int totalEntregados;
    private final int totalRevisados;
    private final EntregaFila miEntrega; // null si es la vista de gestión (PM/Admin) o si el colaborador no entregó

    public EntregableFila(Long id, Long proyectoId, String proyectoNombre, String titulo, String descripcion,
                           LocalDateTime fechaApertura, LocalDateTime fechaCierre, String puntajeMaximoLabel,
                           String estadoCrudo, int totalEquipo, int totalEntregados, int totalRevisados,
                           EntregaFila miEntrega) {
        this.id = id;
        this.proyectoId = proyectoId;
        this.proyectoNombre = proyectoNombre;
        this.titulo = titulo;
        this.descripcion = descripcion;
        this.fechaAperturaLabel = fechaApertura.format(FORMATO);
        this.fechaCierreLabel = fechaCierre.format(FORMATO);
        this.puntajeMaximoLabel = puntajeMaximoLabel;
        this.estadoCrudo = estadoCrudo;
        this.vencido = "activo".equals(estadoCrudo) && LocalDateTime.now().isAfter(fechaCierre);
        this.totalEquipo = totalEquipo;
        this.totalEntregados = totalEntregados;
        this.totalRevisados = totalRevisados;
        this.miEntrega = miEntrega;
    }

    public Long getId() { return id; }
    public Long getProyectoId() { return proyectoId; }
    public String getProyectoNombre() { return proyectoNombre; }
    public String getTitulo() { return titulo; }
    public String getDescripcion() { return descripcion; }
    public String getFechaAperturaLabel() { return fechaAperturaLabel; }
    public String getFechaCierreLabel() { return fechaCierreLabel; }
    public String getPuntajeMaximoLabel() { return puntajeMaximoLabel; }
    public String getEstadoCrudo() { return estadoCrudo; }
    public boolean isVencido() { return vencido; }
    public int getTotalEquipo() { return totalEquipo; }
    public int getTotalEntregados() { return totalEntregados; }
    public int getTotalRevisados() { return totalRevisados; }
    public EntregaFila getMiEntrega() { return miEntrega; }

    public String getEstadoLabel() {
        if ("cancelado".equals(estadoCrudo)) return "Cancelado";
        return vencido ? "Cerrado" : "Abierto";
    }

    public String getEstadoBadgeClass() {
        if ("cancelado".equals(estadoCrudo)) return "badge-neutral";
        return vencido ? "badge-red" : "badge-green";
    }
}
