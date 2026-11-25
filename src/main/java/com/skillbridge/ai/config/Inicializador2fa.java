package com.skillbridge.ai.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Crea usuarios_2fa si no existe (la app corre con ddl-auto=none). Si falla, solo avisa: el 2FA queda no disponible, el login normal no se ve afectado. */
@Component
public class Inicializador2fa implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(Inicializador2fa.class);
    private final JdbcTemplate jdbc;

    public Inicializador2fa(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(String... args) {
        try {
            jdbc.execute("CREATE TABLE IF NOT EXISTS usuarios_2fa ("
                    + "usuario_id BIGINT NOT NULL, secreto VARCHAR(64) NOT NULL, "
                    + "activo TINYINT(1) NOT NULL DEFAULT 0, "
                    + "creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                    + "PRIMARY KEY (usuario_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        } catch (Exception e) {
            log.warn("No se pudo crear/verificar usuarios_2fa: {}", e.getMessage());
        }
        try {
            // Último paso TOTP aceptado: impide reutilizar un código dentro de su ventana.
            Integer existe = jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.COLUMNS "
                    + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'usuarios_2fa' AND COLUMN_NAME = 'ultimo_paso'", Integer.class);
            if (existe != null && existe == 0) {
                jdbc.execute("ALTER TABLE usuarios_2fa ADD COLUMN ultimo_paso BIGINT NULL");
                log.info("usuarios_2fa: agregada la columna ultimo_paso.");
            }
        } catch (Exception e) {
            log.warn("No se pudo agregar usuarios_2fa.ultimo_paso: {}", e.getMessage());
        }
    }
}
