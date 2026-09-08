package com.skillbridge.ai.dto;

/** Una habilidad declarada por un colaborador en su perfil (colaborador/perfil.html). */
public class HabilidadPerfilFila {

    private final Long habilidadId;
    private final String nombre;
    private final String categoriaNombre;
    private final int nivelNumero;
    private final String nivelLabel;
    private final String declaradaDesdeLabel;

    public HabilidadPerfilFila(Long habilidadId, String nombre, String categoriaNombre, int nivelNumero, String declaradaDesdeLabel) {
        this.habilidadId = habilidadId;
        this.nombre = nombre;
        this.categoriaNombre = categoriaNombre;
        this.nivelNumero = nivelNumero;
        this.nivelLabel = switch (nivelNumero) {
            case 1, 2 -> "Básico";
            case 3 -> "Intermedio";
            case 4 -> "Avanzado";
            case 5 -> "Experto";
            default -> "—";
        };
        this.declaradaDesdeLabel = declaradaDesdeLabel;
    }

    public Long getHabilidadId() {
        return habilidadId;
    }

    public String getNombre() {
        return nombre;
    }

    public String getCategoriaNombre() {
        return categoriaNombre;
    }

    public int getNivelNumero() {
        return nivelNumero;
    }

    public String getNivelLabel() {
        return nivelLabel;
    }

    public String getDeclaradaDesdeLabel() {
        return declaradaDesdeLabel;
    }
}
