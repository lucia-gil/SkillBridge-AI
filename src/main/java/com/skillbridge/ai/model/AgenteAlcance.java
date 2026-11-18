package com.skillbridge.ai.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Huella de permisos con la que se creó una conversación del asistente
 * (tabla agente_alcances). Si el rol o las asignaciones de la persona
 * cambian, la huella deja de coincidir y la conversación queda oculta.
 */
@Entity
@Table(name = "agente_alcances")
public class AgenteAlcance {

    @Id
    @Column(name = "conversacion_id")
    private Long conversacionId;

    /** SHA-256 en hexadecimal (PoliticaAgente.firma). */
    @Column(nullable = false, length = 64)
    private String firma;

    protected AgenteAlcance() {
    }

    public AgenteAlcance(Long conversacionId, String firma) {
        this.conversacionId = conversacionId;
        this.firma = firma;
    }

    public Long getConversacionId() { return conversacionId; }
    public String getFirma() { return firma; }
}
