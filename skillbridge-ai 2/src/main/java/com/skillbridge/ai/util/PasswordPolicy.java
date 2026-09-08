package com.skillbridge.ai.util;

/**
 * Politica de contrasenas de SkillBridge AI.
 *
 * Adaptada de la clase equivalente en QuintaOla-SGA (mismo enfoque: un
 * validador central usado en todos los puntos donde se define una
 * contrasena), pero con las reglas que YA promete visualmente el
 * medidor de fuerza de auth/registro.html (12 caracteres, mayuscula +
 * numero, simbolo) en vez de las de QuintaOla (8 caracteres) - el
 * backend debe validar exactamente lo que la UI ya le muestra al
 * usuario, o el formulario mentiria.
 */
public final class PasswordPolicy {

    public static final int MIN_LEN = 12;

    private PasswordPolicy() {
    }

    /**
     * Valida la contrasena. Devuelve {@code null} si cumple, o un mensaje de
     * error listo para mostrar al usuario si no cumple.
     */
    public static String validar(String pwd) {
        if (pwd == null || pwd.isEmpty()) {
            return "La contraseña no puede estar vacía.";
        }
        if (pwd.indexOf(' ') >= 0) {
            return "La contraseña no debe contener espacios.";
        }
        if (pwd.length() < MIN_LEN) {
            return "La contraseña debe tener al menos " + MIN_LEN + " caracteres.";
        }
        boolean mayuscula = false;
        boolean numero = false;
        boolean simbolo = false;
        for (int i = 0; i < pwd.length(); i++) {
            char c = pwd.charAt(i);
            if (Character.isUpperCase(c)) mayuscula = true;
            else if (Character.isDigit(c)) numero = true;
            else if (!Character.isLetter(c)) simbolo = true;
        }
        if (!mayuscula) return "La contraseña debe incluir al menos una mayúscula.";
        if (!numero) return "La contraseña debe incluir al menos un número.";
        if (!simbolo) return "La contraseña debe incluir al menos un símbolo especial.";
        return null;
    }

    public static String requisitos() {
        return "Mínimo " + MIN_LEN + " caracteres, con mayúscula, número y símbolo especial.";
    }
}
