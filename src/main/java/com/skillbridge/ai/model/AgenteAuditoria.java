package com.skillbridge.ai.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Registro de cada operación del agente (tabla agente_auditoria). Nunca
 * guarda prompts, respuestas ni argumentos: solo quién, qué herramienta y
 * el resultado (CONSULTADA, PREPARADA, EJECUTADA, CANCELADA, DENEGADA).
 */
@Entity
@Table(name = "agente_auditoria")
public class AgenteAuditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(nullable = false, length = 80)
    private String herramienta;

    @Column(nullable = false, length = 30)
    private String resultado;

    /** Id de la confirmación relacionada, si la hay. */
    @Column(length = 36)
    private String correlacion;

    @Column(insertable = false, updatable = false)
    private LocalDateTime fecha;

    protected AgenteAuditoria() {
    }

    public AgenteAuditoria(Long usuarioId, String herramienta, String resultado, String correlacion) {
        this.usuarioId = usuarioId;
        this.herramienta = herramienta;
        this.resultado = resultado;
        this.correlacion = correlacion;
    }

    public Long getId() { return id; }
    public Long getUsuarioId() { return usuarioId; }
    public String getHerramienta() { return herramienta; }
    public String getResultado() { return resultado; }
    public String getCorrelacion() { return correlacion; }
    public LocalDateTime getFecha() { return fecha; }
}
