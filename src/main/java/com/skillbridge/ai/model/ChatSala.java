package com.skillbridge.ai.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Sala de chat de un proyecto (RF07). Hay una sala "General" por proyecto, creada al primer uso. */
@Entity
@Table(name = "chat_salas")
public class ChatSala {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "proyecto_id", nullable = false)
    private Long proyectoId;

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

    public Long getId() { return id; }
    public Long getProyectoId() { return proyectoId; }
    public Proyecto getProyecto() { return proyecto; }
    public String getNombre() { return nombre; }
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
}
