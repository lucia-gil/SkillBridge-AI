package com.nexacorp.skillbridge.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** RF08, panel de Administrador. Tabla clave-valor: ~13 parámetros heterogéneos. */
@Entity
@Table(name = "configuracion_global")
@Getter
@Setter
@NoArgsConstructor
public class ConfiguracionGlobal {

    @Id
    @Column(length = 80)
    private String clave;

    @Column(nullable = false, length = 255)
    private String valor;

    @Column(length = 255)
    private String descripcion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actualizado_por_id")
    private Perfil actualizadoPor;

    @Column(name = "fecha_actualizacion", nullable = false)
    private LocalDateTime fechaActualizacion;

    @PrePersist
    @PreUpdate
    void onSave() {
        this.fechaActualizacion = LocalDateTime.now();
    }
}
