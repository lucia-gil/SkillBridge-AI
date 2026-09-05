package com.nexacorp.skillbridge.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** Lista blanca de registro (RF01/RF02): nadie se autoregistra sin que un Administrador autorice antes su correo. */
@Entity
@Table(name = "correos_autorizados")
@Getter
@Setter
@NoArgsConstructor
public class CorreoAutorizado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 150)
    private String correo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "autorizado_por_id", nullable = false)
    private Usuario autorizadoPor;

    @Enumerated(EnumType.STRING)
    @Column(name = "origen_carga", nullable = false, length = 15)
    private OrigenCarga origenCarga = OrigenCarga.individual;

    @Column(nullable = false)
    private boolean utilizado = false;

    @Column(name = "fecha_autorizacion", nullable = false, updatable = false)
    private LocalDateTime fechaAutorizacion;

    @Column(name = "fecha_uso")
    private LocalDateTime fechaUso;

    @PrePersist
    void onCreate() {
        this.fechaAutorizacion = LocalDateTime.now();
    }
}
