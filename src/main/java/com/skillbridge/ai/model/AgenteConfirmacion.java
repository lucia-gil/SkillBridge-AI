package com.skillbridge.ai.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Cambio que el agente IA dejó preparado y que la persona debe confirmar con
 * el botón (tabla agente_confirmaciones, ver db/agente.sql). Estados:
 * pendiente, ejecutada o cancelada. Vence a los pocos minutos (expira).
 */
@Entity
@Table(name = "agente_confirmaciones")
public class AgenteConfirmacion {

    public static final String PENDIENTE = "pendiente";
    public static final String EJECUTADA = "ejecutada";
    public static final String CANCELADA = "cancelada";

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(nullable = false, length = 30)
    private String rol;

    @Column(nullable = false, length = 80)
    private String herramienta;

    /** Argumentos validados de la herramienta, en JSON. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String argumentos;

    @Column(nullable = false, length = 20)
    private String estado = PENDIENTE;

    @Column(nullable = false)
    private Instant creada;

    @Column(nullable = false)
    private Instant expira;

    protected AgenteConfirmacion() {
    }

    public AgenteConfirmacion(String id, Long usuarioId, String rol, String herramienta, String argumentos,
                              Instant creada, Instant expira) {
        this.id = id;
        this.usuarioId = usuarioId;
        this.rol = rol;
        this.herramienta = herramienta;
        this.argumentos = argumentos;
        this.creada = creada;
        this.expira = expira;
    }

    public String getId() { return id; }
    public Long getUsuarioId() { return usuarioId; }
    public String getRol() { return rol; }
    public String getHerramienta() { return herramienta; }
    public String getArgumentos() { return argumentos; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public Instant getCreada() { return creada; }
    public Instant getExpira() { return expira; }
}
