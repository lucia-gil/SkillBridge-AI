package com.skillbridge.ai.dto;

import org.springframework.security.core.AuthenticatedPrincipal;

import java.io.Serializable;

/**
 * Lo que se guarda en HttpSession bajo la clave "usuarioLogueado"
 * (mismo nombre de atributo que QuintaOla-SGA, por consistencia con el
 * proyecto de referencia). Liviano y serializable a proposito: no es la
 * entidad JPA completa, para no arrastrar el contexto de persistencia
 * dentro de la sesion HTTP.
 */
public class UsuarioSesion implements Serializable, AuthenticatedPrincipal {

    private static final long serialVersionUID = 1L;

    private Long usuarioId;
    private Long perfilId;
    private String correo;
    private String nombreCompleto;
    /** Valor crudo de usuarios.rol_organizacional (puede ser null). */
    private String rolOrganizacional;
    /** Rol calculado para navegacion: administrador | resource_manager | project_manager | colaborador. */
    private String rolEfectivo;
    private String iniciales;
    /**
     * Modelo v7: true si este Miembro lidera hoy al menos un proyecto. Junto
     * con rolOrganizacional decide a qué vistas puede entrar; rolEfectivo
     * pasa a ser "la vista en la que está navegando ahora" (la fija
     * SesionInterceptor según la ruta: /project-manager/** o /colaborador/**).
     */
    private boolean lideraProyectos;

    public boolean isLideraProyectos() {
        return lideraProyectos;
    }

    public void setLideraProyectos(boolean lideraProyectos) {
        this.lideraProyectos = lideraProyectos;
    }

    /** Miembro (sin rol fijo) que además lidera proyectos: puede alternar entre vista PM y Colaborador. */
    public boolean isPuedeCambiarVista() {
        return rolOrganizacional == null && lideraProyectos;
    }

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

    /**
     * Nombre del usuario para Spring Security: es el principal del
     * Authentication (ver security/SesionSegura). Spring Session lo guarda en
     * SPRING_SESSION.PRINCIPAL_NAME y sec:authentication="name" lo muestra.
     */
    @Override
    public String getName() {
        return correo;
    }
}
