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
    private final int limite;

    public OcupacionColaboradorFila(String nombre, String cargo, int promedio) {
        this(null, nombre, cargo, promedio, 100);
    }

    /** Con perfilId: lo usa Ocupación (RM) para la acción "Rebalancear". */
    public OcupacionColaboradorFila(Long perfilId, String nombre, String cargo, int promedio) {
        this(perfilId, nombre, cargo, promedio, 100);
    }

    /**
     * Con el límite de carga vigente (Configuración). "Sobre-asignado" es
     * superar ese límite; "Al límite" son los 10 puntos anteriores a él.
     */
    public OcupacionColaboradorFila(Long perfilId, String nombre, String cargo, int promedio, int limite) {
        this.perfilId = perfilId;
        this.nombre = nombre;
        this.iniciales = InicialesUtil.de(nombre);
        this.cargo = cargo != null ? cargo : "—";
        this.promedio = promedio;
        this.limite = limite;
        if (promedio > limite) {
            this.estadoLabel = "Sobre-asignado";
            this.estadoBadgeClass = "badge-red";
        } else if (promedio >= limite - 10) {
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

    /** Límite de carga contra el que se evaluó esta fila. */
    public int getLimite() {
        return limite;
    }

    /** true si supera el límite de carga vigente. */
    public boolean isSobrecargado() {
        return promedio > limite;
    }

    /** true si está a 10 puntos o menos del límite, sin superarlo. */
    public boolean isAlLimite() {
        return !isSobrecargado() && promedio >= limite - 10;
    }
}
