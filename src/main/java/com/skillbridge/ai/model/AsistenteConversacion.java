package com.skillbridge.ai.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** asistente_conversaciones: una conversación del Asistente IA (memoria del chatbot). */
@Entity
@Table(name = "asistente_conversaciones")
public class AsistenteConversacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    /** Vista (rol efectivo) desde la que se habló: el contexto cambia según el rol. */
    @Column(name = "rol", nullable = false, length = 30)
    private String rol;

    @Column(name = "titulo", nullable = false, length = 120)
    private String titulo;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "fecha_actualizacion", nullable = false)
    private LocalDateTime fechaActualizacion;

    protected AsistenteConversacion() { }

    public AsistenteConversacion(Long usuarioId, String rol, String titulo) {
        this.usuarioId = usuarioId;
        this.rol = rol;
        this.titulo = titulo;
        this.fechaCreacion = LocalDateTime.now();
        this.fechaActualizacion = this.fechaCreacion;
    }

    public void tocar() { this.fechaActualizacion = LocalDateTime.now(); }

    public Long getId() { return id; }
    public Long getUsuarioId() { return usuarioId; }
    public String getRol() { return rol; }
    public String getTitulo() { return titulo; }
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public LocalDateTime getFechaActualizacion() { return fechaActualizacion; }
}
