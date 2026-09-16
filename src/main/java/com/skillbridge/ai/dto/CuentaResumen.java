package com.skillbridge.ai.dto;

/** Datos de administrador/mi-cuenta.html y colaborador/mi-cuenta.html. */
public class CuentaResumen {

    private final String nombreCompleto;
    private final String correo;
    private final String cargo;
    private final String biografia;
    private final int experienciaAnios;
    private final int disponibilidadPorcentaje;
    private final String rolLabel;
    private final String iniciales;
    private final String cuentaDesdeLabel;

    public CuentaResumen(String nombreCompleto, String correo, String cargo, String biografia, int experienciaAnios,
                          int disponibilidadPorcentaje, String rolLabel, String cuentaDesdeLabel) {
        this.nombreCompleto = nombreCompleto;
        this.correo = correo;
        this.cargo = cargo != null ? cargo : "";
        this.biografia = biografia != null ? biografia : "";
        this.experienciaAnios = experienciaAnios;
        this.disponibilidadPorcentaje = disponibilidadPorcentaje;
        this.rolLabel = rolLabel;
        this.iniciales = InicialesUtil.de(nombreCompleto);
        this.cuentaDesdeLabel = cuentaDesdeLabel;
    }

    public String getNombreCompleto() {
        return nombreCompleto;
    }

    public String getCorreo() {
        return correo;
    }

    public String getCargo() {
        return cargo;
    }

    public String getBiografia() {
        return biografia;
    }

    public int getExperienciaAnios() {
        return experienciaAnios;
    }

    public int getDisponibilidadPorcentaje() {
        return disponibilidadPorcentaje;
    }

    public String getRolLabel() {
        return rolLabel;
    }

    public String getIniciales() {
        return iniciales;
    }

    public String getCuentaDesdeLabel() {
        return cuentaDesdeLabel;
    }
}
