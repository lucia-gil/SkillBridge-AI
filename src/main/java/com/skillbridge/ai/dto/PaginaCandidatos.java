package com.skillbridge.ai.dto;

import java.util.List;

/**
 * Respuesta JSON del selector de personas (modelo v7, fase 4). La búsqueda,
 * los filtros, el orden y la paginación se resuelven en el servidor, así el
 * selector funciona igual con 20 que con miles de personas.
 *
 * @param habilidadesProyecto habilidades que pide el proyecto (se preseleccionan como filtro)
 * @param catalogoHabilidades todas las habilidades, para filtrar por otras
 * @param cargos              cargos existentes, para el filtro por cargo
 */
public record PaginaCandidatos(List<Candidato> items, int total, int pagina, int paginas,
                               long vacantes, int limiteCarga, int maximoProyectos,
                               List<Opcion> habilidadesProyecto, List<Opcion> catalogoHabilidades,
                               List<String> cargos) {

    /** Una persona elegible (o visible pero bloqueada, con su motivo). */
    public record Candidato(Long perfilId, String nombre, String correo, String iniciales, String cargo,
                            int experienciaAnios, int cargaActual, int disponible, long proyectosVigentes,
                            int coincidencias, int requeridas, List<Chip> habilidades,
                            boolean seleccionable, String motivo, boolean propuestaPendiente) {
    }

    /** Habilidad de una persona; requerida = la pide el proyecto; cumple = alcanza el nivel pedido. */
    public record Chip(String nombre, int nivel, boolean requerida, boolean cumple) {
    }

    public record Opcion(Long id, String nombre, int nivel) {
    }
}
