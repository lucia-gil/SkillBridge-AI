package com.skillbridge.ai.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Entregables: de nota (0-20) a revisión por estados.
 *
 * Amplía entregas.estado a 'enviado' | 'completado' | 'requiere_ajustes' |
 * 'rehacer' y pasa las entregas que ya estaban 'revisado' a 'completado'.
 * Las columnas antiguas (entregas.calificacion y entregables.puntaje_maximo)
 * se dejan en la base para no borrar datos de un despliegue existente,
 * pero la aplicación ya no las usa. Es idempotente: si ya se aplicó, no
 * hace nada. Si falla, solo avisa en el log.
 */
@Component
public class InicializadorRevisionEntregas implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(InicializadorRevisionEntregas.class);
    private static final String ENUM_NUEVO = "enum('enviado','completado','requiere_ajustes','rehacer')";
    private final JdbcTemplate jdbc;

    public InicializadorRevisionEntregas(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(String... args) {
        try {
            String tipo = jdbc.queryForObject("SELECT COLUMN_TYPE FROM information_schema.COLUMNS "
                    + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'entregas' AND COLUMN_NAME = 'estado'", String.class);
            if (tipo == null || ENUM_NUEVO.equalsIgnoreCase(tipo.replace(" ", ""))) return;

            // 1) Enum de transición (acepta lo viejo y lo nuevo), 2) migrar datos, 3) enum final.
            jdbc.execute("ALTER TABLE entregas MODIFY estado "
                    + "ENUM('enviado','revisado','completado','requiere_ajustes','rehacer') NOT NULL DEFAULT 'enviado'");
            int migradas = jdbc.update("UPDATE entregas SET estado = 'completado' WHERE estado = 'revisado'");
            jdbc.execute("ALTER TABLE entregas MODIFY estado "
                    + "ENUM('enviado','completado','requiere_ajustes','rehacer') NOT NULL DEFAULT 'enviado'");
            log.info("entregas: revisión por estados aplicada ({} entrega(s) 'revisado' pasaron a 'completado').", migradas);
        } catch (Exception e) {
            log.warn("No se pudo migrar entregas.estado a revisión por estados: {}", e.getMessage());
        }
        try {
            // La columna puntaje_maximo ya no se llena desde la app: que nunca bloquee un INSERT.
            Integer existe = jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.COLUMNS "
                    + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'entregables' AND COLUMN_NAME = 'puntaje_maximo' "
                    + "AND COLUMN_DEFAULT IS NULL AND IS_NULLABLE = 'NO'", Integer.class);
            if (existe != null && existe > 0) {
                jdbc.execute("ALTER TABLE entregables MODIFY puntaje_maximo DECIMAL(4,2) NOT NULL DEFAULT 20.00");
            }
        } catch (Exception e) {
            log.warn("No se pudo verificar entregables.puntaje_maximo: {}", e.getMessage());
        }
    }
}
