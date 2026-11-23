package com.skillbridge.ai.config;
import javax.sql.DataSource;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;
@Component
@Order(2)
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="agente.init-schema", havingValue="true", matchIfMissing=true)
public class InicializadorAgente implements CommandLineRunner {
    private final DataSource fuente;
    public InicializadorAgente(DataSource fuente) { this.fuente=fuente; }
    @PostConstruct public void iniciar() {
        new ResourceDatabasePopulator(new ClassPathResource("db/agente.sql")).execute(fuente);
    }

    @Override
    public void run(String... args) {
        // Las tablas base del historial ya fueron comprobadas por InicializadorTablasExtra.
        new ResourceDatabasePopulator(new ClassPathResource("db/asistente-organizacion.sql")).execute(fuente);
    }
}
