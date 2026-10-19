package com.skillbridge.ai.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Mapea "foro_destacados": "me fue útil" de una persona sobre un mensaje
 * del foro (hilo raíz o respuesta). Una fila por (mensaje, perfil): la PK
 * compuesta impide destacar dos veces el mismo mensaje.
 */
@Entity
@Table(name = "foro_destacados")
@IdClass(ForoDestacadoId.class)
public class ForoDestacado {

    @Id
    @Column(name = "publicacion_id", nullable = false)
    private Long publicacionId;

    @Id
    @Column(name = "perfil_id", nullable = false)
    private Long perfilId;

    @Column(name = "fecha_creacion", insertable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    public ForoDestacado() {
    }

    public ForoDestacado(Long publicacionId, Long perfilId) {
        this.publicacionId = publicacionId;
        this.perfilId = perfilId;
    }

    public Long getPublicacionId() {
        return publicacionId;
    }

    public void setPublicacionId(Long publicacionId) {
        this.publicacionId = publicacionId;
    }

    public Long getPerfilId() {
        return perfilId;
    }

    public void setPerfilId(Long perfilId) {
        this.perfilId = perfilId;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }
}
