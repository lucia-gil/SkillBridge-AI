package com.skillbridge.ai.model;

import java.io.Serializable;
import java.util.Objects;

/** Llave compuesta (publicacion_id, perfil_id) de "foro_destacados". */
public class ForoDestacadoId implements Serializable {

    private Long publicacionId;
    private Long perfilId;

    public ForoDestacadoId() {
    }

    public ForoDestacadoId(Long publicacionId, Long perfilId) {
        this.publicacionId = publicacionId;
        this.perfilId = perfilId;
    }

    public Long getPublicacionId() {
        return publicacionId;
    }

    public Long getPerfilId() {
        return perfilId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ForoDestacadoId that)) return false;
        return Objects.equals(publicacionId, that.publicacionId) && Objects.equals(perfilId, that.perfilId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(publicacionId, perfilId);
    }
}
