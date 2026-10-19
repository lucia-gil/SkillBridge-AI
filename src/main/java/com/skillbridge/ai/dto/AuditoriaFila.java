package com.skillbridge.ai.dto;

/**
 * Fila de administrador/auditoria.html. El esquema NO tiene columnas
 * tipo/severidad/origen en auditoria_logs (ver comentario de la tabla en
 * skillbridge_db_v4.sql) - se derivan aquí de forma heurística a partir del
 * prefijo de "accion", declarado explícitamente para no aparentar que son
 * datos almacenados.
 */
public class AuditoriaFila {

    private final String fechaLabel;
    private final String usuarioLabel;
    private final String accion;
    private final String detalle;
    private final String tipoCrudo;
    private final String tipoLabel;
    private final String severidadCrudo;
    private final String severidadBadgeClass;
    private final String origenLabel;

    public AuditoriaFila(String fechaLabel, String usuarioLabel, String accion, String detalle) {
        this.fechaLabel = fechaLabel;
        this.usuarioLabel = usuarioLabel != null ? usuarioLabel : "Sistema";
        this.accion = accion;
        this.detalle = detalle != null ? detalle : "—";
        // auditoria_logs no tiene columna "origen" (ver comentario de la tabla
        // en skillbridge_db_v4.sql): se deriva de si el evento tiene un
        // usuario asociado o no, solo como referencia visual.
        this.origenLabel = "Sistema".equals(this.usuarioLabel) ? "Sistema" : "Manual";

        String a = accion == null ? "" : accion;
        if (a.startsWith("LOGIN") || a.startsWith("REGISTRO")) {
            this.tipoCrudo = "seguridad";
            this.tipoLabel = "Seguridad";
        } else if (a.startsWith("USUARIO")) {
            this.tipoCrudo = "roles";
            this.tipoLabel = "Roles y usuarios";
        } else if (a.startsWith("HABILIDAD") || a.startsWith("PROYECTO") || a.startsWith("ASIGNACION")) {
            this.tipoCrudo = "asignaciones";
            this.tipoLabel = "Proyectos y asignaciones";
        } else {
            this.tipoCrudo = "ia";
            this.tipoLabel = "Sistema";
        }

        if (a.contains("FALLIDO") || a.contains("BLOQUEADO") || a.contains("ELIMINADO")) {
            this.severidadCrudo = "Alta";
            this.severidadBadgeClass = "badge-red";
        } else if (a.contains("SUSPENDIDO") || a.contains("CAMBIADO") || a.contains("ROL")) {
            this.severidadCrudo = "Media";
            this.severidadBadgeClass = "badge-amber";
        } else {
            this.severidadCrudo = "Info";
            this.severidadBadgeClass = "badge-neutral";
        }
    }

    public String getFechaLabel() {
        return fechaLabel;
    }

    public String getUsuarioLabel() {
        return usuarioLabel;
    }

    public String getAccion() {
        return accion;
    }

    public String getDetalle() {
        return detalle;
    }

    public String getTipoCrudo() {
        return tipoCrudo;
    }

    public String getTipoLabel() {
        return tipoLabel;
    }

    public String getSeveridadCrudo() {
        return severidadCrudo;
    }

    public String getSeveridadBadgeClass() {
        return severidadBadgeClass;
    }

    public String getOrigenLabel() {
        return origenLabel;
    }
}
