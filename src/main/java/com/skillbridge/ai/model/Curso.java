package com.skillbridge.ai.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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

    // ── Catálogo de formación (v11) ──
    /** CURSO, VIDEO, RUTA (roadmap) o ESPECIALIZACION. */
    @Column(nullable = false, length = 20)
    private String tipo = "CURSO";
    /** Clave de PlataformaCurso (youtube, coursera, microsoft...). */
    @Column(length = 40)
    private String plataforma;
    @Column(nullable = false)
    private boolean gratuito = true;
    @Column(name = "con_certificado", nullable = false)
    private boolean conCertificado = false;
    @Column(nullable = false, length = 5)
    private String idioma = "ES";
    @Column(nullable = false)
    private boolean destacado = false;
    /** Pasos ordenados de una ruta o especialización. */
    @ElementCollection
    @org.hibernate.annotations.BatchSize(size = 50)
    @CollectionTable(name = "curso_pasos", joinColumns = @JoinColumn(name = "curso_id"))
    @OrderColumn(name = "orden")
    private List<CursoPaso> pasos = new ArrayList<>();
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
    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
    public String getPlataforma() { return plataforma; }
    public void setPlataforma(String plataforma) { this.plataforma = plataforma; }
    public boolean isGratuito() { return gratuito; }
    public void setGratuito(boolean gratuito) { this.gratuito = gratuito; }
    public boolean isConCertificado() { return conCertificado; }
    public void setConCertificado(boolean conCertificado) { this.conCertificado = conCertificado; }
    public String getIdioma() { return idioma; }
    public void setIdioma(String idioma) { this.idioma = idioma; }
    public boolean isDestacado() { return destacado; }
    public void setDestacado(boolean destacado) { this.destacado = destacado; }
    public List<CursoPaso> getPasos() { return pasos; }
    public void setPasos(List<CursoPaso> pasos) { this.pasos = pasos; }
}
