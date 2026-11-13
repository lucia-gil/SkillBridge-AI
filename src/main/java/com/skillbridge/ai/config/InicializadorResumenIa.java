package com.skillbridge.ai.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Crea, si todavía no existen, las tablas del Resumen profesional con IA:
 * "perfil_resumen_ia" (una fila por perfil con el resumen que arma Claude
 * con las habilidades validadas) y "constancia_analisis_ia" (el resumen
 * previo de cada constancia que ve el Resource Manager al validar). Mismo patrón que
 * InicializadorTablasExtra (ddl-auto=none); el SQL equivalente está al
 * final de db/skillbridge_db.sql.
 */
@Component
@Order(1)
public class InicializadorResumenIa implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(InicializadorResumenIa.class);
    private final JdbcTemplate jdbc;

    public InicializadorResumenIa(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(String... args) {
        try {
            jdbc.execute("CREATE TABLE IF NOT EXISTS perfil_resumen_ia ("
                    + " perfil_id BIGINT UNSIGNED NOT NULL PRIMARY KEY,"
                    + " contenido_json MEDIUMTEXT NOT NULL,"
                    + " firma_fuentes VARCHAR(64) NULL,"
                    + " fuentes_usadas INT NOT NULL DEFAULT 0,"
                    + " generado_en DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                    + " editado_en DATETIME NULL,"
                    + " CONSTRAINT fk_resumen_ia_perfil FOREIGN KEY (perfil_id)"
                    + " REFERENCES perfiles(id) ON DELETE CASCADE"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        } catch (Exception e) {
            log.warn("No se pudo verificar/crear la tabla perfil_resumen_ia: {}", e.getMessage());
        }
        try {
            jdbc.execute("CREATE TABLE IF NOT EXISTS constancia_analisis_ia ("
                    + " perfil_id BIGINT UNSIGNED NOT NULL,"
                    + " habilidad_id BIGINT UNSIGNED NOT NULL,"
                    + " firma VARCHAR(64) NOT NULL,"
                    + " contenido_json TEXT NOT NULL,"
                    + " generado_en DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                    + " PRIMARY KEY (perfil_id, habilidad_id),"
                    + " CONSTRAINT fk_analisis_ia_perfil FOREIGN KEY (perfil_id)"
                    + " REFERENCES perfiles(id) ON DELETE CASCADE"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        } catch (Exception e) {
            log.warn("No se pudo verificar/crear la tabla constancia_analisis_ia: {}", e.getMessage());
        }
    }
}
