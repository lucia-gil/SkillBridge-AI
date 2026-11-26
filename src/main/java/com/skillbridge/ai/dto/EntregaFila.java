package com.skillbridge.ai.dto;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Una fila de la tabla de revisión (una entrega, o un colaborador que
 * TODAVÍA no entregó — en ese caso casi todo viene null/por defecto, ver
 * {@link com.skillbridge.ai.service.EntregableService}).
 */
public class EntregaFila {

    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final Long entregaId; // null si no ha entregado
    private final Long entregableId;
    private final Long perfilId;
    private final String nombre;
    private final String iniciales;
    private final boolean entrego;
    private final String fechaEntregaLabel;
    private final boolean atrasado;
    private final String texto;
    private final String urlEntrega;
    private final boolean tieneArchivo;
    private final String archivoNombre;
    // "sin_entregar" | "enviado" (por revisar) | "completado" | "requiere_ajustes" | "rehacer"
    private final String estadoCrudo;
    private final String comentarioPm;

    public EntregaFila(Long entregaId, Long entregableId, Long perfilId, String nombre, boolean entrego,
                        LocalDateTime fechaEntrega, boolean atrasado, String texto, String urlEntrega,
                        boolean tieneArchivo, String archivoNombre, String estadoCrudo,
                        String comentarioPm) {
        this.entregaId = entregaId;
        this.entregableId = entregableId;
        this.perfilId = perfilId;
        this.nombre = nombre;
        this.iniciales = InicialesUtil.de(nombre);
        this.entrego = entrego;
        this.fechaEntregaLabel = fechaEntrega != null ? fechaEntrega.format(FORMATO) : "—";
        this.atrasado = atrasado;
        this.texto = texto;
        this.urlEntrega = urlEntrega;
        this.tieneArchivo = tieneArchivo;
        this.archivoNombre = archivoNombre;
        this.estadoCrudo = estadoCrudo;
        this.comentarioPm = comentarioPm;
    }

    public Long getEntregaId() { return entregaId; }
    public Long getEntregableId() { return entregableId; }
    public Long getPerfilId() { return perfilId; }
    public String getNombre() { return nombre; }
    public String getIniciales() { return iniciales; }
    public boolean isEntrego() { return entrego; }
    public String getFechaEntregaLabel() { return fechaEntregaLabel; }
    public boolean isAtrasado() { return atrasado; }
    public String getTexto() { return texto; }
    public String getUrlEntrega() { return urlEntrega; }
    public boolean isTieneArchivo() { return tieneArchivo; }
    public String getArchivoNombre() { return archivoNombre; }
    public String getEstadoCrudo() { return estadoCrudo; }
    public String getComentarioPm() { return comentarioPm; }

    /** true cuando el PM ya la revisó (cualquiera de los tres estados de revisión). */
    public boolean isRevisada() {
        return "completado".equals(estadoCrudo) || "requiere_ajustes".equals(estadoCrudo) || "rehacer".equals(estadoCrudo);
    }

    /** Completada = cerrada: el colaborador ya no puede reemplazarla ni borrarla. */
    public boolean isCompletada() { return "completado".equals(estadoCrudo); }

    /** El PM pidió cambios (ajustes o rehacer): el colaborador debe volver a entregar. */
    public boolean isPideReenvio() { return "requiere_ajustes".equals(estadoCrudo) || "rehacer".equals(estadoCrudo); }

    public String getEstadoLabel() {
        return switch (estadoCrudo) {
            case "completado" -> "Completado";
            case "requiere_ajustes" -> "Requiere ajustes";
            case "rehacer" -> "Rehacer";
            case "enviado" -> atrasado ? "Por revisar (atrasado)" : "Por revisar";
            default -> "Sin entregar";
        };
    }

    public String getEstadoBadgeClass() {
        return switch (estadoCrudo) {
            case "completado" -> "badge-green";
            case "requiere_ajustes" -> "badge-amber";
            case "rehacer" -> "badge-red";
            case "enviado" -> "badge-blue";
            default -> "badge-neutral";
        };
    }
}
