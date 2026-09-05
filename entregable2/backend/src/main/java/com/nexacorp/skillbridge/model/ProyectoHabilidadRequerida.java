package com.nexacorp.skillbridge.model;

import com.nexacorp.skillbridge.model.id.ProyectoHabilidadRequeridaId;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** RF03, insumo de RF05 (AI Talent Matching). */
@Entity
@Table(name = "proyecto_habilidad_requerida")
@Getter
@Setter
@NoArgsConstructor
public class ProyectoHabilidadRequerida {

    @EmbeddedId
    private ProyectoHabilidadRequeridaId id = new ProyectoHabilidadRequeridaId();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("proyectoId")
    @JoinColumn(name = "proyecto_id", nullable = false)
    private Proyecto proyecto;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("habilidadId")
    @JoinColumn(name = "habilidad_id", nullable = false)
    private Habilidad habilidad;

    /** 1 a 5 (CHECK ck_phr_nivel en la base de datos). */
    @Column(name = "nivel_requerido", nullable = false)
    private int nivelRequerido;
}
