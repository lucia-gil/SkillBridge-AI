package com.skillbridge.ai.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;

/** Una de las dos personas de una sala de mensajes directos (tabla chat_participantes). */
@Entity
@Table(name = "chat_participantes")
public class ChatParticipante {

    @EmbeddedId
    private Id id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "perfil_id", insertable = false, updatable = false)
    private Perfil perfil;

    public ChatParticipante() {}

    public ChatParticipante(Long salaId, Long perfilId) {
        this.id = new Id(salaId, perfilId);
    }

    public Id getId() { return id; }
    public Long getSalaId() { return id.getSalaId(); }
    public Long getPerfilId() { return id.getPerfilId(); }
    public Perfil getPerfil() { return perfil; }

    @Embeddable
    public static class Id implements Serializable {
        @Column(name = "sala_id")
        private Long salaId;
        @Column(name = "perfil_id")
        private Long perfilId;

        public Id() {}
        public Id(Long salaId, Long perfilId) { this.salaId = salaId; this.perfilId = perfilId; }
        public Long getSalaId() { return salaId; }
        public Long getPerfilId() { return perfilId; }

        @Override public boolean equals(Object o) {
            return o instanceof Id x && Objects.equals(salaId, x.salaId) && Objects.equals(perfilId, x.perfilId);
        }
        @Override public int hashCode() { return Objects.hash(salaId, perfilId); }
    }
}
