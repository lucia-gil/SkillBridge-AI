package com.nexacorp.skillbridge.model.id;

import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/** Llave primaria compuesta de perfil_habilidad: (perfil_id, habilidad_id). */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode
public class PerfilHabilidadId implements Serializable {

    private Long perfilId;
    private Long habilidadId;

    public PerfilHabilidadId(Long perfilId, Long habilidadId) {
        this.perfilId = perfilId;
        this.habilidadId = habilidadId;
    }
}
