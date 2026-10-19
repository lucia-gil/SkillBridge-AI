package com.skillbridge.ai.dto;

/** Fila (tarjeta) de un evento en la agenda del Calendario. */
public class EventoFila {

    private final Long id;
    private final int diaNum;
    private final String mesAbrev;
    private final String fechaLabel;
    private final String horaLabel;
    private final String tipoCodigo;
    private final String tipoNombre;
    private final String tipoColor;
    private final String titulo;
    private final String proyectoNombre;
    private final String ubicacionLabel;
    private final String estado;
    private final String audienciaCodigo;
    private final String audienciaLabel;
    /** true si el perfil que pidio la lista puede editar/eliminar ESTE evento (ver EventoService.puedeGestionar). */
    private final boolean puedeGestionar;
    // Campos "crudos" (sin formatear) que necesita el modal de edicion para
    // precargar el formulario: la fila ya trae fechaLabel/horaLabel legibles
    // para mostrar, pero el <input type="datetime-local"> necesita el ISO.
    private final Long proyectoId;
    private final String descripcion;
    private final String ubicacion;
    private final String enlaceVirtual;
    private final String fechaInicioIso;

    public EventoFila(Long id, int diaNum, String mesAbrev, String fechaLabel, String horaLabel,
                      String tipoCodigo, String tipoNombre, String tipoColor, String titulo,
                      String proyectoNombre, String ubicacionLabel, String estado,
                      String audienciaCodigo, String audienciaLabel, boolean puedeGestionar,
                      Long proyectoId, String descripcion, String ubicacion, String enlaceVirtual,
                      String fechaInicioIso) {
        this.id = id;
        this.diaNum = diaNum;
        this.mesAbrev = mesAbrev;
        this.fechaLabel = fechaLabel;
        this.horaLabel = horaLabel;
        this.tipoCodigo = tipoCodigo;
        this.tipoNombre = tipoNombre;
        this.tipoColor = tipoColor;
        this.titulo = titulo;
        this.proyectoNombre = proyectoNombre;
        this.ubicacionLabel = ubicacionLabel;
        this.estado = estado;
        this.audienciaCodigo = audienciaCodigo;
        this.audienciaLabel = audienciaLabel;
        this.puedeGestionar = puedeGestionar;
        this.proyectoId = proyectoId;
        this.descripcion = descripcion;
        this.ubicacion = ubicacion;
        this.enlaceVirtual = enlaceVirtual;
        this.fechaInicioIso = fechaInicioIso;
    }

    public Long getId() { return id; }
    public int getDiaNum() { return diaNum; }
    public String getMesAbrev() { return mesAbrev; }
    public String getFechaLabel() { return fechaLabel; }
    public String getHoraLabel() { return horaLabel; }
    public String getTipoCodigo() { return tipoCodigo; }
    public String getTipoNombre() { return tipoNombre; }
    public String getTipoColor() { return tipoColor; }
    public String getTitulo() { return titulo; }
    public String getProyectoNombre() { return proyectoNombre; }
    public String getUbicacionLabel() { return ubicacionLabel; }
    public String getEstado() { return estado; }
    public String getAudienciaCodigo() { return audienciaCodigo; }
    public String getAudienciaLabel() { return audienciaLabel; }
    public boolean isPuedeGestionar() { return puedeGestionar; }
    public Long getProyectoId() { return proyectoId; }
    public String getDescripcion() { return descripcion; }
    public String getUbicacion() { return ubicacion; }
    public String getEnlaceVirtual() { return enlaceVirtual; }
    public String getFechaInicioIso() { return fechaInicioIso; }
}