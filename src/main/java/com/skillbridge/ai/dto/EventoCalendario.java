package com.skillbridge.ai.dto;

/** Un evento del calendario: fecha ISO (yyyy-MM-dd), nombre del proyecto y tipo ("inicio"|"entrega"). */
public record EventoCalendario(String fecha, String proyecto, String tipo) {
}
