package com.skillbridge.ai.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Esquema v9 (perfil profesional del colaborador): agrega, solo si todavía no
 * existen, las columnas de contacto y presentación que muestra
 * resource-manager/colaborador-detalle.html y que el colaborador edita en
 * "Mi cuenta". Mismo patrón que InicializadorEsquemaV7 (ddl-auto=none, MySQL 8
 * sin "ADD COLUMN IF NOT EXISTS"); el SQL equivalente está en
 * db/skillbridge_db.sql.
 *
 * También asegura las columnas de la v8 (linkedin_url, github_url), que hasta
 * ahora solo se agregaban corriendo skillbridge_db.sql a mano: una base
 * nueva (p. ej. RDS) sin ese script fallaba al abrir cualquier perfil.
 */
@Component
@Order(1)
public class InicializadorEsquemaV9 implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(InicializadorEsquemaV9.class);
    private final JdbcTemplate jdbc;

    public InicializadorEsquemaV9(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(String... args) {
        // v8 (por si la base no tiene skillbridge_db.sql aplicada)
        agregarSiFalta("perfiles", "linkedin_url", "VARCHAR(500) NULL");
        agregarSiFalta("perfiles", "github_url", "VARCHAR(500) NULL");
        agregarSiFalta("proyectos", "github_url", "VARCHAR(500) NULL");
        // v9
        agregarSiFalta("perfiles", "portafolio_url", "VARCHAR(500) NULL");
        agregarSiFalta("perfiles", "telefono", "VARCHAR(30) NULL");
        agregarSiFalta("perfiles", "ubicacion", "VARCHAR(120) NULL");
        agregarSiFalta("perfiles", "etiquetas", "JSON NULL");
    }

    private void agregarSiFalta(String tabla, String columna, String definicion) {
        try {
            Integer existe = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.COLUMNS "
                            + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                    Integer.class, tabla, columna);
            if (existe != null && existe == 0) {
                jdbc.execute("ALTER TABLE " + tabla + " ADD COLUMN " + columna + " " + definicion);
                log.info("Esquema v9: agregada la columna {}.{}", tabla, columna);
            }
        } catch (Exception e) {
            log.warn("Esquema v9: no se pudo verificar/agregar {}.{}: {}", tabla, columna, e.getMessage());
        }
    }
}
