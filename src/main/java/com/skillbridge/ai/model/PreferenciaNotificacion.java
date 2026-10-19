package com.skillbridge.ai.model;

import jakarta.persistence.*;

/**
 * Mapea "preferencias_notificacion". Una fila por (perfil_id, tipo_evento);
 * el UNIQUE del esquema se respeta buscando-y-actualizando en vez de
 * insertar siempre (ver PreferenciaNotificacionService.actualizar).
 */
@Entity
@Table(name = "preferencias_notificacion")
public class PreferenciaNotificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "perfil_id", nullable = false)
    private Long perfilId;

    @Column(name = "tipo_evento", nullable = false, length = 30)
    private String tipoEvento;

    @Column(name = "canal", nullable = false, length = 20)
    private String canal;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getPerfilId() {
        return perfilId;
    }

    public void setPerfilId(Long perfilId) {
        this.perfilId = perfilId;
    }

    public String getTipoEvento() {
        return tipoEvento;
    }

    public void setTipoEvento(String tipoEvento) {
        this.tipoEvento = tipoEvento;
    }

    public String getCanal() {
        return canal;
    }

    public void setCanal(String canal) {
        this.canal = canal;
    }
}
