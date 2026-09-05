package com.nexacorp.skillbridge.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * RF04 — límite de carga por colaborador. No está en el documento del JP;
 * se mantiene porque RF04 lo exige explícitamente ("evitar asignaciones que
 * superen los límites definidos por la organización") y los mockups lo
 * muestran (alerta "Tomás Herrera (120%)... Solicitar excepción").
 */
@Entity
@Table(name = "excepciones_carga")
@Getter
@Setter
@NoArgsConstructor
public class ExcepcionCarga {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asignacion_id", nullable = false)
    private Asignacion asignacion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "solicitado_por_id", nullable = false)
    private Perfil solicitadoPor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "aprobado_por_id")
    private Perfil aprobadoPor;

    @Column(name = "porcentaje_aprobado", nullable = false)
    private int porcentajeAprobado;

    @Column(name = "fecha_limite", nullable = false)
    private LocalDate fechaLimite;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private EstadoExcepcion estado = EstadoExcepcion.pendiente;

    @Column(columnDefinition = "TEXT")
    private String motivo;

    @Column(name = "fecha_solicitud", nullable = false, updatable = false)
    private LocalDateTime fechaSolicitud;

    @Column(name = "fecha_resolucion")
    private LocalDateTime fechaResolucion;

    @PrePersist
    void onCreate() {
        this.fechaSolicitud = LocalDateTime.now();
    }
}
