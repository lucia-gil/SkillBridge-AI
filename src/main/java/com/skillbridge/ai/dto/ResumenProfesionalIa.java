package com.skillbridge.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Resumen profesional que arma Claude a partir de las constancias (PDF y
 * links) que el colaborador adjuntó a sus habilidades: una especie de CV
 * corto para que el Resource Manager y el Project Manager no tengan que
 * abrir cada archivo. Se guarda como JSON en perfil_resumen_ia.contenido_json
 * y el colaborador puede editar cualquier parte.
 *
 * Los campos marcados con @JsonIgnore no se guardan: los completa
 * ResumenProfesionalIaService al leerlo (fechas, si quedó desactualizado).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ResumenProfesionalIa {

    /** Una línea tipo "Desarrollador frontend con base sólida en React y Docker". */
    private String titular;
    /** 3 a 5 líneas en tercera persona. */
    private String resumen;
    /** Puntos fuertes respaldados por las constancias (viñetas cortas). */
    private List<String> fortalezas = new ArrayList<>();
    /** Qué aporta cada constancia, por habilidad. */
    private List<Evidencia> evidencias = new ArrayList<>();
    /** Sugerencias para reforzar el perfil (habilidades sin respaldo, niveles a acreditar). */
    private List<String> sugerencias = new ArrayList<>();

    @JsonIgnore private String generadoEnLabel;
    @JsonIgnore private String editadoEnLabel;
    @JsonIgnore private int fuentesUsadas;
    @JsonIgnore private boolean desactualizado;

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Evidencia {
        private String habilidad;
        /** Nombre corto de la constancia: "Certificado de Coursera", "curso-react.pdf"... */
        private String fuente;
        /** "pdf", "link" o "pdf+link". */
        private String tipo;
        /** Qué acredita, en una o dos líneas. */
        private String detalle;

        public Evidencia() {}

        public Evidencia(String habilidad, String fuente, String tipo, String detalle) {
            this.habilidad = habilidad;
            this.fuente = fuente;
            this.tipo = tipo;
            this.detalle = detalle;
        }

        public String getHabilidad() { return habilidad; }
        public void setHabilidad(String habilidad) { this.habilidad = habilidad; }
        public String getFuente() { return fuente; }
        public void setFuente(String fuente) { this.fuente = fuente; }
        public String getTipo() { return tipo; }
        public void setTipo(String tipo) { this.tipo = tipo; }
        public String getDetalle() { return detalle; }
        public void setDetalle(String detalle) { this.detalle = detalle; }

        @JsonIgnore
        public boolean isEsPdf() { return tipo != null && tipo.contains("pdf"); }
    }

    public String getTitular() { return titular; }
    public void setTitular(String titular) { this.titular = titular; }
    public String getResumen() { return resumen; }
    public void setResumen(String resumen) { this.resumen = resumen; }
    public List<String> getFortalezas() { return fortalezas; }
    public void setFortalezas(List<String> fortalezas) { this.fortalezas = fortalezas != null ? fortalezas : new ArrayList<>(); }
    public List<Evidencia> getEvidencias() { return evidencias; }
    public void setEvidencias(List<Evidencia> evidencias) { this.evidencias = evidencias != null ? evidencias : new ArrayList<>(); }
    public List<String> getSugerencias() { return sugerencias; }
    public void setSugerencias(List<String> sugerencias) { this.sugerencias = sugerencias != null ? sugerencias : new ArrayList<>(); }

    public String getGeneradoEnLabel() { return generadoEnLabel; }
    public void setGeneradoEnLabel(String generadoEnLabel) { this.generadoEnLabel = generadoEnLabel; }
    public String getEditadoEnLabel() { return editadoEnLabel; }
    public void setEditadoEnLabel(String editadoEnLabel) { this.editadoEnLabel = editadoEnLabel; }
    public int getFuentesUsadas() { return fuentesUsadas; }
    public void setFuentesUsadas(int fuentesUsadas) { this.fuentesUsadas = fuentesUsadas; }
    public boolean isDesactualizado() { return desactualizado; }
    public void setDesactualizado(boolean desactualizado) { this.desactualizado = desactualizado; }

    @JsonIgnore
    public boolean isEditado() { return editadoEnLabel != null; }

    /** Para los textarea de edición: una viñeta por línea. */
    @JsonIgnore
    public String getFortalezasTexto() { return String.join("\n", fortalezas); }

    @JsonIgnore
    public String getSugerenciasTexto() { return String.join("\n", sugerencias); }
}
