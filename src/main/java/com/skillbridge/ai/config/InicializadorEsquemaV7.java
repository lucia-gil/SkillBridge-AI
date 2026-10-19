package com.skillbridge.ai.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Esquema v7 (modelo de permisos y consistencia de roles): agrega, solo si
 * todavía no existen, las columnas nuevas. La app corre con ddl-auto=none,
 * así que igual que Inicializador2fa se hace aquí de forma idempotente; la
 * misma alteración está en db/migracion_v7_consistencia_roles.sql para
 * quien prefiera aplicarla a mano.
 *
 *  - proyectos.fecha_pausa / motivo_pausa / fecha_reanudacion_estimada
 *    (pausa = el equipo sale del proyecto, ver fase 2)
 *  - correos_autorizados.rol_asignado (invitación con rol, ver fase 3)
 *  - propuestas_asignacion.origen (talent_matching | manual | reincorporacion)
 *
 * MySQL 8 no soporta "ADD COLUMN IF NOT EXISTS", por eso se consulta
 * information_schema antes de cada ALTER.
 */
@Component
@Order(0)
public class InicializadorEsquemaV7 implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(InicializadorEsquemaV7.class);
    private final JdbcTemplate jdbc;

    public InicializadorEsquemaV7(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(String... args) {
        agregarSiFalta("proyectos", "fecha_pausa", "DATE NULL");
        agregarSiFalta("proyectos", "motivo_pausa", "VARCHAR(500) NULL");
        agregarSiFalta("proyectos", "fecha_reanudacion_estimada", "DATE NULL");
        agregarSiFalta("correos_autorizados", "rol_asignado", "ENUM('administrador','resource_manager') NULL");
        agregarSiFalta("propuestas_asignacion", "origen", "VARCHAR(20) NOT NULL DEFAULT 'talent_matching'");
    }

    private void agregarSiFalta(String tabla, String columna, String definicion) {
        try {
            Integer existe = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.COLUMNS "
                            + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                    Integer.class, tabla, columna);
            if (existe != null && existe == 0) {
                jdbc.execute("ALTER TABLE " + tabla + " ADD COLUMN " + columna + " " + definicion);
                log.info("Esquema v7: agregada la columna {}.{}", tabla, columna);
            }
        } catch (Exception e) {
            log.warn("Esquema v7: no se pudo verificar/agregar {}.{}: {}", tabla, columna, e.getMessage());
        }
    }
}
