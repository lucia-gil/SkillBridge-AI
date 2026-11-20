package com.skillbridge.ai.service;

import com.skillbridge.ai.dto.NotificacionFila;
import com.skillbridge.ai.model.Notificacion;
import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.model.TipoNotificacion;
import com.skillbridge.ai.repository.NotificacionRepository;
import com.skillbridge.ai.repository.PerfilRepository;
import com.skillbridge.ai.repository.PreferenciaNotificacionRepository;
import com.skillbridge.ai.repository.TipoNotificacionRepository;
import com.skillbridge.ai.repository.UsuarioRepository;
import com.skillbridge.ai.util.Roles;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
        registrarAviso(perfilId, tipo.get(), tipoCodigo, titulo, detalle,
                enlaceAccion, CODIGO_A_TIPO_EVENTO.get(tipoCodigo));
    }

    public void crearInternoSeguridad(Long perfil, String enlace, String regla) {
        var tipo = tipoNotificacionRepository.findByCodigo("alerta").orElseThrow();
        Notificacion aviso = new Notificacion();
        aviso.setPerfilId(perfil);
        aviso.setTipoId(tipo.getId());
        aviso.setTitulo("Incidente de seguridad en revisión");
        aviso.setDetalle("Umbral alcanzado para " + regla);
        aviso.setEnlaceAccion(enlace);
        notificacionRepository.save(aviso);
    }

    private void registrarAviso(Long perfilId, TipoNotificacion tipo, String tipoCodigo, String titulo,
                                 String detalle, String enlaceAccion, String preferencia) {
        Notificacion n = new Notificacion();
        n.setPerfilId(perfilId);
        n.setTipoId(tipo.getId());
        n.setTitulo(titulo.codePointCount(0, titulo.length()) > 150
                ? titulo.substring(0, titulo.offsetByCodePoints(0, 149)) + "…" : titulo);
        n.setDetalle(detalle);
        n.setEnlaceAccion(enlaceAccion);
        notificacionRepository.save(n);
        enviarCorreoSiCorresponde(perfilId, tipoCodigo, titulo, detalle, enlaceAccion, preferencia);
    }

    private void enviarCorreoSiCorresponde(Long perfilId, String tipoCodigo, String titulo, String detalle,
                                          String enlaceAccion, String tipoEvento) {
        if (tipoEvento != null) {
            boolean tambienMail = preferenciaNotificacionRepository.findByPerfilIdAndTipoEvento(perfilId, tipoEvento)
                    .map(p -> PreferenciaNotificacionService.incluyeCorreo(p.getCanal()))
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
                    // El remitente ya es "SkillBridge AI": el asunto es el título del aviso.
                    emailService.enviar(new EmailService.Correo(usuario.getCorreo(), titulo,
                            etiquetaPara(tipoCodigo, enlaceAccion), titulo, detalle, enlaceCompleto,
                            textoBotonPara(tipoCodigo, enlaceAccion), tipoEvento != null)
                            .conNombre(usuario.getNombreCompleto()));
                }));
    }

    /** Respeta rutas internas que ya tienen una vista. Añade el rol solo a enlaces relativos. */
    private String construirEnlaceCorreo(Long perfilId, String rolOrganizacional, String enlaceAccion) {
        if (baseUrl == null || baseUrl.isBlank() || enlaceAccion == null || enlaceAccion.isBlank()) return null;
        try {
            var base = java.net.URI.create(baseUrl.trim());
            var enlace = java.net.URI.create(enlaceAccion);
            String ruta = enlace.getPath();
            if (!("https".equalsIgnoreCase(base.getScheme()) || "http".equalsIgnoreCase(base.getScheme()))
                    || base.getHost() == null || base.getUserInfo() != null
                    || base.getRawQuery() != null || base.getRawFragment() != null
                    || enlace.isAbsolute() || enlace.getRawAuthority() != null || ruta == null || ruta.isBlank()
                    || ruta.startsWith("//") || ruta.contains("\\")
                    || java.util.Arrays.stream(ruta.split("/")).anyMatch(s -> s.equals("..") || s.equals("."))) {
                return null;
            }
            String basePublica = base.toString().replaceAll("/+$", "");
            if (ruta.startsWith("/")) return basePublica + enlaceAccion;
            String rol = calcularRolEfectivoParaCorreo(perfilId, rolOrganizacional);
            return basePublica + "/" + Roles.slug(rol) + "/" + enlaceAccion;
        } catch (IllegalArgumentException ex) {
            return null;
        }
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

    /** Texto del botón del correo según el tipo de aviso (y, para "info", según a dónde lleva). */
    private String textoBotonPara(String tipoCodigo, String enlace) {
        if (enlace != null && enlace.startsWith("/administrador/")) {
            return enlace.endsWith("/auditoria.html") ? "Revisar auditoría" : "Ver usuarios y roles";
        }
        return switch (tipoCodigo) {
            case "asignacion" -> "Ver proyecto";
            case "foro_respuesta", "foro_cita" -> "Ver respuesta";
            case "entregable_nuevo", "entregable_revisado", "entregable_entregado" -> "Ver entregable";
            case "solicitud" -> "Revisar propuesta";
            case "resultado_ia" -> "Ver resultado";
            default -> {
                String e = enlace == null ? "" : enlace;
                if (e.startsWith("chat.html")) yield "Leer mensaje";
                if (e.contains("calendario.html")) yield "Ver calendario";
                if (e.startsWith("foro")) yield "Ver hilo";
                yield "Ir a SkillBridge AI";
            }
        };
    }

    /** Categoría que muestra el correo sobre el título. */
    private String etiquetaPara(String tipoCodigo, String enlace) {
        if (enlace != null && enlace.startsWith("/administrador/")) return "Administración";
        return switch (tipoCodigo) {
            case "asignacion" -> "Proyectos y asignaciones";
            case "foro_respuesta", "foro_cita" -> "Foros";
            case "entregable_nuevo", "entregable_revisado", "entregable_entregado" -> "Entregables";
            case "solicitud" -> "Solicitud de aprobación";
            case "resultado_ia" -> "AI Talent Matching";
            case "alerta" -> "Alerta";
            default -> {
                String e = enlace == null ? "" : enlace;
                if (e.startsWith("chat.html")) yield "Mensajes";
                if (e.contains("calendario.html")) yield "Calendario";
                if (e.startsWith("foro")) yield "Foros";
                yield "Aviso de SkillBridge AI";
            }
        };
    }

    /** Actividad de cuentas para administradores activos. Respeta su preferencia de alertas por correo. */
    @Transactional
    public void avisarAdministradores(String tipoCodigo, String titulo, String detalle) {
        var tipo = tipoNotificacionRepository.findByCodigo(tipoCodigo)
                .or(() -> tipoNotificacionRepository.findByCodigo("info"));
        if (tipo.isEmpty()) return;
        for (Long perfilId : perfilRepository.listarIdsConCuentaActivaPorRol(Roles.ADMINISTRADOR)) {
            registrarAviso(perfilId, tipo.get(), tipoCodigo, titulo, detalle,
                    "/administrador/usuarios.html", "alertas_criticas");
        }
    }

    /** Solo después de completar la sesión, incluido el segundo factor cuando corresponde. */
    @Transactional
    public void avisarInicioAdministrador(Long perfilId) {
        if (perfilId == null) return;
        var tipo = tipoNotificacionRepository.findByCodigo("info")
                .or(() -> tipoNotificacionRepository.findByCodigo("alerta"));
        tipo.ifPresent(t -> registrarAviso(perfilId, t, t.getCodigo(),
                "Inicio de sesión en Administración",
                "Se inició sesión en tu cuenta de administrador. Puedes revisar los accesos en Auditoría.",
                "/administrador/auditoria.html", "alertas_criticas"));
    }

    public List<NotificacionFila> listar(UsuarioSesion sesion) {
        return listar(sesion, Pageable.unpaged());
    }

    public List<NotificacionFila> recientes(UsuarioSesion sesion) {
        return listar(sesion, PageRequest.ofSize(4));
    }

    private List<NotificacionFila> listar(UsuarioSesion sesion, Pageable pagina) {
        if (sesion.getPerfilId() == null) return List.of();
        return notificacionRepository.listarPorPerfil(sesion.getPerfilId(), esAdmin(sesion), pagina).stream()
                .map(n -> new NotificacionFila(n.getId(), n.getTitulo(), n.getDetalle(), Boolean.TRUE.equals(n.getLeida()),
                        n.getTipo().getCodigo(), n.getTipo().getNombre(),
                        n.getFechaCreacion() != null ? n.getFechaCreacion().format(FORMATO) : "",
                        n.getEnlaceAccion()))
                .toList();
    }

    public long contarNoLeidas(UsuarioSesion sesion) {
        if (sesion.getPerfilId() == null) return 0;
        return notificacionRepository.contarNoLeidas(sesion.getPerfilId(), esAdmin(sesion));
    }

    @Transactional
    public boolean marcarLeida(Long id, UsuarioSesion sesion) {
        return sesion.getPerfilId() != null
                && notificacionRepository.marcarLeida(sesion.getPerfilId(), esAdmin(sesion), id) > 0;
    }

    @Transactional
    public int marcarTodasLeidas(UsuarioSesion sesion) {
        return sesion.getPerfilId() == null ? 0
                : notificacionRepository.marcarTodasLeidas(sesion.getPerfilId(), esAdmin(sesion));
    }

    private boolean esAdmin(UsuarioSesion sesion) {
        return Roles.ADMINISTRADOR.equals(sesion.getRolOrganizacional());
    }
}
