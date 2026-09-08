package com.skillbridge.ai.config;

import com.skillbridge.ai.model.TipoNotificacion;
import com.skillbridge.ai.repository.TipoNotificacionRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Siembra idempotente de catálogo al arrancar la aplicación.
 *
 * skillbridge_db_v4.sql (INSERT INTO tipos_notificacion, línea ~646) trae 4
 * códigos: alerta, solicitud, info, resultado_ia. Esta iteración agrega dos
 * eventos reales que el esquema no anticipaba pero que ahora sí ocurren de
 * verdad (asignar un colaborador a un proyecto, responder un hilo de
 * foro) - en vez de pedirle al usuario que edite a mano su script SQL ya
 * cargado, se insertan aquí solo si faltan (existsByCodigo), así que
 * correr la aplicación varias veces nunca duplica filas ni falla si ya
 * existen (por ejemplo, si el usuario las agregó manualmente).
 */
@Component
@Order(1)
public class DataSeeder implements ApplicationRunner {

    private final TipoNotificacionRepository tipoNotificacionRepository;

    public DataSeeder(TipoNotificacionRepository tipoNotificacionRepository) {
        this.tipoNotificacionRepository = tipoNotificacionRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        crearSiFalta("asignacion", "Asignación de proyecto",
                "Se te asignó o se te retiró de un proyecto");
        crearSiFalta("foro_respuesta", "Respuesta en foro",
                "Alguien respondió un hilo que sigues");
    }

    private void crearSiFalta(String codigo, String nombre, String descripcion) {
        if (tipoNotificacionRepository.existsByCodigo(codigo)) {
            return;
        }
        TipoNotificacion tipo = new TipoNotificacion();
        tipo.setCodigo(codigo);
        tipo.setNombre(nombre);
        tipo.setDescripcion(descripcion);
        tipoNotificacionRepository.save(tipo);
    }
}
