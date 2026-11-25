package com.skillbridge.ai.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Esquema v11: aplica al arrancar, solo si faltan, los cambios de
 * db/migracion_admin.sql y db/migracion_identidad_dni.sql. Así una base
 * creada con una versión anterior de skillbridge_db.sql no rompe el
 * calendario (tipos_audiencia.politica), las invitaciones
 * (correos_autorizados.estado_envio / ultimo_envio / origen 'masiva_excel')
 * ni el registro con DNI (tablas identidad_dni e identidad_dni_config).
 */
@Component
@Order(1)
public class InicializadorEsquemaV11 implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(InicializadorEsquemaV11.class);
    private final JdbcTemplate jdbc;

    public InicializadorEsquemaV11(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(String... args) {
        agregarSiFalta("tipos_audiencia", "politica", "VARCHAR(40) NULL");
        agregarSiFalta("correos_autorizados", "estado_envio", "VARCHAR(32) NULL");
        agregarSiFalta("correos_autorizados", "ultimo_envio", "DATETIME NULL");
        ampliarOrigenCarga();
        crearTablasIdentidad();
    }

    private void ampliarOrigenCarga() {
        try {
            String tipo = jdbc.queryForObject(
                    "SELECT COLUMN_TYPE FROM information_schema.COLUMNS "
                            + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'correos_autorizados' AND COLUMN_NAME = 'origen_carga'",
                    String.class);
            if (tipo != null && tipo.startsWith("enum(") && !tipo.contains("masiva_excel")) {
                jdbc.execute("ALTER TABLE correos_autorizados MODIFY origen_carga "
                        + "ENUM('individual','masiva_csv','masiva_excel') NOT NULL DEFAULT 'individual'");
                log.info("Esquema v11: correos_autorizados.origen_carga admite 'masiva_excel'.");
            }
        } catch (Exception e) {
            log.warn("Esquema v11: no se pudo revisar correos_autorizados.origen_carga: {}", e.getMessage());
        }
    }

    private void crearTablasIdentidad() {
        try {
            jdbc.execute("CREATE TABLE IF NOT EXISTS identidad_dni ("
                    + " huella BINARY(32) PRIMARY KEY,"
                    + " usuario_id BIGINT UNSIGNED NULL UNIQUE,"
                    + " invitacion_id BIGINT UNSIGNED NULL UNIQUE,"
                    + " terminacion CHAR(4) CHARACTER SET ascii NOT NULL,"
                    + " verificado_en DATETIME NULL,"
                    + " CONSTRAINT fk_identidad_dni_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE,"
                    + " CONSTRAINT fk_identidad_dni_invitacion FOREIGN KEY (invitacion_id) REFERENCES correos_autorizados(id) ON DELETE CASCADE,"
                    + " CONSTRAINT chk_identidad_dni_estado CHECK ("
                    + "  (usuario_id IS NULL AND invitacion_id IS NOT NULL AND verificado_en IS NULL)"
                    + "  OR (usuario_id IS NOT NULL AND invitacion_id IS NULL AND verificado_en IS NOT NULL))"
                    + ") ENGINE=InnoDB");
            jdbc.execute("CREATE TABLE IF NOT EXISTS identidad_dni_config ("
                    + " id TINYINT UNSIGNED PRIMARY KEY,"
                    + " huella_clave BINARY(32) NOT NULL,"
                    + " CONSTRAINT chk_identidad_dni_config CHECK (id = 1)"
                    + ") ENGINE=InnoDB");
        } catch (Exception e) {
            log.warn("Esquema v11: no se pudieron verificar/crear las tablas de identidad por DNI: {}", e.getMessage());
        }
    }

    private void agregarSiFalta(String tabla, String columna, String definicion) {
        try {
            Integer existe = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.COLUMNS "
                            + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                    Integer.class, tabla, columna);
            if (existe != null && existe == 0) {
                jdbc.execute("ALTER TABLE " + tabla + " ADD COLUMN " + columna + " " + definicion);
                log.info("Esquema v11: agregada la columna {}.{}", tabla, columna);
            }
        } catch (Exception e) {
            log.warn("Esquema v11: no se pudo verificar/agregar {}.{}: {}", tabla, columna, e.getMessage());
        }
    }
}
