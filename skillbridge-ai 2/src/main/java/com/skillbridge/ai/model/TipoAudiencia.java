package com.skillbridge.ai.model;

import jakarta.persistence.*;

/** Catalogo de audiencias de un evento (todos, solo_pm, solo_colaboradores) - tabla tipos_audiencia. */
@Entity
@Table(name = "tipos_audiencia")
public class TipoAudiencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "codigo", nullable = false)
    private String codigo;

    @Column(name = "nombre", nullable = false)
    private String nombre;

    public Long getId() { return id; }
    public String getCodigo() { return codigo; }
    public String getNombre() { return nombre; }
}
