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

    /** tipo_evento -> true si además de "app" también quiere "mail". */
    public Map<String, Boolean> obtener(Long perfilId) {
        Map<String, Boolean> mapa = new LinkedHashMap<>();
        for (String tipo : TIPOS_EVENTO) mapa.put(tipo, true); // por defecto: app + mail
        repository.findByPerfilId(perfilId).forEach(p -> mapa.put(p.getTipoEvento(), "app_mail".equals(p.getCanal())));
        return mapa;
    }

    @Transactional
    public void actualizar(Long perfilId, Map<String, Boolean> seleccion) {
        for (String tipoEvento : TIPOS_EVENTO) {
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
