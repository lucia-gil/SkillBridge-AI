package com.skillbridge.ai.dto;

/** Calcula las 2 iniciales que se muestran en los avatares, en un solo lugar. */
public final class InicialesUtil {

    private InicialesUtil() {
    }

    public static String de(String nombreCompleto) {
        if (nombreCompleto == null || nombreCompleto.isBlank()) return "??";
        String[] partes = nombreCompleto.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < partes.length && sb.length() < 2; i++) {
            if (!partes[i].isEmpty()) sb.append(Character.toUpperCase(partes[i].charAt(0)));
        }
        return sb.length() > 0 ? sb.toString() : "??";
    }
}
