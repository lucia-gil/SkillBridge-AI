package com.skillbridge.ai.model;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Objects;

/**
 * Hasta qué mensaje leyó cada persona en cada sala (tabla chat_lecturas).
 * Con esto se calcula el contador de "no leídos" del sidebar y de la lista de salas.
 */
@Entity
@Table(name = "chat_lecturas")
public class ChatLectura {

    @EmbeddedId
    private Id id;

    @Column(name = "ultimo_mensaje_id", nullable = false)
    private Long ultimoMensajeId;

    public ChatLectura() {}

    public ChatLectura(Long salaId, Long perfilId, Long ultimoMensajeId) {
        this.id = new Id(salaId, perfilId);
        this.ultimoMensajeId = ultimoMensajeId;
    }

    public Id getId() { return id; }
    public Long getUltimoMensajeId() { return ultimoMensajeId; }
    public void setUltimoMensajeId(Long ultimoMensajeId) { this.ultimoMensajeId = ultimoMensajeId; }

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
