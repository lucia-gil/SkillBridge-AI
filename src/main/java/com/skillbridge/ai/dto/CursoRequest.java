package com.skillbridge.ai.dto;

import jakarta.validation.constraints.*;

public class CursoRequest {
    @NotBlank @Size(max = 150)
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
}
