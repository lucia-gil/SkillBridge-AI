package com.skillbridge.ai.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Fila única (id = 1) de la tabla agente_mutex. Se bloquea con
 * SELECT ... FOR UPDATE (AgenteMutexRepository.bloquear) para que las
 * escrituras del agente se ejecuten de a una, aunque haya varias
 * instancias de la aplicación contra la misma base.
 */
@Entity
@Table(name = "agente_mutex")
public class AgenteMutex {

    @Id
    private Integer id;

    protected AgenteMutex() {
    }

    public Integer getId() { return id; }
}
