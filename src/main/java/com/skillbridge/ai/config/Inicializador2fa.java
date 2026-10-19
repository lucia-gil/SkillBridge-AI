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
    }
}
