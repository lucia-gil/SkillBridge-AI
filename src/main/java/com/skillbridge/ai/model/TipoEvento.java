package com.skillbridge.ai.model;

import jakarta.persistence.*;

/**
 * Catalogo de tipos de evento (reunion, entregable, hito) - tabla tipos_evento.
 * Mantiene los getters originales (usados por EventoService) y agrega
 * setters + el campo descripcion (existe en la tabla real, ver
 * skillbridge_db_v4.sql) para que el CRUD del Admin pueda crear/editar.
 */
@Entity
@Table(name = "tipos_evento")
public class TipoEvento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "codigo", nullable = false, unique = true, length = 40)
    private String codigo;

    @Column(name = "nombre", nullable = false, length = 80)
    private String nombre;

    @Column(name = "descripcion", length = 255)
    private String descripcion;

    @Column(name = "color", length = 20)
    private String color;

    public Long getId() { return id; }
    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
}