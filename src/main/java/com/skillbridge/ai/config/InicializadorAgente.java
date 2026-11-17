package com.skillbridge.ai.config;
import javax.sql.DataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="agente.init-schema", havingValue="true", matchIfMissing=true)
public class InicializadorAgente {
    private final DataSource fuente;
    public InicializadorAgente(DataSource fuente) { this.fuente=fuente; }
    @PostConstruct public void iniciar() {
        new ResourceDatabasePopulator(new ClassPathResource("db/agente.sql")).execute(fuente);
    }
}
