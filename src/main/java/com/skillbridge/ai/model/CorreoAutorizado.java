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

    @jakarta.persistence.Column(name="estado_envio")
    private String estadoEnvio;
    public String getEstadoEnvio() { return estadoEnvio; }
    public void setEstadoEnvio(String estadoEnvio) { this.estadoEnvio = estadoEnvio; }

    @Column(name = "ultimo_envio")
    private LocalDateTime ultimoEnvio;

    public LocalDateTime getUltimoEnvio() { return ultimoEnvio; }
    public void setUltimoEnvio(LocalDateTime ultimoEnvio) { this.ultimoEnvio = ultimoEnvio; }

    @Column(name = "fecha_uso")
    private LocalDateTime fechaUso;

    /**
     * Modelo v7 (fase 3): rol organizacional con el que se invitó
     * (administrador | resource_manager | null = Miembro). Al registrarse,
     * la cuenta nace con este rol. Columna creada por InicializadorEsquemaV7.
     */
    @Column(name = "rol_asignado", length = 20)
    private String rolAsignado;

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

    public String getRolAsignado() {
        return rolAsignado;
    }

    public void setRolAsignado(String rolAsignado) {
        this.rolAsignado = rolAsignado;
    }
}
