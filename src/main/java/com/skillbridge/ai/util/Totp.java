package com.skillbridge.ai.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

/**
 * TOTP (RFC 6238) con HMAC-SHA1, 6 digitos y paso de 30 s: compatible con
 * Google Authenticator, Microsoft Authenticator, Authy, 1Password, etc.
 * Implementacion propia y sin dependencias externas (solo javax.crypto).
 */
public final class Totp {

    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom RANDOM = new SecureRandom();
    public static final int PASO_SEGUNDOS = 30;
    public static final int DIGITOS = 6;

    private Totp() {
    }

    /** Secreto aleatorio de 160 bits en Base32 (32 caracteres), como recomienda el RFC 4226. */
    public static String generarSecreto() {
        byte[] bytes = new byte[20];
        RANDOM.nextBytes(bytes);
        return base32Codificar(bytes);
    }

    public static String base32Codificar(byte[] datos) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0, bits = 0;
        for (byte b : datos) {
            buffer = (buffer << 8) | (b & 0xFF);
            bits += 8;
            while (bits >= 5) {
                sb.append(BASE32.charAt((buffer >> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) {
            sb.append(BASE32.charAt((buffer << (5 - bits)) & 31));
        }
        return sb.toString();
    }

    public static byte[] base32Decodificar(String texto) {
        String limpio = texto.replace("=", "").replace(" ", "").toUpperCase();
        ByteBuffer out = ByteBuffer.allocate(limpio.length() * 5 / 8 + 1);
        int buffer = 0, bits = 0;
        for (char c : limpio.toCharArray()) {
            int val = BASE32.indexOf(c);
            if (val < 0) {
                throw new IllegalArgumentException("Secreto Base32 invalido");
            }
            buffer = (buffer << 5) | val;
            bits += 5;
            if (bits >= 8) {
                out.put((byte) ((buffer >> (bits - 8)) & 0xFF));
                bits -= 8;
            }
        }
        byte[] res = new byte[out.position()];
        out.flip();
        out.get(res);
        return res;
    }

    /** Codigo de 6 digitos para un contador (paso de tiempo) dado. */
    public static String codigo(byte[] clave, long contador) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(clave, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(contador).array());
            int offset = hash[hash.length - 1] & 0x0F;
            int binario = ((hash[offset] & 0x7F) << 24) | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8) | (hash[offset + 3] & 0xFF);
            int otp = binario % 1_000_000;
            return String.format("%0" + DIGITOS + "d", otp);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA1 no disponible", e);
        }
    }

    /** Acepta el paso actual y +-1 (tolerancia de 30 s por desfase de relojes). */
    public static boolean verificar(String secretoBase32, String codigoIngresado) {
        return verificar(secretoBase32, codigoIngresado, System.currentTimeMillis() / 1000L);
    }

    public static boolean verificar(String secretoBase32, String codigoIngresado, long epochSegundos) {
        return pasoVerificado(secretoBase32, codigoIngresado, epochSegundos) >= 0;
    }

    /**
     * Paso de 30 s al que corresponde el código (actual o +-1), o -1 si no es
     * válido. Sirve para impedir que el mismo código se use dos veces.
     */
    public static long pasoVerificado(String secretoBase32, String codigoIngresado, long epochSegundos) {
        if (secretoBase32 == null || codigoIngresado == null) return -1;
        String ingresado = codigoIngresado.replaceAll("\\s", "");
        if (!ingresado.matches("\\d{" + DIGITOS + "}")) return -1;
        byte[] clave;
        try {
            clave = base32Decodificar(secretoBase32);
        } catch (IllegalArgumentException e) {
            return -1;
        }
        long paso = epochSegundos / PASO_SEGUNDOS;
        long encontrado = -1;
        for (long d = -1; d <= 1; d++) {
            // comparacion en tiempo constante entre los 3 candidatos
            if (java.security.MessageDigest.isEqual(
                    codigo(clave, paso + d).getBytes(StandardCharsets.UTF_8), ingresado.getBytes(StandardCharsets.UTF_8))) {
                encontrado = paso + d;
            }
        }
        return encontrado;
    }

    /** URI otpauth:// que leen las apps autenticadoras (se dibuja como QR en el cliente). */
    public static String uri(String emisor, String cuenta, String secreto) {
        String e = URLEncoder.encode(emisor, StandardCharsets.UTF_8).replace("+", "%20");
        String c = URLEncoder.encode(cuenta, StandardCharsets.UTF_8).replace("+", "%20");
        return "otpauth://totp/" + e + ":" + c + "?secret=" + secreto + "&issuer=" + e
                + "&algorithm=SHA1&digits=" + DIGITOS + "&period=" + PASO_SEGUNDOS;
    }
}
