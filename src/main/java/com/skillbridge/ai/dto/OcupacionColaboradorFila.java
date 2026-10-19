package com.skillbridge.ai.dto;

/** Fila de "Ocupación por colaborador" en Reportes globales. */
public class OcupacionColaboradorFila {

    private final Long perfilId;
    private final String nombre;
    private final String iniciales;
    private final String cargo;
    private final int promedio;
    private final String estadoLabel;
    private final String estadoBadgeClass;

    public OcupacionColaboradorFila(String nombre, String cargo, int promedio) {
        this(null, nombre, cargo, promedio);
    }

    /** Con perfilId: lo usa Ocupación (RM) para la acción "Rebalancear". */
    public OcupacionColaboradorFila(Long perfilId, String nombre, String cargo, int promedio) {
        this.perfilId = perfilId;
        this.nombre = nombre;
        this.iniciales = InicialesUtil.de(nombre);
        this.cargo = cargo != null ? cargo : "—";
        this.promedio = promedio;
        if (promedio > 100) {
            this.estadoLabel = "Sobre-asignado";
            this.estadoBadgeClass = "badge-red";
        } else if (promedio >= 90) {
            this.estadoLabel = "Al límite";
            this.estadoBadgeClass = "badge-amber";
        } else if (promedio >= 60) {
            this.estadoLabel = "Óptimo";
            this.estadoBadgeClass = "badge-green";
        } else {
            this.estadoLabel = "Subutilizado";
            this.estadoBadgeClass = "badge-neutral";
        }
    }

    public Long getPerfilId() {
        return perfilId;
    }

    public String getNombre() {
        return nombre;
    }

    public String getIniciales() {
        return iniciales;
    }

    public String getCargo() {
        return cargo;
    }

    public int getPromedio() {
        return promedio;
    }

    public String getEstadoLabel() {
        return estadoLabel;
    }

    public String getEstadoBadgeClass() {
        return estadoBadgeClass;
    }
}
