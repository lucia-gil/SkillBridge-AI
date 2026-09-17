package com.skillbridge.ai.model;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

/** Llave compuesta (perfil_id, habilidad_id) de "perfil_habilidad". */
@Embeddable
public class PerfilHabilidadId implements Serializable {

    private Long perfilId;
    private Long habilidadId;

    public PerfilHabilidadId() {
    }

    public PerfilHabilidadId(Long perfilId, Long habilidadId) {
        this.perfilId = perfilId;
        this.habilidadId = habilidadId;
    }

    public Long getPerfilId() {
        return perfilId;
    }

    public void setPerfilId(Long perfilId) {
        this.perfilId = perfilId;
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
        if (!(o instanceof PerfilHabilidadId)) return false;
        PerfilHabilidadId that = (PerfilHabilidadId) o;
        return Objects.equals(perfilId, that.perfilId) && Objects.equals(habilidadId, that.habilidadId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(perfilId, habilidadId);
    }
}
