package com.skillbridge.ai.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Mapea "auditoria_logs". valor_anterior/valor_nuevo son columnas JSON en
 * MySQL; se mapean como String porque la aplicacion solo necesita
 * persistir/leer el texto JSON ya serializado, no consultarlo con
 * operadores JSON de MySQL.
 */
@Entity
@Table(name = "auditoria_logs")
public class AuditoriaLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(name = "accion", nullable = false, length = 100)
    private String accion;

    @Column(name = "entidad_afectada", length = 60)
    private String entidadAfectada;

    @Column(name = "entidad_id")
    private Long entidadId;

    @Column(name = "valor_anterior", columnDefinition = "json")
    private String valorAnterior;

    @Column(name = "valor_nuevo", columnDefinition = "json")
    private String valorNuevo;

    @Column(name = "detalle", columnDefinition = "TEXT")
    private String detalle;

    @Column(name = "fecha", insertable = false, updatable = false)
    private LocalDateTime fecha;

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

    public String getAccion() {
        return accion;
    }

    public void setAccion(String accion) {
        this.accion = accion;
    }

    public String getEntidadAfectada() {
        return entidadAfectada;
    }

    public void setEntidadAfectada(String entidadAfectada) {
        this.entidadAfectada = entidadAfectada;
    }

    public Long getEntidadId() {
        return entidadId;
    }

    public void setEntidadId(Long entidadId) {
        this.entidadId = entidadId;
    }

    public String getValorAnterior() {
        return valorAnterior;
    }

    public void setValorAnterior(String valorAnterior) {
        this.valorAnterior = valorAnterior;
    }

    public String getValorNuevo() {
        return valorNuevo;
    }

    public void setValorNuevo(String valorNuevo) {
        this.valorNuevo = valorNuevo;
    }

    public String getDetalle() {
        return detalle;
    }

    public void setDetalle(String detalle) {
        this.detalle = detalle;
    }

    public LocalDateTime getFecha() {
        return fecha;
    }
}
