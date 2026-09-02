package com.nexacorp.skillbridge.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * RF06 — diferenciador: un resumen "vigente" por hilo (no hay historial de
 * versiones). Comparar fechaGenerado contra la fecha de la última respuesta
 * del hilo dice si quedó desactualizado.
 */
@Entity
@Table(name = "resumenes_ia")
@Getter
@Setter
@NoArgsConstructor
public class ResumenIa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "publicacion_id", nullable = false, unique = true)
    private ForoPublicacion publicacion;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String resumen;

    @Column(name = "modelo_utilizado", length = 50)
    private String modeloUtilizado;

    @Column(name = "fecha_generado", nullable = false)
    private LocalDateTime fechaGenerado;

    @PrePersist
    @PreUpdate
    void onSave() {
        this.fechaGenerado = LocalDateTime.now();
    }
}
