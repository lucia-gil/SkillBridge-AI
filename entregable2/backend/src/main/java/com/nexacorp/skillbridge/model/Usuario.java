package com.nexacorp.skillbridge.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * RF01 — solo datos de login. NO tiene nombre ni nada mostrable en
 * pantalla: eso vive en {@link Perfil} (1:1). Cualquier campo que se vaya a
 * mostrar en la interfaz apunta a Perfil, no a Usuario.
 */
@Entity
@Table(name = "usuarios")
@Getter
@Setter
@NoArgsConstructor
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Email
    @NotBlank
    @Column(nullable = false, unique = true, length = 150)
    private String correo;

    @NotBlank
    @Column(name = "contrasena_hash", nullable = false, length = 255)
    private String contrasenaHash;

    /** NULL en la mayoría de los usuarios: ver {@link RolOrganizacional}. */
    @Enumerated(EnumType.STRING)
    @Column(name = "rol_organizacional", length = 20)
    private RolOrganizacional rolOrganizacional;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private EstadoCuenta estado = EstadoCuenta.activo;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "fecha_actualizacion", nullable = false)
    private LocalDateTime fechaActualizacion;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.fechaCreacion = now;
        this.fechaActualizacion = now;
    }

    @PreUpdate
    void onUpdate() {
        this.fechaActualizacion = LocalDateTime.now();
    }
}
