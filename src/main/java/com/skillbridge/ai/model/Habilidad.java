package com.skillbridge.ai.model;

import jakarta.persistence.*;

/** Mapea "habilidades" (catalogo cerrado, RF08) - entidad central del CRUD de habilidades. */
@Entity
@Table(name = "habilidades")
public class Habilidad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nombre", nullable = false, unique = true, length = 80)
    private String nombre;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id", nullable = false)
    private CategoriaHabilidad categoria;

    public Habilidad() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public CategoriaHabilidad getCategoria() {
        return categoria;
    }

    public void setCategoria(CategoriaHabilidad categoria) {
        this.categoria = categoria;
    }
}
