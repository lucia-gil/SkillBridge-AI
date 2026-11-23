package com.skillbridge.ai.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class AsistenteOrganizacionRepository {
    public record Entrada(Long id, String titulo, LocalDateTime actualizada, boolean fijada,
                          Long proyectoId, String proyectoNombre, boolean archivada) {}

    private final JdbcTemplate jdbc;

    public AsistenteOrganizacionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Entrada> historial(Long usuarioId, String rol, String firma) {
        return jdbc.query("""
                SELECT c.id, c.titulo, c.fecha_actualizacion, c.fijada,
                       o.proyecto_id, p.nombre AS proyecto_nombre, COALESCE(o.archivada, FALSE) AS archivada
                FROM asistente_conversaciones c
                JOIN agente_alcances a ON a.conversacion_id = c.id AND a.firma = ?
                LEFT JOIN asistente_organizacion o ON o.conversacion_id = c.id
                LEFT JOIN proyectos p ON p.id = o.proyecto_id
                WHERE c.usuario_id = ? AND c.rol = ?
                ORDER BY c.fijada DESC, c.fecha_actualizacion DESC, c.id DESC
                """, (rs, fila) -> new Entrada(rs.getLong("id"), rs.getString("titulo"),
                rs.getObject("fecha_actualizacion", LocalDateTime.class), rs.getBoolean("fijada"),
                rs.getObject("proyecto_id", Long.class), rs.getString("proyecto_nombre"),
                rs.getBoolean("archivada")), firma, usuarioId, rol);
    }

    public void archivar(Long conversacionId, boolean archivada) {
        jdbc.update("""
                INSERT INTO asistente_organizacion (conversacion_id, archivada) VALUES (?, ?)
                ON DUPLICATE KEY UPDATE archivada = ?
                """, conversacionId, archivada, archivada);
    }

    public void vincular(Long conversacionId, Long proyectoId) {
        jdbc.update("""
                INSERT INTO asistente_organizacion (conversacion_id, proyecto_id) VALUES (?, ?)
                ON DUPLICATE KEY UPDATE proyecto_id = ?
                """, conversacionId, proyectoId, proyectoId);
    }
}
