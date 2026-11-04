package com.skillbridge.ai.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Mensaje de una sala de chat: autor, contenido, fecha y hora (RF07). */
@Entity
@Table(name = "chat_mensajes")
public class ChatMensaje {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sala_id", nullable = false)
    private Long salaId;

    /** Perfil que escribió el mensaje. */
    @Column(name = "autor_id", nullable = false)
    private Long autorId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "autor_id", insertable = false, updatable = false)
    private Perfil autor;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String contenido;

    @Column(name = "fecha_envio", nullable = false)
    private LocalDateTime fechaEnvio;

    public ChatMensaje() {}

    public ChatMensaje(Long salaId, Long autorId, String contenido) {
        this.salaId = salaId;
        this.autorId = autorId;
        this.contenido = contenido;
        this.fechaEnvio = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getSalaId() { return salaId; }
    public Long getAutorId() { return autorId; }
    public Perfil getAutor() { return autor; }
    public String getContenido() { return contenido; }
    public LocalDateTime getFechaEnvio() { return fechaEnvio; }
}
