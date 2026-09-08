package com.skillbridge.ai.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Mapea la tabla "perfiles" (RF02) - datos de negocio 1:1 con usuarios.
 */
@Entity
@Table(name = "perfiles")
public class Perfil {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false, unique = true)
    private Long usuarioId;

    @Column(name = "cargo", length = 100)
    private String cargo;

    @Column(name = "disponibilidad_porcentaje", nullable = false)
    private Integer disponibilidadPorcentaje = 100;

    @Column(name = "experiencia_anios", nullable = false)
    private Integer experienciaAnios = 0;

    @Column(name = "biografia", columnDefinition = "TEXT")
    private String biografia;

    @Column(name = "estado", nullable = false, length = 20)
    private String estado = "activo";

    @Column(name = "fecha_creacion", insertable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    public Perfil() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public void setUsuarioId(Long usuarioId) {
        this.usuarioId = usuarioId;
    }

    public String getCargo() {
        return cargo;
    }

    public void setCargo(String cargo) {
        this.cargo = cargo;
    }

    public Integer getDisponibilidadPorcentaje() {
        return disponibilidadPorcentaje;
    }

    public void setDisponibilidadPorcentaje(Integer disponibilidadPorcentaje) {
        this.disponibilidadPorcentaje = disponibilidadPorcentaje;
    }

    public Integer getExperienciaAnios() {
        return experienciaAnios;
    }

    public void setExperienciaAnios(Integer experienciaAnios) {
        this.experienciaAnios = experienciaAnios;
    }

    public String getBiografia() {
        return biografia;
    }

    public void setBiografia(String biografia) {
        this.biografia = biografia;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }
}
