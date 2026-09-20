package com.skillbridge.ai.dto;

import java.math.BigDecimal;
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
    private final String estadoCrudo; // "sin_entregar" | "enviado" | "revisado"
    private final String calificacionLabel; // "—" si no calificado
    private final String comentarioPm;

    public EntregaFila(Long entregaId, Long entregableId, Long perfilId, String nombre, boolean entrego,
                        LocalDateTime fechaEntrega, boolean atrasado, String texto, String urlEntrega,
                        boolean tieneArchivo, String archivoNombre, String estadoCrudo,
                        BigDecimal calificacion, String comentarioPm) {
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
        this.calificacionLabel = calificacion != null ? calificacion.stripTrailingZeros().toPlainString() : "—";
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
    public String getCalificacionLabel() { return calificacionLabel; }
    public String getComentarioPm() { return comentarioPm; }

    public String getEstadoLabel() {
        return switch (estadoCrudo) {
            case "revisado" -> "Revisado";
            case "enviado" -> atrasado ? "Entregado (atrasado)" : "Entregado";
            default -> "Sin entregar";
        };
    }

    public String getEstadoBadgeClass() {
        return switch (estadoCrudo) {
            case "revisado" -> "badge-blue";
            case "enviado" -> atrasado ? "badge-amber" : "badge-green";
            default -> "badge-neutral";
        };
    }
}
