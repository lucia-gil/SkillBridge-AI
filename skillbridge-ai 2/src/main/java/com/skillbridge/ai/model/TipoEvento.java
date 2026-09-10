package com.skillbridge.ai.model;

import jakarta.persistence.*;

/** Catalogo de tipos de evento (reunion, entregable, hito) - tabla tipos_evento. */
@Entity
@Table(name = "tipos_evento")
public class TipoEvento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "codigo", nullable = false)
    private String codigo;

    @Column(name = "nombre", nullable = false)
    private String nombre;

    @Column(name = "color")
    private String color;

    public Long getId() { return id; }
    public String getCodigo() { return codigo; }
    public String getNombre() { return nombre; }
    public String getColor() { return color; }
}
