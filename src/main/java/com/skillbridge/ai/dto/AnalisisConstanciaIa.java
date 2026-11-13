package com.skillbridge.ai.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Resumen previo, hecho por Claude, de la constancia que respalda UNA
 * habilidad: lo ve el Resource Manager al momento de validar (ficha del
 * colaborador), para decidir sin abrir el PDF o el link completo. Se guarda
 * como JSON en constancia_analisis_ia.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AnalisisConstanciaIa {

    /** respalda | parcial | no_relacionada | no_legible */
    private String veredicto;
    private String resumen;
    private String emisor;
    private String curso;
    /** A nombre de quién está la constancia, si aparece. */
    private String titular;
    private String fecha;
    private String duracion;
    private String nota;
    private List<String> alertas = new ArrayList<>();
    /** Nivel que acredita la constancia según la IA: Básico | Intermedio | Avanzado | Experto (o null). */
    @JsonAlias("nivel_sugerido") private String nivelSugerido;
    @JsonAlias("motivo_nivel") private String motivoNivel;

    @JsonIgnore private String generadoEnLabel;

    /** Deja el veredicto en uno de los 4 valores conocidos y recorta textos largos. */
    public void normalizar() {
        String v = veredicto == null ? "" : veredicto.toLowerCase(Locale.ROOT).trim();
        veredicto = switch (v) {
            case "respalda", "parcial", "no_relacionada", "no_legible" -> v;
            default -> v.contains("no") && v.contains("relac") ? "no_relacionada" : v.contains("legib") ? "no_legible" : "parcial";
        };
        resumen = corto(resumen, 500);
        emisor = corto(emisor, 120);
        curso = corto(curso, 160);
        titular = corto(titular, 120);
        fecha = corto(fecha, 60);
        duracion = corto(duracion, 60);
        nota = corto(nota, 40);
        String n = nivelSugerido == null ? "" : nivelSugerido.toLowerCase(Locale.ROOT).trim();
        nivelSugerido = n.startsWith("b") ? "Básico" : n.startsWith("i") ? "Intermedio" : n.startsWith("a") ? "Avanzado"
                : n.startsWith("e") ? "Experto" : null;
        if (!"respalda".equals(veredicto) && !"parcial".equals(veredicto)) nivelSugerido = null;
        motivoNivel = nivelSugerido == null ? null : corto(motivoNivel, 200);
        if (alertas == null) alertas = new ArrayList<>();
        alertas = alertas.stream().map(a -> corto(a, 200)).filter(java.util.Objects::nonNull).limit(4).toList();
    }

    private static String corto(String s, int max) {
        if (s == null || s.isBlank() || s.equalsIgnoreCase("null")) return null;
        String t = s.trim();
        return t.length() > max ? t.substring(0, max).trim() : t;
    }

    @JsonIgnore
    public String getVeredictoLabel() {
        return switch (veredicto == null ? "" : veredicto) {
            case "respalda" -> "Respalda la habilidad";
            case "no_relacionada" -> "No se relaciona con la habilidad";
            case "no_legible" -> "No se pudo leer";
            default -> "La respalda en parte";
        };
    }

    /** Clase de badge del sistema (badge-green / badge-amber / badge-red / badge-neutral). */
    @JsonIgnore
    public String getVeredictoBadgeClass() {
        return switch (veredicto == null ? "" : veredicto) {
            case "respalda" -> "badge-green";
            case "no_relacionada" -> "badge-red";
            case "no_legible" -> "badge-neutral";
            default -> "badge-amber";
        };
    }

    public String getVeredicto() { return veredicto; }
    public void setVeredicto(String veredicto) { this.veredicto = veredicto; }
    public String getResumen() { return resumen; }
    public void setResumen(String resumen) { this.resumen = resumen; }
    public String getEmisor() { return emisor; }
    public void setEmisor(String emisor) { this.emisor = emisor; }
    public String getCurso() { return curso; }
    public void setCurso(String curso) { this.curso = curso; }
    public String getTitular() { return titular; }
    public void setTitular(String titular) { this.titular = titular; }
    public String getFecha() { return fecha; }
    public void setFecha(String fecha) { this.fecha = fecha; }
    public String getDuracion() { return duracion; }
    public void setDuracion(String duracion) { this.duracion = duracion; }
    public String getNota() { return nota; }
    public void setNota(String nota) { this.nota = nota; }
    public List<String> getAlertas() { return alertas; }
    public void setAlertas(List<String> alertas) { this.alertas = alertas != null ? alertas : new ArrayList<>(); }
    public String getNivelSugerido() { return nivelSugerido; }
    public void setNivelSugerido(String nivelSugerido) { this.nivelSugerido = nivelSugerido; }
    public String getMotivoNivel() { return motivoNivel; }
    public void setMotivoNivel(String motivoNivel) { this.motivoNivel = motivoNivel; }
    public String getGeneradoEnLabel() { return generadoEnLabel; }
    public void setGeneradoEnLabel(String generadoEnLabel) { this.generadoEnLabel = generadoEnLabel; }
}
