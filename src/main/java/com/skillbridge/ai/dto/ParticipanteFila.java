package com.skillbridge.ai.dto;

/** Participante de un hilo (barra lateral del detalle). */
public record ParticipanteFila(String nombre, String iniciales, String cargo, int mensajes, boolean esAutorDelHilo) {
}
