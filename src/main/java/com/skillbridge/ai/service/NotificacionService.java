package com.skillbridge.ai.service;

import com.skillbridge.ai.dto.NotificacionFila;
import com.skillbridge.ai.model.Notificacion;
import com.skillbridge.ai.model.TipoNotificacion;
import com.skillbridge.ai.repository.NotificacionRepository;
import com.skillbridge.ai.repository.PerfilRepository;
import com.skillbridge.ai.repository.PreferenciaNotificacionRepository;
import com.skillbridge.ai.repository.TipoNotificacionRepository;
import com.skillbridge.ai.repository.UsuarioRepository;
import com.skillbridge.ai.util.Roles;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
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
    private final UsuarioRepository usuarioRepository;
    private final EmailService emailService;
    private final JdbcTemplate jdbcTemplate;

    @Value("${app.base-url:}")
    private String baseUrl;

    public NotificacionService(NotificacionRepository notificacionRepository, TipoNotificacionRepository tipoNotificacionRepository,
                                PreferenciaNotificacionRepository preferenciaNotificacionRepository, PerfilRepository perfilRepository,
                                UsuarioRepository usuarioRepository, EmailService emailService, JdbcTemplate jdbcTemplate) {
        this.notificacionRepository = notificacionRepository;
        this.tipoNotificacionRepository = tipoNotificacionRepository;
        this.preferenciaNotificacionRepository = preferenciaNotificacionRepository;
        this.perfilRepository = perfilRepository;
        this.usuarioRepository = usuarioRepository;
        this.emailService = emailService;
        this.jdbcTemplate = jdbcTemplate;
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
        enviarCorreoSiCorresponde(perfilId, tipoCodigo, titulo, detalle, enlaceAccion);
    }

    /**
     * Ademas de guardar la notificacion en la campana de la app, manda un
     * correo real si el tipo de evento no tiene preferencia configurable
     * (siempre se manda) o si el usuario tiene activado "tambien por correo"
     * para ese tipo (canal='app_mail' en preferencias_notificacion; sin fila
     * guardada se manda por defecto, igual que PreferenciaNotificacionService.obtener()).
     */
    private void enviarCorreoSiCorresponde(Long perfilId, String tipoCodigo, String titulo, String detalle, String enlaceAccion) {
        String tipoEvento = CODIGO_A_TIPO_EVENTO.get(tipoCodigo);
        if (tipoEvento != null) {
            boolean tambienMail = preferenciaNotificacionRepository.findByPerfilIdAndTipoEvento(perfilId, tipoEvento)
                    .map(p -> "app_mail".equals(p.getCanal()))
                    .orElse(true);
            if (!tambienMail) return;
        }
        // No usar perfil.getUsuario() aqui: si el Perfil fue creado/guardado en
        // esta MISMA transaccion (ej. autoregistro), el contexto de persistencia
        // ya tiene esa instancia administrada con la asociacion "usuario" (de
        // solo lectura, insertable/updatable=false) todavia sin poblar, y el
        // "join fetch" de buscarConUsuario() no la rellena retroactivamente -
        // causaba NullPointerException al registrar un usuario nuevo. Se busca
        // el Usuario por separado, por su id, que siempre esta seteado.
        perfilRepository.findById(perfilId).ifPresent(perfil ->
                usuarioRepository.findById(perfil.getUsuarioId()).ifPresent(usuario -> {
                    String enlaceCompleto = construirEnlaceCorreo(perfilId, usuario.getRolOrganizacional(), enlaceAccion);
                    String textoBoton = textoBotonPara(tipoCodigo);
                    emailService.enviarNotificacion(usuario.getCorreo(), "SkillBridge AI: " + titulo, titulo, detalle,
                            enlaceCompleto, textoBoton);
                }));
    }

    /**
     * Arma el link absoluto de la notificacion para el correo, a partir del
     * enlace relativo que ya se guarda con la notificacion (ej. "proyectos.html",
     * "foro-hilo.html?id=5") - ese mismo enlace ya funciona dentro de la app
     * porque es relativo a la carpeta del rol del usuario (se resuelve solo en
     * el navegador). Para el correo hace falta anteponer el dominio y la
     * carpeta del rol. No se puede reusar AuthService.calcularRolEfectivo(...)
     * aqui porque AuthService ya depende de NotificacionService (dependencia
     * circular) - se replica la misma regla (administrador/resource_manager
     * son puestos fijos; si no, se revisa si tiene una asignacion activa como
     * project_manager; si no, colaborador), igual que en AuthService.
     */
    private String construirEnlaceCorreo(Long perfilId, String rolOrganizacional, String enlaceAccion) {
        if (baseUrl == null || baseUrl.isBlank() || enlaceAccion == null || enlaceAccion.isBlank()) return null;
        String rolEfectivo = calcularRolEfectivoParaCorreo(perfilId, rolOrganizacional);
        return baseUrl + "/" + Roles.slug(rolEfectivo) + "/" + enlaceAccion;
    }

    private String calcularRolEfectivoParaCorreo(Long perfilId, String rolOrganizacional) {
        if (Roles.ADMINISTRADOR.equals(rolOrganizacional)) return Roles.ADMINISTRADOR;
        if (Roles.RESOURCE_MANAGER.equals(rolOrganizacional)) return Roles.RESOURCE_MANAGER;
        if (perfilId != null) {
            try {
                Integer count = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM asignaciones WHERE perfil_id = ? AND rol_en_proyecto = 'project_manager' AND estado = 'activa'",
                        Integer.class, perfilId);
                if (count != null && count > 0) return Roles.PROJECT_MANAGER;
            } catch (Exception ex) {
                // Igual que en AuthService: si la consulta falla, no se rompe el
                // envio del correo por esto - se asume colaborador.
            }
        }
        return Roles.COLABORADOR;
    }

    private String textoBotonPara(String tipoCodigo) {
        return switch (tipoCodigo) {
            case "asignacion" -> "Ver proyecto";
            case "foro_respuesta" -> "Ver respuesta";
            case "entregable_nuevo", "entregable_revisado" -> "Ver entregable";
            case "solicitud", "resultado_ia" -> "Ver en SkillBridge AI";
            default -> "Ir a SkillBridge AI";
        };
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
