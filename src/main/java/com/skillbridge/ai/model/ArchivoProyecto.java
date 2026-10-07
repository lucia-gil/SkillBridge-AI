package com.skillbridge.ai.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Archivo de contexto adjunto a un proyecto (PDF, imágenes, docs).
 * El PM puede subir archivos para dar contexto al equipo.
 */
@Entity
@Table(name = "archivos_proyecto")
public class ArchivoProyecto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "proyecto_id", nullable = false)
    private Long proyectoId;

    @Column(name = "nombre", nullable = false, length = 255)
    private String nombre;

    @Column(name = "tipo", length = 100)
    private String tipo;

    @Lob
    @Column(name = "datos", nullable = false, columnDefinition = "MEDIUMBLOB")
    private byte[] datos;

    @Column(name = "fecha_subida", insertable = false, updatable = false)
    private LocalDateTime fechaSubida;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProyectoId() { return proyectoId; }
    public void setProyectoId(Long proyectoId) { this.proyectoId = proyectoId; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
    public byte[] getDatos() { return datos; }
    public void setDatos(byte[] datos) { this.datos = datos; }
    public LocalDateTime getFechaSubida() { return fechaSubida; }
}
