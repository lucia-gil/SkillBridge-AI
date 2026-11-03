package com.skillbridge.ai.config;

import com.skillbridge.ai.service.ForoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Al arrancar, asigna tópico (y etiquetas, si no tenían) a los hilos del foro
 * que todavía no tienen uno: los creados antes de skillbridge_db.sql.
 * Solo toca hilos con topico_id NULL, así que nunca pisa un tópico elegido
 * a mano. Si la migración v6 no se ejecutó, solo avisa en el log y deja
 * arrancar la aplicación (mismo criterio que InicializadorRazonCancelacion).
 */
@Component
public class InicializadorTopicosForo implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(InicializadorTopicosForo.class);
    private final ForoService foroService;

    public InicializadorTopicosForo(ForoService foroService) {
        this.foroService = foroService;
    }

    @Override
    public void run(String... args) {
        try {
            int clasificados = foroService.clasificarHilosSinTopico();
            if (clasificados > 0) {
                log.info("Foros: se asignó tópico automáticamente a {} hilo(s) existente(s).", clasificados);
            }
        } catch (Exception e) {
            log.warn("Foros: no se pudieron clasificar los hilos existentes. ¿Se ejecutó db/skillbridge_db.sql? ({})",
                    e.getMessage());
        }
    }
}
