package com.skillbridge.ai.dto;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Fila de la tabla administrador/usuarios.html, ya lista para pintar (evita exponer la entidad/hash en la vista). */
public class UsuarioFila {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final Long id;
    private final String nombreCompleto;
    private final String correo;
    private final String iniciales;
    private final String cargo;
    private final String rolLabel;
    private final String rolBadgeClass;
    private final String rolOrganizacionalCrudo; // "administrador" | "resource_manager" | null - para preseleccionar el <select> de editar rol
    private final String estado; // "activo" | "inactivo"
    private final String estadoLabel;
    private final String estadoBadgeClass;
    private final String cuentaDesde;

    public UsuarioFila(Long id, String nombreCompleto, String correo, String cargo,
                        String rolOrganizacionalCrudo, String estado, LocalDateTime fechaCreacion) {
        this.id = id;
        this.nombreCompleto = nombreCompleto;
        this.correo = correo;
        this.iniciales = calcularIniciales(nombreCompleto);
        this.cargo = (cargo == null || cargo.isBlank()) ? "—" : cargo;
        this.rolOrganizacionalCrudo = rolOrganizacionalCrudo;

        if ("administrador".equals(rolOrganizacionalCrudo)) {
            this.rolLabel = "Administrador";
            this.rolBadgeClass = "badge-blue";
        } else if ("resource_manager".equals(rolOrganizacionalCrudo)) {
            this.rolLabel = "Resource Manager";
            this.rolBadgeClass = "badge-neutral";
        } else {
            this.rolLabel = "Sin rol fijo";
            this.rolBadgeClass = "badge-neutral";
        }

        this.estado = estado;
        boolean activo = "activo".equalsIgnoreCase(estado);
        this.estadoLabel = activo ? "Activo" : "Bloqueado";
        this.estadoBadgeClass = activo ? "badge-green" : "badge-red";
        this.cuentaDesde = fechaCreacion != null ? fechaCreacion.format(FMT) : "—";
    }

    private static String calcularIniciales(String nombre) {
        if (nombre == null || nombre.isBlank()) return "??";
        String[] partes = nombre.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < partes.length && sb.length() < 2; i++) {
            if (!partes[i].isEmpty()) sb.append(Character.toUpperCase(partes[i].charAt(0)));
        }
        return sb.length() > 0 ? sb.toString() : "??";
    }

    public Long getId() {
        return id;
    }

    public String getNombreCompleto() {
        return nombreCompleto;
    }

    public String getCorreo() {
        return correo;
    }

    public String getIniciales() {
        return iniciales;
    }

    public String getCargo() {
        return cargo;
    }

    public String getRolLabel() {
        return rolLabel;
    }

    public String getRolBadgeClass() {
        return rolBadgeClass;
    }

    public String getRolOrganizacionalCrudo() {
        return rolOrganizacionalCrudo;
    }

    public String getEstado() {
        return estado;
    }

    public String getEstadoLabel() {
        return estadoLabel;
    }

    public String getEstadoBadgeClass() {
        return estadoBadgeClass;
    }

    public String getCuentaDesde() {
        return cuentaDesde;
    }
}
