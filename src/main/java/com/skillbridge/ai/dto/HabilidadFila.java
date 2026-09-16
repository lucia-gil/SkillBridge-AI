package com.skillbridge.ai.dto;

/** Fila de la tabla administrador/habilidades.html, ya lista para pintar. */
public class HabilidadFila {

    private final Long id;
    private final String nombre;
    private final Long categoriaId;
    private final String categoriaNombre;
    private final long personas;
    private final long proyectos;
    private final String nivelMedio;
    private final String demanda;
    private final String demandaBadgeClass;

    public HabilidadFila(Long id, String nombre, Long categoriaId, String categoriaNombre,
                          long personas, long proyectos, Double promedioNivel) {
        this.id = id;
        this.nombre = nombre;
        this.categoriaId = categoriaId;
        this.categoriaNombre = categoriaNombre;
        this.personas = personas;
        this.proyectos = proyectos;
        this.nivelMedio = etiquetaNivel(promedioNivel);

        // "Demanda" no es una columna del esquema (no hay tabla que la registre
        // hoy) - se deriva de forma simple a partir de cuantos proyectos piden
        // esta habilidad, solo como referencia visual. Ver comentario en
        // HabilidadService.
        if (proyectos >= 3) {
            this.demanda = "Alta";
            this.demandaBadgeClass = "badge-red";
        } else if (proyectos >= 1) {
            this.demanda = "Media";
            this.demandaBadgeClass = "badge-amber";
        } else {
            this.demanda = "Baja";
            this.demandaBadgeClass = "badge-neutral";
        }
    }

    private static String etiquetaNivel(Double promedio) {
        if (promedio == null) return "—";
        if (promedio < 2.0) return "Básico";
        if (promedio < 3.5) return "Intermedio";
        if (promedio < 4.5) return "Avanzado";
        return "Experto";
    }

    public Long getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public Long getCategoriaId() {
        return categoriaId;
    }

    public String getCategoriaNombre() {
        return categoriaNombre;
    }

    public long getPersonas() {
        return personas;
    }

    public long getProyectos() {
        return proyectos;
    }

    public String getNivelMedio() {
        return nivelMedio;
    }

    public String getDemanda() {
        return demanda;
    }

    public String getDemandaBadgeClass() {
        return demandaBadgeClass;
    }
}
