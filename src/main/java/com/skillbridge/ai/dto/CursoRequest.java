package com.skillbridge.ai.dto;

import jakarta.validation.constraints.*;

public class CursoRequest {
    @NotBlank @Size(min = 3, max = 150, message = "El título debe tener entre 3 y 150 caracteres.")
    private String titulo;
    @Size(max = 5000)
    private String descripcion;
    @Size(max = 500)
    private String url;
    private Long categoriaId;
    private Long habilidadId;
    @NotBlank
    private String nivel;
    @Min(1) @Max(10000)
    private int duracionHoras;
    // ── Catálogo de formación (v11) ──
    private String tipo = "CURSO";
    /** Vacío = se detecta por el enlace. */
    private String plataforma;
    private boolean gratuito;
    private boolean conCertificado;
    private String idioma = "ES";
    private boolean destacado;
    /** Pasos de una ruta/especialización (listas paralelas del formulario). */
    private java.util.List<String> pasoTitulo = new java.util.ArrayList<>();
    private java.util.List<String> pasoUrl = new java.util.ArrayList<>();
    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public Long getCategoriaId() { return categoriaId; }
    public void setCategoriaId(Long categoriaId) { this.categoriaId = categoriaId; }
    public Long getHabilidadId() { return habilidadId; }
    public void setHabilidadId(Long habilidadId) { this.habilidadId = habilidadId; }
    public String getNivel() { return nivel; }
    public void setNivel(String nivel) { this.nivel = nivel; }
    public int getDuracionHoras() { return duracionHoras; }
    public void setDuracionHoras(int duracionHoras) { this.duracionHoras = duracionHoras; }
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
    public java.util.List<String> getPasoTitulo() { return pasoTitulo; }
    public void setPasoTitulo(java.util.List<String> pasoTitulo) { this.pasoTitulo = pasoTitulo; }
    public java.util.List<String> getPasoUrl() { return pasoUrl; }
    public void setPasoUrl(java.util.List<String> pasoUrl) { this.pasoUrl = pasoUrl; }
}
