package com.skillbridge.ai.model;

import jakarta.persistence.*;

/**
 * Mapea "foro_topicos" (migracion_v6_foros.sql): catálogo de temas con el que
 * se categorizan los hilos del foro (Backend, Base de datos, DevOps...).
 *
 * color es una clave de tono del design system (blue|violet|green|amber|red|
 * neutral), no un hex, para que la vista respete el tema claro/oscuro.
 * palabrasClave (separadas por coma, minúscula, sin tildes) alimenta el
 * clasificador automático (ForoClasificadorService) y la búsqueda de la vista.
 */
@Entity
@Table(name = "foro_topicos")
public class ForoTopico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "codigo", nullable = false, unique = true, length = 40)
    private String codigo;

    @Column(name = "nombre", nullable = false, length = 80)
    private String nombre;

    @Column(name = "descripcion", length = 255)
    private String descripcion;

    @Column(name = "color", nullable = false, length = 20)
    private String color = "neutral";

    @Column(name = "icono", nullable = false, length = 40)
    private String icono = "icon-message";

    @Column(name = "palabras_clave", columnDefinition = "TEXT")
    private String palabrasClave;

    @Column(name = "orden", nullable = false)
    private Integer orden = 0;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCodigo() {
        return codigo;
    }

    public void setCodigo(String codigo) {
        this.codigo = codigo;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public String getIcono() {
        return icono;
    }

    public void setIcono(String icono) {
        this.icono = icono;
    }

    public String getPalabrasClave() {
        return palabrasClave;
    }

    public void setPalabrasClave(String palabrasClave) {
        this.palabrasClave = palabrasClave;
    }

    public Integer getOrden() {
        return orden;
    }

    public void setOrden(Integer orden) {
        this.orden = orden;
    }
}
