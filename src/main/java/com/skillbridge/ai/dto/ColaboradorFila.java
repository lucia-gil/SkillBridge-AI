package com.skillbridge.ai.dto;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Una fila de resource-manager/colaboradores.html: datos básicos +
 * ocupación (reusa la misma lógica de OcupacionColaboradorFila) + el
 * detalle completo de sus habilidades (con estado de validación) para que
 * el modal "Ver perfil" se arme en el navegador a partir de esta misma
 * fila, sin necesitar un endpoint AJAX aparte - mismo patrón que ya usa
 * resource-manager/asignaciones.html con la lista de PERFILES.
 */
public class ColaboradorFila {

    private final Long perfilId;
    private final String nombre;
    private final String iniciales;
    private final String correo;
    private final String cargo;
    private final int ocupacion;
    private final String estado;
    private final List<HabilidadPerfilFila> habilidades;
    private final boolean tieneFoto;
    private final Long usuarioId;

    public ColaboradorFila(Long perfilId, String nombre, String correo, String cargo,
                           int ocupacion, String estado, List<HabilidadPerfilFila> habilidades,
                           boolean tieneFoto, Long usuarioId) {
        this.perfilId = perfilId;
        this.nombre = nombre;
        this.iniciales = InicialesUtil.de(nombre);
        this.correo = correo;
        this.cargo = cargo != null ? cargo : "—";
        this.ocupacion = ocupacion;
        this.estado = estado;
        this.habilidades = habilidades;
        this.tieneFoto = tieneFoto;
        this.usuarioId = usuarioId;
    }

    public Long getPerfilId() {
        return perfilId;
    }

    public String getNombre() {
        return nombre;
    }

    public String getIniciales() {
        return iniciales;
    }

    public String getCorreo() {
        return correo;
    }

    public String getCargo() {
        return cargo;
    }

    public int getOcupacion() {
        return ocupacion;
    }

    public String getEstado() {
        return estado;
    }

    public String getEstadoLabel() {
        if ("activo".equals(estado)) return "Disponible";
        if ("cuenta_suspendida".equals(estado)) return "Cuenta suspendida";
        return "No disponible";
    }

    public String getEstadoBadgeClass() {
        if ("activo".equals(estado)) return "badge-green";
        if ("cuenta_suspendida".equals(estado)) return "badge-red";
        return "badge-amber";
    }

    public List<HabilidadPerfilFila> getHabilidades() {
        return habilidades;
    }

    public boolean isTieneFoto() { return tieneFoto; }
    public Long getUsuarioId() { return usuarioId; }

    /** Las 3 de mayor nivel, solo para mostrar como badges en la fila de la tabla. */
    public List<HabilidadPerfilFila> getHabilidadesTop() {
        return habilidades.stream()
                .sorted(Comparator.comparingInt(HabilidadPerfilFila::getNivelNumero).reversed())
                .limit(3)
                .collect(Collectors.toList());
    }
}