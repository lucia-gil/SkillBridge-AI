package com.skillbridge.ai.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Mapea "configuracion_global": tabla clave-valor (RF08, panel de
 * Administrador). "clave" es la llave primaria natural (String), tal como
 * está definida en el esquema - no hay un id numérico separado.
 */
@Entity
@Table(name = "configuracion_global")
public class ConfiguracionGlobal {

    @Id
    @Column(name = "clave", length = 80)
    private String clave;

    @Column(name = "valor", nullable = false, length = 255)
    private String valor;

    @Column(name = "descripcion", length = 255)
    private String descripcion;

    @Column(name = "actualizado_por_id")
    private Long actualizadoPorId;

    @Column(name = "fecha_actualizacion", insertable = false, updatable = false)
    private LocalDateTime fechaActualizacion;

    public String getClave() {
        return clave;
    }

    public void setClave(String clave) {
        this.clave = clave;
    }

    public String getValor() {
        return valor;
    }

    public void setValor(String valor) {
        this.valor = valor;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public Long getActualizadoPorId() {
        return actualizadoPorId;
    }

    public void setActualizadoPorId(Long actualizadoPorId) {
        this.actualizadoPorId = actualizadoPorId;
    }

    public LocalDateTime getFechaActualizacion() {
        return fechaActualizacion;
    }
}
