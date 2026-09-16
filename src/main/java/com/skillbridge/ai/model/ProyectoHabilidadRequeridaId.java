package com.skillbridge.ai.model;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

/** Llave compuesta (proyecto_id, habilidad_id) de "proyecto_habilidad_requerida". */
@Embeddable
public class ProyectoHabilidadRequeridaId implements Serializable {

    private Long proyectoId;
    private Long habilidadId;

    public ProyectoHabilidadRequeridaId() {
    }

    public Long getProyectoId() {
        return proyectoId;
    }

    public void setProyectoId(Long proyectoId) {
        this.proyectoId = proyectoId;
    }

    public Long getHabilidadId() {
        return habilidadId;
    }

    public void setHabilidadId(Long habilidadId) {
        this.habilidadId = habilidadId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProyectoHabilidadRequeridaId)) return false;
        ProyectoHabilidadRequeridaId that = (ProyectoHabilidadRequeridaId) o;
        return Objects.equals(proyectoId, that.proyectoId) && Objects.equals(habilidadId, that.habilidadId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(proyectoId, habilidadId);
    }
}
