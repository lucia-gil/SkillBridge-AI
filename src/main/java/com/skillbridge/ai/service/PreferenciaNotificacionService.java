package com.skillbridge.ai.service;

import com.skillbridge.ai.model.PreferenciaNotificacion;
import com.skillbridge.ai.repository.PreferenciaNotificacionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * "Preferencias de aviso" real, sobre la tabla "preferencias_notificacion".
 * tipo_evento es un ENUM cerrado de 4 valores en el esquema; se simplifica
 * el canal a un toggle "también por correo" (canal='app_mail') vs. solo en
 * la plataforma (canal='app') en vez de exponer los 5 valores del ENUM de
 * canal, que para esta UI son redundantes entre sí (app/solo_app y
 * mail/solo_mail expresan lo mismo).
 */
@Service
public class PreferenciaNotificacionService {

    public static final List<String> TIPOS_EVENTO = List.of(
            "alertas_criticas", "solicitudes_aprobacion", "resultados_ia", "resumen_semanal");

    private final PreferenciaNotificacionRepository repository;

    public PreferenciaNotificacionService(PreferenciaNotificacionRepository repository) {
        this.repository = repository;
    }

    /** tipo_evento -> true si quiere recibirlo por correo. */
    public Map<String, Boolean> obtener(Long perfilId) {
        Map<String, Boolean> mapa = new LinkedHashMap<>();
        for (String tipo : TIPOS_EVENTO) mapa.put(tipo, true); // por defecto: app + mail
        repository.findByPerfilId(perfilId).forEach(p -> mapa.put(p.getTipoEvento(), incluyeCorreo(p.getCanal())));
        return mapa;
    }

    /**
     * true si el canal incluye correo. El ENUM admite 'mail', 'app_mail' y
     * 'solo_mail' (los datos de demo usan 'solo_mail'); antes solo se tomaba
     * 'app_mail' y esas personas dejaban de recibir el correo que habían pedido.
     */
    public static boolean incluyeCorreo(String canal) {
        return "app_mail".equals(canal) || "mail".equals(canal) || "solo_mail".equals(canal);
    }

    public static List<String> tiposPara(String rol) {
        if ("administrador".equals(rol)) return List.of("alertas_criticas", "resumen_semanal");
        return TIPOS_EVENTO;
    }

    @Transactional
    public void actualizar(Long perfilId, Map<String, Boolean> seleccion) {
        actualizar(perfilId, seleccion, TIPOS_EVENTO);
    }

    @Transactional
    public void actualizar(Long perfilId, Map<String, Boolean> seleccion, List<String> tiposVisibles) {
        for (String tipoEvento : tiposVisibles) {
            boolean tambienMail = Boolean.TRUE.equals(seleccion.get(tipoEvento));
            PreferenciaNotificacion pref = repository.findByPerfilIdAndTipoEvento(perfilId, tipoEvento)
                    .orElseGet(() -> {
                        PreferenciaNotificacion nueva = new PreferenciaNotificacion();
                        nueva.setPerfilId(perfilId);
                        nueva.setTipoEvento(tipoEvento);
                        return nueva;
                    });
            pref.setCanal(tambienMail ? "app_mail" : "app");
            repository.save(pref);
        }
    }

    /** Qué avisos cubre cada preferencia (texto bajo cada interruptor). */
    public static String descripcion(String tipoEvento) {
        return switch (tipoEvento) {
            case "alertas_criticas" -> "Asignaciones, cambios de dedicación, salidas de proyectos y cambios de Project Manager.";
            case "solicitudes_aprobacion" -> "Propuestas de asignación que esperan la aprobación de un Resource Manager.";
            case "resultados_ia" -> "Resultado de tus propuestas de AI Talent Matching.";
            case "resumen_semanal" -> "Cada lunes a las 8:00, un correo con tu semana: pendientes, carga y agenda.";
            default -> "";
        };
    }

    public static String etiqueta(String tipoEvento) {
        return switch (tipoEvento) {
            case "alertas_criticas" -> "Alertas críticas";
            case "solicitudes_aprobacion" -> "Solicitudes de aprobación";
            case "resultados_ia" -> "Resultados de IA";
            case "resumen_semanal" -> "Resumen semanal";
            default -> tipoEvento;
        };
    }
}
