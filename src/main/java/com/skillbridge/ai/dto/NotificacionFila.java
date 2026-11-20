package com.skillbridge.ai.dto;

/** Fila del centro de notificaciones (compartida por Administrador y Colaborador). */
public class NotificacionFila {

    private final Long id;
    private final String titulo;
    private final String detalle;
    private final boolean leida;
    private final String tipoCodigo;
    private final String tipoLabel;
    private final String iconClass;
    private final String iconId;
    private final String fechaLabel;
    private final String enlaceAccion;

    public NotificacionFila(Long id, String titulo, String detalle, boolean leida, String tipoCodigo,
                             String tipoLabel, String fechaLabel, String enlaceAccion) {
        this.id = id;
        this.titulo = titulo;
        this.detalle = detalle;
        this.leida = leida;
        this.tipoCodigo = tipoCodigo;
        this.tipoLabel = tipoLabel;
        String codigo = tipoCodigo == null ? "" : tipoCodigo;
        this.iconClass = switch (codigo) {
            case "alerta" -> "alert";
            case "solicitud" -> "info";
            case "resultado_ia" -> "ai";
            case "asignacion" -> "success";
            case "foro_respuesta" -> "mail";
            default -> "info";
        };
        // Solo se usan ids de icono ya presentes en img/icons.svg (verificados
        // contra los que ya consumía static/js/mock-data.js).
        this.iconId = switch (codigo) {
            case "alerta" -> "icon-alert-triangle";
            case "solicitud" -> "icon-shield";
            case "resultado_ia" -> "icon-sparkles";
            case "asignacion" -> "icon-plus-circle";
            case "foro_respuesta" -> "icon-mail";
            default -> "icon-bell";
        };
        this.fechaLabel = fechaLabel;
        this.enlaceAccion = enlaceAccion;
    }

    public Long getId() {
        return id;
    }

    public String getTitulo() {
        return titulo;
    }

    public String getDetalle() {
        return detalle;
    }

    public boolean isLeida() {
        return leida;
    }

    public String getTipoCodigo() {
        return tipoCodigo;
    }

    public String getTipoLabel() {
        return tipoLabel;
    }

    public String getIconClass() {
        return iconClass;
    }

    public String getIconId() {
        return iconId;
    }

    public String getFechaLabel() {
        return fechaLabel;
    }

    /** Destinos administrativos conocidos. La bandeja no navega a URLs externas. */
    public String getRutaAdmin() {
        if (enlaceAccion == null) return null;
        String ruta = enlaceAccion.startsWith("/administrador/")
                ? enlaceAccion.substring("/administrador/".length()) : enlaceAccion;
        if (ruta.matches("seguridad[.]html[?]id=[0-9]+")) return "/administrador/" + ruta;
        return switch (ruta) {
            case "inicio.html", "usuarios.html", "proyectos.html", "auditoria.html", "configuracion.html",
                    "reportes.html", "cursos.html", "catalogos.html", "habilidades.html" -> "/administrador/" + ruta;
            default -> null;
        };
    }

    public String getEnlaceAccion() {
        return enlaceAccion;
    }
}
