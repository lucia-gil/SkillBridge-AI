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

    @Column(name = "es_fijado", nullable = false)
    private Boolean esFijado = false;

    @Column(name = "fecha_publicacion", insertable = false, updatable = false)
    private LocalDateTime fechaPublicacion;

    // ── v6 (migracion_v6_foros.sql) ──

    /** Tópico del hilo (solo en hilos raíz; en respuestas queda NULL). */
    @Column(name = "topico_id")
    private Long topicoId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topico_id", insertable = false, updatable = false)
    private ForoTopico topico;

    /**
     * Mensaje del mismo hilo al que responde puntualmente (cita, estilo
     * Slack/Zoom). El hilo sigue siendo plano: publicacionPadreId sigue
     * apuntando SIEMPRE al hilo raíz.
     */
    @Column(name = "respuesta_a_id")
    private Long respuestaAId;

    @Column(name = "editado", nullable = false)
    private Boolean editado = false;

    @Column(name = "num_ediciones", nullable = false)
    private Integer numEdiciones = 0;

    @Column(name = "fecha_edicion")
    private LocalDateTime fechaEdicion;

    /** Resumen generado por el Asistente IA (solo tiene sentido en el hilo raiz). */
    @Column(name = "resumen_ia", columnDefinition = "TEXT")
    private String resumenIa;

    @Column(name = "resumen_ia_generado_en")
    private LocalDateTime resumenIaGeneradoEn;

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

    public String getResumenIa() {
        return resumenIa;
    }

    public void setResumenIa(String resumenIa) {
        this.resumenIa = resumenIa;
    }

    public LocalDateTime getResumenIaGeneradoEn() {
        return resumenIaGeneradoEn;
    }

    public void setResumenIaGeneradoEn(LocalDateTime resumenIaGeneradoEn) {
        this.resumenIaGeneradoEn = resumenIaGeneradoEn;
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

    public Boolean getEsFijado() {
        return esFijado;
    }

    public void setEsFijado(Boolean esFijado) {
        this.esFijado = esFijado;
    }

    public LocalDateTime getFechaPublicacion() {
        return fechaPublicacion;
    }

    public Long getTopicoId() {
        return topicoId;
    }

    public void setTopicoId(Long topicoId) {
        this.topicoId = topicoId;
    }

    public ForoTopico getTopico() {
        return topico;
    }

    public Long getRespuestaAId() {
        return respuestaAId;
    }

    public void setRespuestaAId(Long respuestaAId) {
        this.respuestaAId = respuestaAId;
    }

    public Boolean getEditado() {
        return editado;
    }

    public void setEditado(Boolean editado) {
        this.editado = editado;
    }

    public Integer getNumEdiciones() {
        return numEdiciones;
    }

    public void setNumEdiciones(Integer numEdiciones) {
        this.numEdiciones = numEdiciones;
    }

    public LocalDateTime getFechaEdicion() {
        return fechaEdicion;
    }

    public void setFechaEdicion(LocalDateTime fechaEdicion) {
        this.fechaEdicion = fechaEdicion;
    }
}
