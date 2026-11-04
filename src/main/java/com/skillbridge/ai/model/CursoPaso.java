package com.skillbridge.ai.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Un paso de una ruta (roadmap) o especialización: tabla curso_pasos. El
 * orden lo guarda la columna "orden" (@OrderColumn en Curso.pasos).
 */
@Embeddable
public class CursoPaso {

    @Column(nullable = false, length = 150)
    private String titulo;

    @Column(length = 500)
    private String url;

    public CursoPaso() {}

    public CursoPaso(String titulo, String url) {
        this.titulo = titulo;
        this.url = url;
    }

    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
}
