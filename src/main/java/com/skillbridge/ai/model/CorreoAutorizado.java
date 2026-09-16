package com.skillbridge.ai.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Mapea "correos_autorizados": lista blanca de registro. Nadie se
 * autorregistra libremente - un Administrador autoriza el correo antes
 * (fila aqui) y recien entonces esa persona puede completar /registro.
 */
@Entity
@Table(name = "correos_autorizados")
public class CorreoAutorizado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "correo", nullable = false, unique = true, length = 150)
    private String correo;

    @Column(name = "autorizado_por_id", nullable = false)
    private Long autorizadoPorId;

    @Column(name = "origen_carga", nullable = false, length = 20)
    private String origenCarga = "individual";

    @Column(name = "utilizado", nullable = false)
    private Boolean utilizado = false;

    @Column(name = "fecha_autorizacion", insertable = false, updatable = false)
    private LocalDateTime fechaAutorizacion;

    @Column(name = "fecha_uso")
    private LocalDateTime fechaUso;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCorreo() {
        return correo;
    }

    public void setCorreo(String correo) {
        this.correo = correo;
    }

    public Long getAutorizadoPorId() {
        return autorizadoPorId;
    }

    public void setAutorizadoPorId(Long autorizadoPorId) {
        this.autorizadoPorId = autorizadoPorId;
    }

    public String getOrigenCarga() {
        return origenCarga;
    }

    public void setOrigenCarga(String origenCarga) {
        this.origenCarga = origenCarga;
    }

    public Boolean getUtilizado() {
        return utilizado;
    }

    public void setUtilizado(Boolean utilizado) {
        this.utilizado = utilizado;
    }

    public LocalDateTime getFechaAutorizacion() {
        return fechaAutorizacion;
    }

    public LocalDateTime getFechaUso() {
        return fechaUso;
    }

    public void setFechaUso(LocalDateTime fechaUso) {
        this.fechaUso = fechaUso;
    }
}
