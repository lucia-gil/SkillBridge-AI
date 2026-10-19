package com.skillbridge.ai.dto;

/** Fila de la tabla "equipo" dentro del detalle de un proyecto. */
public class MiembroEquipoFila {

    private final Long asignacionId;
    private final Long perfilId;
    private final String nombre;
    private final String correo;
    private final String iniciales;
    private final String rolEnProyectoCrudo;
    private final String rolLabel;
    private final int cargaPorcentaje;

    public MiembroEquipoFila(Long asignacionId, Long perfilId, String nombre, String correo,
                              String rolEnProyectoCrudo, int cargaPorcentaje) {
        this.asignacionId = asignacionId;
        this.perfilId = perfilId;
        this.nombre = nombre;
        this.correo = correo;
        this.iniciales = InicialesUtil.de(nombre);
        this.rolEnProyectoCrudo = rolEnProyectoCrudo;
        this.rolLabel = "project_manager".equals(rolEnProyectoCrudo) ? "Project Manager" : "Colaborador";
        this.cargaPorcentaje = cargaPorcentaje;
    }

    public Long getAsignacionId() {
        return asignacionId;
    }

    public Long getPerfilId() {
        return perfilId;
    }

    public String getNombre() {
        return nombre;
    }

    public String getCorreo() {
        return correo;
    }

    public String getIniciales() {
        return iniciales;
    }

    public String getRolEnProyectoCrudo() {
        return rolEnProyectoCrudo;
    }

    public String getRolLabel() {
        return rolLabel;
    }

    public int getCargaPorcentaje() {
        return cargaPorcentaje;
    }
}
