package com.skillbridge.ai.dto;

/** Metadatos de un archivo adjunto al proyecto (sin el BLOB). */
public class ArchivoProyectoFila {

    private final Long id;
    private final String nombre;
    private final String tipo;
    private final String fechaSubidaLabel;

    public ArchivoProyectoFila(Long id, String nombre, String tipo, String fechaSubidaLabel) {
        this.id = id;
        this.nombre = nombre;
        this.tipo = tipo;
        this.fechaSubidaLabel = fechaSubidaLabel;
    }

    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public String getTipo() { return tipo; }
    public String getFechaSubidaLabel() { return fechaSubidaLabel; }

    public String getIcono() {
        if (tipo == null) return "file-text";
        if (tipo.startsWith("image/")) return "camera";
        if (tipo.contains("pdf")) return "file-text";
        return "file-text";
    }
}
