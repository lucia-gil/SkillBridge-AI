package com.skillbridge.ai.model;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Mapea "proyecto_habilidad_requerida". Solo se usa de forma minima aqui
 * (contar cuantos proyectos requieren una habilidad, para el catalogo de
 * habilidades) - el CRUD de Proyectos queda fuera del alcance de esta
 * entrega.
 */
@Entity
@Table(name = "proyecto_habilidad_requerida")
public class ProyectoHabilidadRequerida {

    @EmbeddedId
    private ProyectoHabilidadRequeridaId id;

    public ProyectoHabilidadRequeridaId getId() {
        return id;
    }

    public void setId(ProyectoHabilidadRequeridaId id) {
        this.id = id;
    }
}
