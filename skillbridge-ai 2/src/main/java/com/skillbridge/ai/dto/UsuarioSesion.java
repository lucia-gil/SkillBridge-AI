package com.skillbridge.ai.dto;

import java.io.Serializable;

/**
 * Lo que se guarda en HttpSession bajo la clave "usuarioLogueado"
 * (mismo nombre de atributo que QuintaOla-SGA, por consistencia con el
 * proyecto de referencia). Liviano y serializable a proposito: no es la
 * entidad JPA completa, para no arrastrar el contexto de persistencia
 * dentro de la sesion HTTP.
 */
public class UsuarioSesion implements Serializable {

    private Long usuarioId;
    private Long perfilId;
    private String correo;
    private String nombreCompleto;
    /** Valor crudo de usuarios.rol_organizacional (puede ser null). */
    private String rolOrganizacional;
    /** Rol calculado para navegacion: administrador | resource_manager | project_manager | colaborador. */
    private String rolEfectivo;
    private String iniciales;

    public Long getUsuarioId() {
        return usuarioId;
    }

    public void setUsuarioId(Long usuarioId) {
        this.usuarioId = usuarioId;
    }

    public Long getPerfilId() {
        return perfilId;
    }

    public void setPerfilId(Long perfilId) {
        this.perfilId = perfilId;
    }

    public String getCorreo() {
        return correo;
    }

    public void setCorreo(String correo) {
        this.correo = correo;
    }

    public String getNombreCompleto() {
        return nombreCompleto;
    }

    public void setNombreCompleto(String nombreCompleto) {
        this.nombreCompleto = nombreCompleto;
    }

    public String getRolOrganizacional() {
        return rolOrganizacional;
    }

    public void setRolOrganizacional(String rolOrganizacional) {
        this.rolOrganizacional = rolOrganizacional;
    }

    public String getRolEfectivo() {
        return rolEfectivo;
    }

    public void setRolEfectivo(String rolEfectivo) {
        this.rolEfectivo = rolEfectivo;
    }

    public String getIniciales() {
        return iniciales;
    }

    public void setIniciales(String iniciales) {
        this.iniciales = iniciales;
    }
}
