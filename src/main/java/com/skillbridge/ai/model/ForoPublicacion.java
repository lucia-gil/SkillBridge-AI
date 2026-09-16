package com.skillbridge.ai.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Mapea "foro_publicaciones" (RF06). Una sola tabla autorreferencial:
 * publicacionPadreId NULL = hilo raiz (tiene titulo); no NULL = respuesta a
 * ese hilo. proyectoId es obligatorio en el esquema (todo post cuelga de un
 * proyecto) - ForoService arma la vista de "Foros" del colaborador cruzando
 * los proyectos en los que tiene/tuvo una asignacion.
 */
@Entity
@Table(name = "foro_publicaciones")
public class ForoPublicacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "proyecto_id", nullable = false)
    private Long proyectoId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proyecto_id", insertable = false, updatable = false)
    private Proyecto proyecto;

    @Column(name = "autor_id", nullable = false)
    private Long autorId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "autor_id", insertable = false, updatable = false)
    private Perfil autor;

    @Column(name = "publicacion_padre_id")
    private Long publicacionPadreId;

    @Column(name = "titulo", length = 200)
    private String titulo;

    @Column(name = "contenido", nullable = false, columnDefinition = "TEXT")
    private String contenido;

    @Column(name = "etiquetas", columnDefinition = "json")
    private String etiquetas;

    @Column(name = "es_solucion", nullable = false)
    private Boolean esSolucion = false;

    @Column(name = "num_vistas", nullable = false)
    private Integer numVistas = 0;

    @Column(name = "fecha_publicacion", insertable = false, updatable = false)
    private LocalDateTime fechaPublicacion;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getProyectoId() {
        return proyectoId;
    }

    public void setProyectoId(Long proyectoId) {
        this.proyectoId = proyectoId;
    }

    public Proyecto getProyecto() {
        return proyecto;
    }

    public Long getAutorId() {
        return autorId;
    }

    public void setAutorId(Long autorId) {
        this.autorId = autorId;
    }

    public Perfil getAutor() {
        return autor;
    }

    public Long getPublicacionPadreId() {
        return publicacionPadreId;
    }

    public void setPublicacionPadreId(Long publicacionPadreId) {
        this.publicacionPadreId = publicacionPadreId;
    }

    public String getTitulo() {
        return titulo;
    }

    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }

    public String getContenido() {
        return contenido;
    }

    public void setContenido(String contenido) {
        this.contenido = contenido;
    }

    public String getEtiquetas() {
        return etiquetas;
    }

    public void setEtiquetas(String etiquetas) {
        this.etiquetas = etiquetas;
    }

    public Boolean getEsSolucion() {
        return esSolucion;
    }

    public void setEsSolucion(Boolean esSolucion) {
        this.esSolucion = esSolucion;
    }

    public Integer getNumVistas() {
        return numVistas;
    }

    public void setNumVistas(Integer numVistas) {
        this.numVistas = numVistas;
    }

    public LocalDateTime getFechaPublicacion() {
        return fechaPublicacion;
    }
}
