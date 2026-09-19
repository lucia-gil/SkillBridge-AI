package com.skillbridge.ai.service;

import com.skillbridge.ai.dto.NotificacionFila;
import com.skillbridge.ai.model.Notificacion;
import com.skillbridge.ai.model.TipoNotificacion;
import com.skillbridge.ai.repository.NotificacionRepository;
import com.skillbridge.ai.repository.PerfilRepository;
import com.skillbridge.ai.repository.PreferenciaNotificacionRepository;
import com.skillbridge.ai.repository.TipoNotificacionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Centro de notificaciones real (tabla "notificaciones" + "tipos_notificacion").
 * No hay ningun generador de notificaciones de fabrica: se crean como efecto
 * secundario de acciones reales de esta entrega (ver llamadas a crear(...)
 * desde ProyectoService al asignar un colaborador y desde ForoService al
 * responder un hilo) - por eso una base de datos recien creada empieza sin
 * notificaciones, hasta que ocurre la primera accion que las genera.
 */
@Service
public class NotificacionService {

    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    // tipoCodigo (el que usan ProyectoService/ForoService/TalentMatchingService/
    // EventoService al llamar crear(...)) -> tipo_evento de "preferencias_notificacion"
    // (los 4 valores que expone la pantalla "Preferencias de aviso" de Mi cuenta).
    // Un tipoCodigo que no aparece aqui (ej. "foro_respuesta", "info") no tiene
    // preferencia configurable todavia, asi que su correo siempre se envia.
    private static final Map<String, String> CODIGO_A_TIPO_EVENTO = Map.of(
            "asignacion", "alertas_criticas",
            "solicitud", "solicitudes_aprobacion",
            "resultado_ia", "resultados_ia"
    );

    private final NotificacionRepository notificacionRepository;
    private final TipoNotificacionRepository tipoNotificacionRepository;
    private final PreferenciaNotificacionRepository preferenciaNotificacionRepository;
    private final PerfilRepository perfilRepository;
    private final EmailService emailService;

    public NotificacionService(NotificacionRepository notificacionRepository, TipoNotificacionRepository tipoNotificacionRepository,
                                PreferenciaNotificacionRepository preferenciaNotificacionRepository, PerfilRepository perfilRepository,
                                EmailService emailService) {
        this.notificacionRepository = notificacionRepository;
        this.tipoNotificacionRepository = tipoNotificacionRepository;
        this.preferenciaNotificacionRepository = preferenciaNotificacionRepository;
        this.perfilRepository = perfilRepository;
        this.emailService = emailService;
    }

    @Transactional
    public void crear(Long perfilId, String tipoCodigo, String titulo, String detalle, String enlaceAccion) {
        if (perfilId == null) return;
        Optional<TipoNotificacion> tipo = tipoNotificacionRepository.findByCodigo(tipoCodigo);
        if (tipo.isEmpty()) return; // catálogo sin ese código: no se rompe el flujo principal por esto
        Notificacion n = new Notificacion();
        n.setPerfilId(perfilId);
        n.setTipoId(tipo.get().getId());
        n.setTitulo(titulo);
        n.setDetalle(detalle);
        n.setEnlaceAccion(enlaceAccion);
        notificacionRepository.save(n);
        enviarCorreoSiCorresponde(perfilId, tipoCodigo, titulo, detalle);
    }

    /**
     * Ademas de guardar la notificacion en la campana de la app, manda un
     * correo real si el tipo de evento no tiene preferencia configurable
     * (siempre se manda) o si el usuario tiene activado "tambien por correo"
     * para ese tipo (canal='app_mail' en preferencias_notificacion; sin fila
     * guardada se manda por defecto, igual que PreferenciaNotificacionService.obtener()).
     */
    private void enviarCorreoSiCorresponde(Long perfilId, String tipoCodigo, String titulo, String detalle) {
        String tipoEvento = CODIGO_A_TIPO_EVENTO.get(tipoCodigo);
        if (tipoEvento != null) {
            boolean tambienMail = preferenciaNotificacionRepository.findByPerfilIdAndTipoEvento(perfilId, tipoEvento)
                    .map(p -> "app_mail".equals(p.getCanal()))
                    .orElse(true);
            if (!tambienMail) return;
        }
        perfilRepository.buscarConUsuario(perfilId).ifPresent(perfil -> {
            String correo = perfil.getUsuario().getCorreo();
            emailService.enviarNotificacion(correo, "SkillBridge AI: " + titulo, titulo, detalle);
        });
    }

    public List<NotificacionFila> listar(Long perfilId) {
        return notificacionRepository.listarPorPerfil(perfilId).stream()
                .map(n -> new NotificacionFila(n.getId(), n.getTitulo(), n.getDetalle(), Boolean.TRUE.equals(n.getLeida()),
                        n.getTipo().getCodigo(), n.getTipo().getNombre(),
                        n.getFechaCreacion() != null ? n.getFechaCreacion().format(FORMATO) : "",
                        n.getEnlaceAccion()))
                .collect(Collectors.toList());
    }

    public long contarNoLeidas(Long perfilId) {
        if (perfilId == null) return 0;
        return notificacionRepository.countByPerfilIdAndLeidaFalse(perfilId);
    }

    @Transactional
    public void marcarLeida(Long id, Long perfilId) {
        notificacionRepository.findById(id).ifPresent(n -> {
            if (n.getPerfilId().equals(perfilId)) {
                n.setLeida(true);
                notificacionRepository.save(n);
            }
        });
    }

    @Transactional
    public void marcarTodasLeidas(Long perfilId) {
        notificacionRepository.marcarTodasLeidas(perfilId);
    }
}
