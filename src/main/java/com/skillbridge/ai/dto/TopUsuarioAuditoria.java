package com.skillbridge.ai.dto;

/** Fila de "Usuarios con más eventos" en administrador/auditoria.html. */
public class TopUsuarioAuditoria {

    private final String nombre;
    private final String iniciales;
    private final long eventos;

    public TopUsuarioAuditoria(String nombre, long eventos) {
        this.nombre = nombre;
        this.iniciales = InicialesUtil.de(nombre);
        this.eventos = eventos;
    }

    public String getNombre() {
        return nombre;
    }

    public String getIniciales() {
        return iniciales;
    }

    public long getEventos() {
        return eventos;
    }
}
