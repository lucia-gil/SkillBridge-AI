package com.skillbridge.ai.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Esquema v10 (resumen de hilos con IA): agrega, solo si todavia no existen,
 * las columnas donde se guarda el resumen generado por el Asistente IA de
 * cada hilo del foro (ForoService.generarResumenIa /
 * AsistenteIaService.resumirHilo), para no tener que volver a llamar a
 * Claude cada vez que alguien lo abre. Mismo patron que
 * InicializadorEsquemaV9 (ddl-auto=none, MySQL 8 sin "ADD COLUMN IF NOT
 * EXISTS"); el SQL equivalente esta en db/skillbridge_db.sql.
 */
@Component
@Order(1)
public class InicializadorEsquemaV10 implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(InicializadorEsquemaV10.class);
    private final JdbcTemplate jdbc;

    public InicializadorEsquemaV10(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(String... args) {
        agregarSiFalta("foro_publicaciones", "resumen_ia", "TEXT NULL");
        agregarSiFalta("foro_publicaciones", "resumen_ia_generado_en", "DATETIME NULL");
        limpiarResumenesFallidos();
    }

    /**
     * Antes, si Claude no respondía (sin clave, límite gratuito, error), el
     * aviso se guardaba como si fuera el resumen del hilo y quedaba fijo.
     * Se borran esos avisos para que el hilo vuelva a ofrecer "Resumir con IA".
     */
    private void limpiarResumenesFallidos() {
        try {
            int n = jdbc.update("UPDATE foro_publicaciones SET resumen_ia = NULL, resumen_ia_generado_en = NULL "
                    + "WHERE resumen_ia LIKE 'El Asistente IA todavía no está configurado%' "
                    + "OR resumen_ia LIKE 'No pude generar una respuesta%' "
                    + "OR resumen_ia LIKE 'No pude conectarme al asistente%' "
                    + "OR resumen_ia LIKE 'El asistente alcanzó el límite%' "
                    + "OR resumen_ia LIKE 'Ocurrió un error inesperado al conectar%'");
            if (n > 0) log.info("Foros: se quitaron {} resumen(es) con IA que en realidad eran avisos de error.", n);
        } catch (Exception e) {
            log.warn("Foros: no se pudieron revisar los resúmenes con IA: {}", e.getMessage());
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
                log.info("Esquema v10: agregada la columna {}.{}", tabla, columna);
            }
        } catch (Exception e) {
            log.warn("Esquema v10: no se pudo verificar/agregar {}.{}: {}", tabla, columna, e.getMessage());
        }
    }
}
