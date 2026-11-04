package com.skillbridge.ai.dto;

import java.util.List;

/**
 * Un recurso del catálogo de formación tal como lo pinta js/cursos.js
 * (catálogo de Colaborador/PM/RM y vista de administración). Viaja como JSON
 * dentro de la página (inlining de Thymeleaf), por eso solo tiene tipos simples.
 */
public record CursoVista(
        Long id,
        String titulo,
        String descripcion,
        String url,
        String tipo,
        String plataforma,
        String plataformaNombre,
        boolean plataformaManual,
        String youtubeId,
        Long categoriaId,
        String categoria,
        Long habilidadId,
        String habilidad,
        String nivel,
        int duracionHoras,
        boolean gratuito,
        boolean conCertificado,
        String idioma,
        boolean destacado,
        String estado,
        long creadoEn,
        List<Paso> pasos
) {
    public record Paso(String titulo, String url, String plataforma, String youtubeId) {}
}
