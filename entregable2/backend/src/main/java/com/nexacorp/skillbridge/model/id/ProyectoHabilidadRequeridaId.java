package com.nexacorp.skillbridge.model.id;

import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/** Llave primaria compuesta de proyecto_habilidad_requerida: (proyecto_id, habilidad_id). */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode
public class ProyectoHabilidadRequeridaId implements Serializable {

    private Long proyectoId;
    private Long habilidadId;

    public ProyectoHabilidadRequeridaId(Long proyectoId, Long habilidadId) {
        this.proyectoId = proyectoId;
        this.habilidadId = habilidadId;
    }
}
