package com.skillbridge.ai.dto;

import java.util.List;

/** Respuesta estable de la consulta paginada consumible por búsqueda predictiva. */
public record PaginaHabilidadesResponse(List<HabilidadFila> contenido, int pagina, int tamano,
                                        long totalElementos, int totalPaginas, boolean primera,
                                        boolean ultima) { }
