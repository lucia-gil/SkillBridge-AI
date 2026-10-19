package com.skillbridge.ai.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Mapea "eventos_proyecto" (RF calendario): un evento (reunion/entregable/hito) de un proyecto. */
@Entity
@Table(name = "eventos_proyecto")
public class EventoProyecto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "proyecto_id", nullable = false)
    private Long proyectoId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proyecto_id", insertable = false, updatable = false)
    private Proyecto proyecto;

    @Column(name = "creado_por_id", nullable = false)
    private Long creadoPorId;

    @Column(name = "tipo_id", nullable = false)
    private Long tipoId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tipo_id", insertable = false, updatable = false)
    private TipoEvento tipo;

    @Column(name = "titulo", nullable = false, length = 150)
    private String titulo;

    @Column(name = "descripcion", columnDefinition = "TEXT")
    private String descripcion;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDateTime fechaInicio;

    @Column(name = "fecha_fin")
    private LocalDateTime fechaFin;

    @Column(name = "enlace_virtual", length = 255)
    private String enlaceVirtual;

    @Column(name = "ubicacion", length = 150)
    private String ubicacion;

    @Column(name = "audiencia_id", nullable = false)
    private Long audienciaId = 1L;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "audiencia_id", insertable = false, updatable = false)
    private TipoAudiencia audiencia;

    @Column(name = "estado", nullable = false, length = 20)
    private String estado = "pendiente";

    @Column(name = "fecha_creacion", insertable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    public Long getId() { return id; }
    public Long getProyectoId() { return proyectoId; }
    public void setProyectoId(Long proyectoId) { this.proyectoId = proyectoId; }
    public Proyecto getProyecto() { return proyecto; }
    public Long getCreadoPorId() { return creadoPorId; }
    public void setCreadoPorId(Long creadoPorId) { this.creadoPorId = creadoPorId; }
    public Long getTipoId() { return tipoId; }
    public void setTipoId(Long tipoId) { this.tipoId = tipoId; }
    public TipoEvento getTipo() { return tipo; }
    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public LocalDateTime getFechaInicio() { return fechaInicio; }
    public void setFechaInicio(LocalDateTime fechaInicio) { this.fechaInicio = fechaInicio; }
    public LocalDateTime getFechaFin() { return fechaFin; }
    public void setFechaFin(LocalDateTime fechaFin) { this.fechaFin = fechaFin; }
    public String getEnlaceVirtual() { return enlaceVirtual; }
    public void setEnlaceVirtual(String enlaceVirtual) { this.enlaceVirtual = enlaceVirtual; }
    public String getUbicacion() { return ubicacion; }
    public void setUbicacion(String ubicacion) { this.ubicacion = ubicacion; }
    public Long getAudienciaId() { return audienciaId; }
    public void setAudienciaId(Long audienciaId) { this.audienciaId = audienciaId; }
    public TipoAudiencia getAudiencia() { return audiencia; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
}
