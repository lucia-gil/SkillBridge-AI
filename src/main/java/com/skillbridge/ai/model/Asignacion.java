package com.skillbridge.ai.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Mapea "asignaciones" (RF03/RF04) - rol contextual de un perfil en un
 * proyecto. Es la unica fuente de verdad de quien es el Project Manager de
 * un proyecto (rol_en_proyecto='project_manager', estado='activa') y de
 * quienes son sus colaboradores.
 *
 * La columna GENERATED "clave_activa" (usada por la BD para bloquear dos
 * roles activos a la vez del mismo perfil en el mismo proyecto) no se
 * mapea aqui: la aplicacion nunca la lee ni la escribe, MySQL la calcula
 * sola a partir de proyecto_id/perfil_id/estado.
 */
@Entity
@Table(name = "asignaciones")
public class Asignacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "proyecto_id", nullable = false)
    private Long proyectoId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proyecto_id", insertable = false, updatable = false)
    private Proyecto proyecto;

    @Column(name = "perfil_id", nullable = false)
    private Long perfilId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "perfil_id", insertable = false, updatable = false)
    private Perfil perfil;

    @Column(name = "rol_en_proyecto", nullable = false, length = 20)
    private String rolEnProyecto;

    @Column(name = "carga_porcentaje", nullable = false)
    private Integer cargaPorcentaje;

    @Column(name = "estado", nullable = false, length = 20)
    private String estado = "activa";

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;

    @Column(name = "fecha_fin")
    private LocalDate fechaFin;

    @Column(name = "fecha_creacion", insertable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getProyectoId() {
        return proyectoId;
    }

    public void setProyectoId(Long proyectoId) {
        this.proyectoId = proyectoId;
    }

    public Proyecto getProyecto() {
        return proyecto;
    }

    public Long getPerfilId() {
        return perfilId;
    }

    public void setPerfilId(Long perfilId) {
        this.perfilId = perfilId;
    }

    public Perfil getPerfil() {
        return perfil;
    }

    public String getRolEnProyecto() {
        return rolEnProyecto;
    }

    public void setRolEnProyecto(String rolEnProyecto) {
        this.rolEnProyecto = rolEnProyecto;
    }

    public Integer getCargaPorcentaje() {
        return cargaPorcentaje;
    }

    public void setCargaPorcentaje(Integer cargaPorcentaje) {
        this.cargaPorcentaje = cargaPorcentaje;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public LocalDate getFechaInicio() {
        return fechaInicio;
    }

    public void setFechaInicio(LocalDate fechaInicio) {
        this.fechaInicio = fechaInicio;
    }

    public LocalDate getFechaFin() {
        return fechaFin;
    }

    public void setFechaFin(LocalDate fechaFin) {
        this.fechaFin = fechaFin;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }
}
