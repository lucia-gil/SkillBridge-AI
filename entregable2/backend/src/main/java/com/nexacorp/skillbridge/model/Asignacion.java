package com.nexacorp.skillbridge.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Rol contextual por proyecto (RF03/RF04). rol_en_proyecto SOLO puede ser
 * 'project_manager' o 'colaborador' — Administrador y Resource Manager no
 * "participan en" un proyecto de esa forma (existen por encima de todos los
 * proyectos a la vez, vía {@link Usuario#getRolOrganizacional()}).
 *
 * claveActiva es una columna GENERATED por la base de datos (ver
 * schema.sql): combina proyecto_id-perfil_id solo cuando estado='activa'.
 * Se mapea de solo lectura (insertable=false, updatable=false) — nunca se
 * setea desde Java, MySQL la calcula sola. Hibernate NO refresca este campo
 * automáticamente después de un insert/update (no se usó @Generated de
 * Hibernate para no depender de una versión exacta de su API sin poder
 * compilar y verificarlo en este entorno); si el código necesita leer el
 * valor recién calculado, hay que recargar la fila con
 * asignacionRepository.findById(id) o entityManager.refresh(...) después
 * de guardar.
 */
@Entity
@Table(name = "asignaciones")
@Getter
@Setter
@NoArgsConstructor
public class Asignacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proyecto_id", nullable = false)
    private Proyecto proyecto;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "perfil_id", nullable = false)
    private Perfil perfil;

    @Enumerated(EnumType.STRING)
    @Column(name = "rol_en_proyecto", nullable = false, length = 20)
    private RolEnProyecto rolEnProyecto;

    @Column(name = "carga_porcentaje", nullable = false)
    private int cargaPorcentaje;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private EstadoAsignacion estado = EstadoAsignacion.activa;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;

    @Column(name = "fecha_fin")
    private LocalDate fechaFin;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "clave_activa", insertable = false, updatable = false, length = 41)
    private String claveActiva;

    @PrePersist
    void onCreate() {
        this.fechaCreacion = LocalDateTime.now();
    }
}
