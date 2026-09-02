package com.nexacorp.skillbridge.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * usuario apunta a {@link Usuario}, NO a {@link Perfil}: son eventos de
 * sesión/seguridad, que pueden ocurrir antes de que exista un perfil de
 * negocio (ej. un intento de login).
 *
 * entidadId es una referencia "genérica" (puede apuntar a cualquier tabla
 * según entidadAfectada) — sin FK real, trade-off consciente: la
 * integridad de este campo depende del código, no de la base de datos.
 */
@Entity
@Table(name = "auditoria_logs")
@Getter
@Setter
@NoArgsConstructor
public class AuditoriaLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(nullable = false, length = 100)
    private String accion;

    @Column(name = "entidad_afectada", length = 60)
    private String entidadAfectada;

    @Column(name = "entidad_id")
    private Long entidadId;

    @Column(columnDefinition = "TEXT")
    private String detalle;

    @Column(nullable = false, updatable = false)
    private LocalDateTime fecha;

    @PrePersist
    void onCreate() {
        this.fecha = LocalDateTime.now();
    }
}
