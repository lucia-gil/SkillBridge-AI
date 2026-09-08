package com.skillbridge.ai.dto;

/**
 * Una fila del paso 2 del wizard de registro: habilidad + nivel textual
 * elegidos. Nombres de campo iguales a los que ya arma
 * auth/registro.html en memoria ({ nombre, nivel }) para que el JSON que
 * manda el formulario mapee directo via Jackson, sin @JsonProperty.
 */
public record HabilidadDeclarada(String nombre, String nivel) {
}
