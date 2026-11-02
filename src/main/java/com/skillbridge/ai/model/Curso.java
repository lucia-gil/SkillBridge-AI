package com.skillbridge.ai.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "cursos")
public class Curso {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 150)
    private String titulo;
    @Column(columnDefinition = "TEXT")
    private String descripcion;
    @Column(length = 500)
    private String url;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_id")
    private CategoriaHabilidad categoria;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "habilidad_id")
    private Habilidad habilidad;
    @Column(nullable = false, length = 20)
    private String nivel = "BASICO";
    @Column(nullable = false, length = 20)
    private String estado = "PUBLICADO";
    @Column(name = "duracion_horas", nullable = false)
    private int duracionHoras = 1;
    @Column(name = "fecha_creacion", insertable = false, updatable = false)
    private LocalDateTime fechaCreacion;
    public Long getId() { return id; }
    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public CategoriaHabilidad getCategoria() { return categoria; }
    public void setCategoria(CategoriaHabilidad categoria) { this.categoria = categoria; }
    public Habilidad getHabilidad() { return habilidad; }
    public void setHabilidad(Habilidad habilidad) { this.habilidad = habilidad; }
    public String getNivel() { return nivel; }
    public void setNivel(String nivel) { this.nivel = nivel; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public int getDuracionHoras() { return duracionHoras; }
    public void setDuracionHoras(int duracionHoras) { this.duracionHoras = duracionHoras; }
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
}
