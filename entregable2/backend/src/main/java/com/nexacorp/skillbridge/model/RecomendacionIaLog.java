package com.nexacorp.skillbridge.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Trazabilidad del AI Talent Matching (RF05): exige explicar el "por qué" de cada recomendación. */
@Entity
@Table(name = "recomendaciones_ia_log")
@Getter
@Setter
@NoArgsConstructor
public class RecomendacionIaLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proyecto_id", nullable = false)
    private Proyecto proyecto;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "perfil_recomendado_id", nullable = false)
    private Perfil perfilRecomendado;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "solicitado_por_id", nullable = false)
    private Perfil solicitadoPor;

    @Column(name = "puntaje_compatibilidad", nullable = false, precision = 5, scale = 2)
    private BigDecimal puntajeCompatibilidad;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String explicacion;

    @Column(name = "fue_asignado", nullable = false)
    private boolean fueAsignado = false;

    @Column(name = "fecha_generado", nullable = false, updatable = false)
    private LocalDateTime fechaGenerado;

    @PrePersist
    void onCreate() {
        this.fechaGenerado = LocalDateTime.now();
    }
}
