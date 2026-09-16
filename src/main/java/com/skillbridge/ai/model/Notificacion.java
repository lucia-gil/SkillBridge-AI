package com.skillbridge.ai.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Mapea "notificaciones" (RF09). Se crean como efecto secundario de otras
 * acciones reales de esta entrega (asignar un colaborador a un proyecto,
 * responder un hilo de foro) - ver NotificacionService.crear(...) y sus
 * llamadas desde ProyectoService/ForoService.
 */
@Entity
@Table(name = "notificaciones")
public class Notificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "perfil_id", nullable = false)
    private Long perfilId;

    @Column(name = "tipo_id", nullable = false)
    private Long tipoId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tipo_id", insertable = false, updatable = false)
    private TipoNotificacion tipo;

    @Column(name = "titulo", nullable = false, length = 150)
    private String titulo;

    @Column(name = "detalle", columnDefinition = "TEXT")
    private String detalle;

    @Column(name = "leida", nullable = false)
    private Boolean leida = false;

    @Column(name = "canal", nullable = false, length = 20)
    private String canal = "app";

    @Column(name = "enlace_accion", length = 255)
    private String enlaceAccion;

    @Column(name = "fecha_creacion", insertable = false, updatable = false)
    private LocalDateTime fechaCreacion;

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

    public Long getTipoId() {
        return tipoId;
    }

    public void setTipoId(Long tipoId) {
        this.tipoId = tipoId;
    }

    public TipoNotificacion getTipo() {
        return tipo;
    }

    public String getTitulo() {
        return titulo;
    }

    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }

    public String getDetalle() {
        return detalle;
    }

    public void setDetalle(String detalle) {
        this.detalle = detalle;
    }

    public Boolean getLeida() {
        return leida;
    }

    public void setLeida(Boolean leida) {
        this.leida = leida;
    }

    public String getCanal() {
        return canal;
    }

    public void setCanal(String canal) {
        this.canal = canal;
    }

    public String getEnlaceAccion() {
        return enlaceAccion;
    }

    public void setEnlaceAccion(String enlaceAccion) {
        this.enlaceAccion = enlaceAccion;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }
}
