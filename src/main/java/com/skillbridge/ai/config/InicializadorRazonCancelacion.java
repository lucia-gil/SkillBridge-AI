package com.skillbridge.ai.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Agrega la columna razon_cancelacion a proyectos si no existe (la app corre
 * con ddl-auto=none). Guarda el motivo obligatorio que se pide al cancelar
 * un proyecto (idea de la profesora: solo se puede eliminar un proyecto que
 * ya este "cancelado", y cancelar exige justificar el porque). Si falla
 * (por ejemplo porque ya existe), solo se avisa: no se bloquea el arranque.
 */
@Component
public class InicializadorRazonCancelacion implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(InicializadorRazonCancelacion.class);
    private final JdbcTemplate jdbc;

    public InicializadorRazonCancelacion(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(String... args) {
        try {
            jdbc.execute("ALTER TABLE proyectos ADD COLUMN razon_cancelacion TEXT NULL");
        } catch (Exception e) {
            log.warn("No se pudo agregar razon_cancelacion a proyectos (probablemente ya existe): {}", e.getMessage());
        }
    }
}
