package com.skillbridge.ai.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** asistente_mensajes: cada turno de una conversación del Asistente IA. */
@Entity
@Table(name = "asistente_mensajes")
public class AsistenteMensaje {

    public static final String USUARIO = "usuario";
    public static final String ASISTENTE = "asistente";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversacion_id", nullable = false)
    private Long conversacionId;

    @Column(name = "autor", nullable = false, length = 10)
    private String autor;

    @Column(name = "contenido", nullable = false, columnDefinition = "TEXT")
    private String contenido;

    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha;

    protected AsistenteMensaje() { }

    public AsistenteMensaje(Long conversacionId, String autor, String contenido) {
        this.conversacionId = conversacionId;
        this.autor = autor;
        this.contenido = contenido;
        this.fecha = LocalDateTime.now();
    }

    public boolean esDelUsuario() { return USUARIO.equals(autor); }
    public Long getId() { return id; }
    public Long getConversacionId() { return conversacionId; }
    public String getAutor() { return autor; }
    public String getContenido() { return contenido; }
    public LocalDateTime getFecha() { return fecha; }
}
