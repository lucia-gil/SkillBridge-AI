package com.skillbridge.ai.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Mapea la tabla "usuarios" (autenticacion, RF01) de skillbridge_db_v4.sql.
 *
 * rol_organizacional y estado se mantienen como String (no enum de Java)
 * a proposito: son columnas ENUM de MySQL con un set de valores cerrado y
 * documentado en {@link com.skillbridge.ai.util.Roles} / {@link com.skillbridge.ai.util.Estados};
 * usar String evita cualquier ambiguedad de mapeo @Enumerated (nombre vs
 * ordinal, mayusculas vs minusculas) que no se puede verificar compilando
 * en este entorno sin acceso a MySQL real.
 */
@Entity
@Table(name = "usuarios")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "correo", nullable = false, unique = true, length = 150)
    private String correo;

    @Column(name = "contrasena_hash", nullable = false, length = 255)
    private String contrasenaHash;

    @Column(name = "nombre_completo", nullable = false, length = 150)
    private String nombreCompleto;

    /**
     * Fotografia de perfil almacenada directamente en la tabla usuarios.
     * Se utiliza BLOB porque la aplicacion trabaja con la imagen como parte
     * de los datos de cuenta.
     */
    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "foto_perfil", columnDefinition = "MEDIUMBLOB")
    private byte[] fotoPerfil;

    /**
     * MIME type de la fotografia, por ejemplo:
     * image/jpeg
     * image/png
     */
    @Column(name = "foto_perfil_tipo", length = 50)
    private String fotoPerfilTipo;

    /**
     * Nombre original del archivo subido por el usuario.
     */
    @Column(name = "foto_perfil_nombre", length = 255)
    private String fotoPerfilNombre;

    /** NULL = sin puesto fijo de gestion global (colaborador / project manager segun proyecto). */
    @Column(name = "rol_organizacional", length = 30)
    private String rolOrganizacional;

    @Column(name = "estado", nullable = false, length = 20)
    private String estado = "activo";

    // Generadas por MySQL (DEFAULT CURRENT_TIMESTAMP / ON UPDATE CURRENT_TIMESTAMP);
    // no se escriben desde la aplicacion.
    @Column(name = "fecha_creacion", insertable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "fecha_actualizacion", insertable = false, updatable = false)
    private LocalDateTime fechaActualizacion;

    public Usuario() {
    }

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

    public String getContrasenaHash() {
        return contrasenaHash;
    }

    public void setContrasenaHash(String contrasenaHash) {
        this.contrasenaHash = contrasenaHash;
    }

    public String getNombreCompleto() {
        return nombreCompleto;
    }

    public void setNombreCompleto(String nombreCompleto) {
        this.nombreCompleto = nombreCompleto;
    }

    public byte[] getFotoPerfil() {
        return fotoPerfil;
    }

    public void setFotoPerfil(byte[] fotoPerfil) {
        this.fotoPerfil = fotoPerfil;
    }

    public String getFotoPerfilTipo() {
        return fotoPerfilTipo;
    }

    public void setFotoPerfilTipo(String fotoPerfilTipo) {
        this.fotoPerfilTipo = fotoPerfilTipo;
    }

    public String getFotoPerfilNombre() {
        return fotoPerfilNombre;
    }

    public void setFotoPerfilNombre(String fotoPerfilNombre) {
        this.fotoPerfilNombre = fotoPerfilNombre;
    }

    public String getRolOrganizacional() {
        return rolOrganizacional;
    }

    public void setRolOrganizacional(String rolOrganizacional) {
        this.rolOrganizacional = rolOrganizacional;
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

    public LocalDateTime getFechaActualizacion() {
        return fechaActualizacion;
    }

    public boolean isActivo() {
        return "activo".equalsIgnoreCase(estado);
    }

    /**
     * Indica si el usuario tiene una fotografia de perfil almacenada.
     */
    public boolean tieneFotoPerfil() {
        return fotoPerfil != null
                && fotoPerfil.length > 0
                && fotoPerfilTipo != null
                && !fotoPerfilTipo.isBlank();
    }
}