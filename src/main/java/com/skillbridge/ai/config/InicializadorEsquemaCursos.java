package com.skillbridge.ai.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Crea la tabla "cursos" (módulo Cursos del Administrador) si todavía no
 * existe, igual que Inicializador2fa con usuarios_2fa: con ddl-auto=none,
 * sin esto la pantalla administrador/cursos.html revienta en una base que
 * no corrió db/migracion_v9_cursos.sql a mano. Mismo SQL que ese script.
 * Si la tabla ya existe (creada a mano o por una versión anterior) no la toca.
 */
@Component
@Order(1)
public class InicializadorEsquemaCursos implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(InicializadorEsquemaCursos.class);
    private final JdbcTemplate jdbc;

    public InicializadorEsquemaCursos(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(String... args) {
        try {
            jdbc.execute("CREATE TABLE IF NOT EXISTS cursos ("
                    + " id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,"
                    + " titulo VARCHAR(150) NOT NULL,"
                    + " descripcion TEXT NULL,"
                    + " url VARCHAR(500) NULL,"
                    + " categoria_id BIGINT UNSIGNED NULL,"
                    + " habilidad_id BIGINT UNSIGNED NULL,"
                    + " nivel VARCHAR(20) NOT NULL DEFAULT 'BASICO',"
                    + " estado VARCHAR(20) NOT NULL DEFAULT 'PUBLICADO',"
                    + " duracion_horas INT NOT NULL DEFAULT 1,"
                    + " fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                    + " fecha_actualizacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,"
                    + " CONSTRAINT fk_curso_categoria FOREIGN KEY (categoria_id) REFERENCES categorias_habilidad(id),"
                    + " CONSTRAINT fk_curso_habilidad FOREIGN KEY (habilidad_id) REFERENCES habilidades(id),"
                    + " INDEX idx_cursos_estado_titulo (estado, titulo),"
                    + " INDEX idx_cursos_categoria (categoria_id)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        } catch (Exception e) {
            log.warn("No se pudo verificar/crear la tabla cursos: {}", e.getMessage());
        }
    }
}
