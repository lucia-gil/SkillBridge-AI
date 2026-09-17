package com.skillbridge.ai.dto;

import java.util.List;

/** Una habilidad declarada por un colaborador en su perfil (colaborador/perfil.html, resource-manager/colaboradores.html). */
public class HabilidadPerfilFila {

    private final Long habilidadId;
    private final String nombre;
    private final String categoriaNombre;
    private final int nivelNumero;
    private final String nivelLabel;
    private final String declaradaDesdeLabel;
    private final boolean validada;
    private final List<CertificadoFila> certificados;

    public HabilidadPerfilFila(Long habilidadId, String nombre, String categoriaNombre, int nivelNumero, String declaradaDesdeLabel) {
        this(habilidadId, nombre, categoriaNombre, nivelNumero, declaradaDesdeLabel, false, List.of());
    }

    // Sobrecarga con "validada": se agrega aparte (en vez de cambiar el
    // constructor original) para no romper el unico llamado que ya existia
    // en colaborador/perfil.html - ese caso de uso no necesita saber si un
    // Resource Manager valido la habilidad, solo resource-manager/colaboradores.html.
    public HabilidadPerfilFila(Long habilidadId, String nombre, String categoriaNombre, int nivelNumero, String declaradaDesdeLabel, boolean validada) {
        this(habilidadId, nombre, categoriaNombre, nivelNumero, declaradaDesdeLabel, validada, List.of());
    }

    // Sobrecarga con "certificados": usada por la vista de detalle completo
    // del colaborador (resource-manager/colaborador-detalle.html), que
    // necesita mostrar los links de respaldo que el colaborador adjuntó
    // para cada habilidad (o avisar que no adjuntó ninguno).
    public HabilidadPerfilFila(Long habilidadId, String nombre, String categoriaNombre, int nivelNumero,
                               String declaradaDesdeLabel, boolean validada, List<CertificadoFila> certificados) {
        this.habilidadId = habilidadId;
        this.nombre = nombre;
        this.categoriaNombre = categoriaNombre;
        this.nivelNumero = nivelNumero;
        this.nivelLabel = switch (nivelNumero) {
            case 1, 2 -> "Básico";
            case 3 -> "Intermedio";
            case 4 -> "Avanzado";
            case 5 -> "Experto";
            default -> "—";
        };
        this.declaradaDesdeLabel = declaradaDesdeLabel;
        this.validada = validada;
        this.certificados = certificados;
    }

    public Long getHabilidadId() {
        return habilidadId;
    }

    public String getNombre() {
        return nombre;
    }

    public String getCategoriaNombre() {
        return categoriaNombre;
    }

    public int getNivelNumero() {
        return nivelNumero;
    }

    public String getNivelLabel() {
        return nivelLabel;
    }

    public String getDeclaradaDesdeLabel() {
        return declaradaDesdeLabel;
    }

    public boolean isValidada() {
        return validada;
    }

    public List<CertificadoFila> getCertificados() {
        return certificados;
    }

    public boolean isTieneCertificado() {
        return !certificados.isEmpty();
    }
}