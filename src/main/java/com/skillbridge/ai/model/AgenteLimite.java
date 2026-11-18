package com.skillbridge.ai.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Límite de solicitudes al agente por usuario (tabla agente_limites):
 * cuántas lleva en la ventana del minuto actual y un "lease" que impide
 * dos solicitudes simultáneas del mismo usuario.
 */
@Entity
@Table(name = "agente_limites")
public class AgenteLimite {

    @Id
    @Column(name = "usuario_id")
    private Long usuarioId;

    /** Minuto actual (segundos Unix / 60). */
    @Column(nullable = false)
    private Long ventana;

    @Column(nullable = false)
    private Integer cantidad;

    @Column(name = "lease_id", length = 36)
    private String leaseId;

    /** Segundos Unix hasta los que la solicitud en curso bloquea otra nueva. */
    @Column(name = "lease_hasta", nullable = false)
    private Long leaseHasta = 0L;

    protected AgenteLimite() {
    }

    public AgenteLimite(Long usuarioId, Long ventana) {
        this.usuarioId = usuarioId;
        this.ventana = ventana;
        this.cantidad = 0;
    }

    public Long getUsuarioId() { return usuarioId; }
    public Long getVentana() { return ventana; }
    public void setVentana(Long ventana) { this.ventana = ventana; }
    public Integer getCantidad() { return cantidad; }
    public void setCantidad(Integer cantidad) { this.cantidad = cantidad; }
    public String getLeaseId() { return leaseId; }
    public void setLeaseId(String leaseId) { this.leaseId = leaseId; }
    public Long getLeaseHasta() { return leaseHasta; }
    public void setLeaseHasta(Long leaseHasta) { this.leaseHasta = leaseHasta; }
}
