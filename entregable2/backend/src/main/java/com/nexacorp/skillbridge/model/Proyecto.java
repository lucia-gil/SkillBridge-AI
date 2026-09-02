package com.nexacorp.skillbridge.model;

import com.nexacorp.skillbridge.converter.StringListJsonConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** RF03. tecnologias es JSON (lista de strings) — etiqueta libre, no impacta el matching de habilidades. */
@Entity
@Table(name = "proyectos")
@Getter
@Setter
@NoArgsConstructor
public class Proyecto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(columnDefinition = "TEXT")
    private String descripcion;

    @Convert(converter = StringListJsonConverter.class)
    @Column(columnDefinition = "JSON")
    private List<String> tecnologias;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private EstadoProyecto estado = EstadoProyecto.planificacion;

    @Column(name = "colaboradores_requeridos", nullable = false)
    private int colaboradoresRequeridos = 0;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;

    @Column(name = "fecha_fin_estimada")
    private LocalDate fechaFinEstimada;

    /**
     * El PM que crea el proyecto. IMPORTANTE: crear un Proyecto por sí solo
     * NO basta — hay que crear también su fila en `asignaciones` con
     * rol_en_proyecto = project_manager, en la misma transacción (ver
     * {@link com.nexacorp.skillbridge.service.ProyectoService}).
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "creado_por_id", nullable = false)
    private Perfil creadoPor;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    @PrePersist
    void onCreate() {
        this.fechaCreacion = LocalDateTime.now();
    }
}
