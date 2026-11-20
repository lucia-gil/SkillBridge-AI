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

    public boolean activar(Long usuarioId, String codigo) {
        String s = secreto(usuarioId, false);
        if (s == null || !Totp.verificar(s, codigo)) return false;
        return jdbc.update("UPDATE usuarios_2fa SET activo=1 WHERE usuario_id=? AND activo=0 AND BINARY secreto=?", usuarioId, s) == 1;
    }

    public boolean verificar(Long usuarioId, String codigo) {
        return Totp.verificar(secreto(usuarioId, true), codigo);
    }

    public boolean desactivar(Long usuarioId, String codigo) {
        if (!verificar(usuarioId, codigo)) return false;
        jdbc.update("DELETE FROM usuarios_2fa WHERE usuario_id = ?", usuarioId);
        return true;
    }
}
