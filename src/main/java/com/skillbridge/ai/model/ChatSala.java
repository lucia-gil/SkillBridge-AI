package com.skillbridge.ai.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Sala de chat. Tipo "proyecto": la sala "General" de un proyecto (RF07),
 * creada al primer uso. Tipo "directo": mensajes directos entre dos personas
 * (sin proyecto); sus integrantes están en chat_participantes y clave_directa
 * ("perfilMenor-perfilMayor") evita salas duplicadas para la misma pareja.
 */
@Entity
@Table(name = "chat_salas")
public class ChatSala {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    public static final String PROYECTO = "proyecto";
    public static final String DIRECTO = "directo";

    @Column(nullable = false, length = 20)
    private String tipo = PROYECTO;

    @Column(name = "proyecto_id")
    private Long proyectoId;

    @Column(name = "clave_directa", length = 41, unique = true)
    private String claveDirecta;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proyecto_id", insertable = false, updatable = false)
    private Proyecto proyecto;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(name = "fecha_creacion", insertable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    public ChatSala() {}

    public ChatSala(Long proyectoId, String nombre) {
        this.proyectoId = proyectoId;
        this.nombre = nombre;
    }

    /** Sala de mensajes directos entre dos perfiles. */
    public static ChatSala directa(Long perfilA, Long perfilB) {
        ChatSala s = new ChatSala();
        s.tipo = DIRECTO;
        s.claveDirecta = claveDirecta(perfilA, perfilB);
        s.nombre = "Mensaje directo";
        return s;
    }

    /** "menor-mayor": la misma pareja siempre da la misma clave. */
    public static String claveDirecta(Long a, Long b) {
        return Math.min(a, b) + "-" + Math.max(a, b);
    }

    public boolean esDirecta() { return DIRECTO.equals(tipo); }
    public String getTipo() { return tipo; }
    public String getClaveDirecta() { return claveDirecta; }

    public Long getId() { return id; }
    public Long getProyectoId() { return proyectoId; }
    public Proyecto getProyecto() { return proyecto; }
    public String getNombre() { return nombre; }
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
}
