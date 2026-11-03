package com.skillbridge.ai.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Mapea la tabla "perfiles" (RF02) - datos de negocio 1:1 con usuarios.
 */
@Entity
@Table(name = "perfiles")
public class Perfil {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false, unique = true)
    private Long usuarioId;

    // Asociacion de SOLO LECTURA hacia el mismo usuario_id: el campo
    // usuarioId de arriba sigue siendo el que se escribe (setUsuarioId, ya
    // usado por AuthService/UsuarioService); esta relacion evita tener que
    // hacer un segundo query manual a UsuarioRepository cada vez que una
    // pantalla de Proyectos/Foros/Notificaciones necesita el nombre_completo
    // o correo de la persona duena de un perfil.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", insertable = false, updatable = false)
    private Usuario usuario;

    @Column(name = "cargo", length = 100)
    private String cargo;

    @Column(name = "disponibilidad_porcentaje", nullable = false)
    private Integer disponibilidadPorcentaje = 100;

    @Column(name = "experiencia_anios", nullable = false)
    private Integer experienciaAnios = 0;

    @Column(name = "biografia", columnDefinition = "TEXT")
    private String biografia;

    @Column(name = "estado", nullable = false, length = 20)
    private String estado = "activo";

    @Column(name = "fecha_creacion", insertable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "linkedin_url", length = 500)
    private String linkedinUrl;

    @Column(name = "github_url", length = 500)
    private String githubUrl;

    // v9 (skillbridge_db.sql / InicializadorEsquemaV9):
    // datos de contacto y presentación que se ven en el perfil profesional.
    @Column(name = "portafolio_url", length = 500)
    private String portafolioUrl;

    @Column(name = "telefono", length = 30)
    private String telefono;

    @Column(name = "ubicacion", length = 120)
    private String ubicacion;

    /** Etiquetas cortas sobre la persona ("Mentor", "Inglés C1"...), arreglo JSON de textos. */
    @Column(name = "etiquetas", columnDefinition = "json")
    private String etiquetas;

    public Perfil() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public void setUsuarioId(Long usuarioId) {
        this.usuarioId = usuarioId;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public String getCargo() {
        return cargo;
    }

    public void setCargo(String cargo) {
        this.cargo = cargo;
    }

    public Integer getDisponibilidadPorcentaje() {
        return disponibilidadPorcentaje;
    }

    public void setDisponibilidadPorcentaje(Integer disponibilidadPorcentaje) {
        this.disponibilidadPorcentaje = disponibilidadPorcentaje;
    }

    public Integer getExperienciaAnios() {
        return experienciaAnios;
    }

    public void setExperienciaAnios(Integer experienciaAnios) {
        this.experienciaAnios = experienciaAnios;
    }

    public String getBiografia() {
        return biografia;
    }

    public void setBiografia(String biografia) {
        this.biografia = biografia;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public String getLinkedinUrl() {
        return linkedinUrl;
    }

    public void setLinkedinUrl(String linkedinUrl) {
        this.linkedinUrl = linkedinUrl;
    }

    public String getGithubUrl() {
        return githubUrl;
    }

    public void setGithubUrl(String githubUrl) {
        this.githubUrl = githubUrl;
    }

    public String getPortafolioUrl() {
        return portafolioUrl;
    }

    public void setPortafolioUrl(String portafolioUrl) {
        this.portafolioUrl = portafolioUrl;
    }

    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(String telefono) {
        this.telefono = telefono;
    }

    public String getUbicacion() {
        return ubicacion;
    }

    public void setUbicacion(String ubicacion) {
        this.ubicacion = ubicacion;
    }

    public String getEtiquetas() {
        return etiquetas;
    }

    public void setEtiquetas(String etiquetas) {
        this.etiquetas = etiquetas;
    }
}
