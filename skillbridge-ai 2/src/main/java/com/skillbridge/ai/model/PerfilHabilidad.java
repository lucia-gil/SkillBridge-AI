package com.skillbridge.ai.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Mapea "perfil_habilidad" - habilidades declaradas por un colaborador
 * (usada al registrarse, paso 2 del wizard de auth/registro.html).
 */
@Entity
@Table(name = "perfil_habilidad")
public class PerfilHabilidad {

    @EmbeddedId
    private PerfilHabilidadId id;

    @Column(name = "nivel", nullable = false)
    private Integer nivel;

    @Column(name = "validado_por_id")
    private Long validadoPorId;

    @Column(name = "fecha_declaracion", insertable = false, updatable = false)
    private LocalDateTime fechaDeclaracion;

    public PerfilHabilidad() {
    }

    public PerfilHabilidad(Long perfilId, Long habilidadId, Integer nivel) {
        this.id = new PerfilHabilidadId(perfilId, habilidadId);
        this.nivel = nivel;
    }

    public PerfilHabilidadId getId() {
        return id;
    }

    public void setId(PerfilHabilidadId id) {
        this.id = id;
    }

    public Integer getNivel() {
        return nivel;
    }

    public void setNivel(Integer nivel) {
        this.nivel = nivel;
    }

    public Long getValidadoPorId() {
        return validadoPorId;
    }

    public void setValidadoPorId(Long validadoPorId) {
        this.validadoPorId = validadoPorId;
    }

    public LocalDateTime getFechaDeclaracion() {
        return fechaDeclaracion;
    }
}
