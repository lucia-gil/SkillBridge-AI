package com.skillbridge.ai.config;

import com.skillbridge.ai.model.TipoNotificacion;
import com.skillbridge.ai.repository.TipoNotificacionRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Asegura que cada evento real tenga un tipo de notificación en la base existente. */
@Component
@Order(1)
public class DataSeeder implements ApplicationRunner {

    private final TipoNotificacionRepository tipoNotificacionRepository;

    public DataSeeder(TipoNotificacionRepository tipoNotificacionRepository) {
        this.tipoNotificacionRepository = tipoNotificacionRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        crearSiFalta("info", "Información", "Novedades de SkillBridge AI");
        crearSiFalta("alerta", "Alerta", "Actividad que requiere atención");
        crearSiFalta("solicitud", "Solicitud", "Nueva solicitud por revisar");
        crearSiFalta("resultado_ia", "Resultado de IA", "Resultado de una propuesta o proceso de IA");
        crearSiFalta("entregable_nuevo", "Nuevo entregable", "Se publicó un entregable en tu proyecto");
        crearSiFalta("entregable_revisado", "Entrega revisada", "El responsable revisó tu entrega");
        crearSiFalta("entregable_entregado", "Entrega recibida", "Un colaborador envió o actualizó su entrega");
        crearSiFalta("foro_cita", "Mención en foro", "Alguien respondió a tu mensaje en el foro");
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
