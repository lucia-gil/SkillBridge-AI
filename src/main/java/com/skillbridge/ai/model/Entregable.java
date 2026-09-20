package com.skillbridge.ai.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Un entregable de un proyecto (RF nuevo: "avance real" vía trabajo
 * revisado, no heurístico por fechas). Lo crea el PM del proyecto (o el
 * Administrador) y lo ve TODO el equipo activo de ese proyecto — no es
 * por persona, es por proyecto, igual que un "Entregable N" de Moodle
 * visto en la captura de referencia.
 */
@Entity
@Table(name = "entregables")
public class Entregable {

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

    @Column(name = "titulo", nullable = false, length = 200)
    private String titulo;

    @Column(name = "descripcion")
    private String descripcion;

    @Column(name = "fecha_apertura", nullable = false)
    private LocalDateTime fechaApertura;

    @Column(name = "fecha_cierre", nullable = false)
    private LocalDateTime fechaCierre;

    @Column(name = "puntaje_maximo", nullable = false)
    private BigDecimal puntajeMaximo = new BigDecimal("20.00");

    /** 'activo' | 'cancelado' (cancelado = el PM lo retiró, ya no acepta entregas). */
    @Column(name = "estado", nullable = false, length = 20)
    private String estado = "activo";

    @Column(name = "fecha_creacion", insertable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    public Long getId() { return id; }
    public Long getProyectoId() { return proyectoId; }
    public void setProyectoId(Long proyectoId) { this.proyectoId = proyectoId; }
    public Proyecto getProyecto() { return proyecto; }
    public Long getCreadoPorId() { return creadoPorId; }
    public void setCreadoPorId(Long creadoPorId) { this.creadoPorId = creadoPorId; }
    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public LocalDateTime getFechaApertura() { return fechaApertura; }
    public void setFechaApertura(LocalDateTime fechaApertura) { this.fechaApertura = fechaApertura; }
    public LocalDateTime getFechaCierre() { return fechaCierre; }
    public void setFechaCierre(LocalDateTime fechaCierre) { this.fechaCierre = fechaCierre; }
    public BigDecimal getPuntajeMaximo() { return puntajeMaximo; }
    public void setPuntajeMaximo(BigDecimal puntajeMaximo) { this.puntajeMaximo = puntajeMaximo; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
}
