package com.nexacorp.skillbridge.model;

import com.nexacorp.skillbridge.model.id.PerfilHabilidadId;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** N:M perfil↔habilidad (RF02/RF08). nivel es una escala 1-5 (ver informe, reconciliación con los mockups). */
@Entity
@Table(name = "perfil_habilidad")
@Getter
@Setter
@NoArgsConstructor
public class PerfilHabilidad {

    @EmbeddedId
    private PerfilHabilidadId id = new PerfilHabilidadId();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("perfilId")
    @JoinColumn(name = "perfil_id", nullable = false)
    private Perfil perfil;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("habilidadId")
    @JoinColumn(name = "habilidad_id", nullable = false)
    private Habilidad habilidad;

    /** 1 a 5 (CHECK ck_ph_nivel en la base de datos). */
    @Column(nullable = false)
    private int nivel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "validado_por_id")
    private Perfil validadoPor;

    @Column(name = "fecha_declaracion", nullable = false, updatable = false)
    private LocalDateTime fechaDeclaracion;

    @PrePersist
    void onCreate() {
        this.fechaDeclaracion = LocalDateTime.now();
    }
}
