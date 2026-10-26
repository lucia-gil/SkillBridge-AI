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
    private final String linkedinUrl;
    private final String githubUrl;

    public CuentaResumen(String nombreCompleto, String correo, String cargo, String biografia, int experienciaAnios,
                          int disponibilidadPorcentaje, String rolLabel, String cuentaDesdeLabel,
                          String linkedinUrl, String githubUrl) {
        this.nombreCompleto = nombreCompleto;
        this.correo = correo;
        this.cargo = cargo != null ? cargo : "";
        this.biografia = biografia != null ? biografia : "";
        this.experienciaAnios = experienciaAnios;
        this.disponibilidadPorcentaje = disponibilidadPorcentaje;
        this.rolLabel = rolLabel;
        this.iniciales = InicialesUtil.de(nombreCompleto);
        this.cuentaDesdeLabel = cuentaDesdeLabel;
        this.linkedinUrl = linkedinUrl != null ? linkedinUrl : "";
        this.githubUrl = githubUrl != null ? githubUrl : "";
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

    public String getLinkedinUrl() { return linkedinUrl; }
    public String getGithubUrl() { return githubUrl; }

    // ── v9: contacto y presentación (opcionales; se completan con setters
    //    para no cambiar el constructor que ya usan las otras pantallas) ──
    private String portafolioUrl = "";
    private String telefono = "";
    private String ubicacion = "";
    private java.util.List<String> etiquetas = java.util.List.of();

    public String getPortafolioUrl() { return portafolioUrl; }
    public void setPortafolioUrl(String v) { this.portafolioUrl = v != null ? v : ""; }
    public String getTelefono() { return telefono; }
    public void setTelefono(String v) { this.telefono = v != null ? v : ""; }
    public String getUbicacion() { return ubicacion; }
    public void setUbicacion(String v) { this.ubicacion = v != null ? v : ""; }
    public java.util.List<String> getEtiquetas() { return etiquetas; }
    public void setEtiquetas(java.util.List<String> v) { this.etiquetas = v != null ? v : java.util.List.of(); }
    /** Las etiquetas como las escribe el usuario en el formulario: separadas por coma. */
    public String getEtiquetasTexto() { return String.join(", ", etiquetas); }
}
