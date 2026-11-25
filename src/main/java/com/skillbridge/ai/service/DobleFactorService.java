package com.skillbridge.ai.service;

import com.skillbridge.ai.util.Totp;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

/** Doble factor TOTP por usuario (tabla usuarios_2fa). */
@Service
public class DobleFactorService {

    private static final String EMISOR = "SkillBridge AI";
    private final JdbcTemplate jdbc;

    public DobleFactorService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private String secreto(Long usuarioId, boolean soloActivo) {
        try {
            return jdbc.queryForObject(
                    "SELECT secreto FROM usuarios_2fa WHERE usuario_id = ?" + (soloActivo ? " AND activo = 1" : ""),
                    String.class, usuarioId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    /** true si el usuario tiene el 2FA activado. Los errores de lectura impiden completar la autenticación. */
    public boolean estaActivo(Long usuarioId) {
        return usuarioId != null && secreto(usuarioId, true) != null;
    }

    /** Genera (o regenera, si aun no estaba activo) un secreto pendiente de confirmar. */
    public Map<String, String> iniciarConfiguracion(Long usuarioId, String correo) {
        if (estaActivo(usuarioId)) {
            throw new IllegalStateException("El doble factor ya esta activo.");
        }
        String s = Totp.generarSecreto();
        jdbc.update("INSERT INTO usuarios_2fa (usuario_id,secreto,activo) VALUES(?,?,0) " +
                "ON DUPLICATE KEY UPDATE secreto=IF(activo=0,?,secreto)", usuarioId, s, s);
        return Map.of("secreto", s, "uri", Totp.uri(EMISOR, correo, s));
    }

    /**
     * La clave generada que aún no se activó (para volver a mostrar el QR si
     * el usuario recarga o se equivoca de código), o vacío si no hay.
     */
    public java.util.Optional<Map<String, String>> configuracionPendiente(Long usuarioId, String correo) {
        if (usuarioId == null || estaActivo(usuarioId)) return java.util.Optional.empty();
        String s = secreto(usuarioId, false);
        return s == null ? java.util.Optional.empty() : java.util.Optional.of(Map.of("secreto", s, "uri", Totp.uri(EMISOR, correo, s)));
    }

    public boolean activar(Long usuarioId, String codigo) {
        String s = secreto(usuarioId, false);
        long paso = Totp.pasoVerificado(s, codigo, ahora());
        if (paso < 0) return false;
        // El código con el que se activa queda usado: no sirve después para iniciar sesión.
        return jdbc.update("UPDATE usuarios_2fa SET activo=1, ultimo_paso=? WHERE usuario_id=? AND activo=0 AND BINARY secreto=?",
                paso, usuarioId, s) == 1;
    }

    /**
     * true si el código es válido y no se había usado antes. Cada código (paso
     * de 30 s) sirve una sola vez: el UPDATE solo acepta un paso posterior al
     * último usado, de forma atómica, así dos envíos del mismo código no pasan.
     */
    public boolean verificar(Long usuarioId, String codigo) {
        long paso = Totp.pasoVerificado(secreto(usuarioId, true), codigo, ahora());
        if (paso < 0) return false;
        return jdbc.update("UPDATE usuarios_2fa SET ultimo_paso=? WHERE usuario_id=? AND activo=1 "
                + "AND (ultimo_paso IS NULL OR ultimo_paso < ?)", paso, usuarioId, paso) == 1;
    }

    private static long ahora() {
        return System.currentTimeMillis() / 1000L;
    }

    public boolean desactivar(Long usuarioId, String codigo) {
        if (!verificar(usuarioId, codigo)) return false;
        jdbc.update("DELETE FROM usuarios_2fa WHERE usuario_id = ?", usuarioId);
        return true;
    }
}
