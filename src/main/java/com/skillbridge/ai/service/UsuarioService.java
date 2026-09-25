package com.skillbridge.ai.service;

import com.skillbridge.ai.dto.UsuarioFila;
import com.skillbridge.ai.model.CorreoAutorizado;
import com.skillbridge.ai.model.Perfil;
import com.skillbridge.ai.model.Usuario;
import com.skillbridge.ai.repository.CorreoAutorizadoRepository;
import com.skillbridge.ai.repository.PerfilRepository;
import com.skillbridge.ai.repository.UsuarioRepository;
import com.skillbridge.ai.service.ConfiguracionService;
import com.skillbridge.ai.util.OperacionInvalidaException;
import com.skillbridge.ai.util.Roles;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * CRUD real de "Usuarios y roles" (administrador/usuarios.html).
 *
 * "Invitar usuario" NO crea la fila en usuarios directamente: el esquema
 * (correos_autorizados, ver skillbridge_db_v4.sql seccion 2) modela el
 * registro como autoregistro con correo pre-autorizado, asi que invitar =
 * autorizar el correo; la fila en usuarios se crea recien cuando esa
 * persona completa /auth/registro.html.
 */
@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PerfilRepository perfilRepository;
    private final CorreoAutorizadoRepository correoAutorizadoRepository;
    private final AuditoriaService auditoriaService;
    private final ConfiguracionService configuracionService;
    private final EmailService emailService;

    @Value("${app.base-url:}")
    private String baseUrl;

    public UsuarioService(UsuarioRepository usuarioRepository,
                           PerfilRepository perfilRepository,
                           CorreoAutorizadoRepository correoAutorizadoRepository,
                           AuditoriaService auditoriaService,
                           ConfiguracionService configuracionService,
                           EmailService emailService) {
        this.usuarioRepository = usuarioRepository;
        this.perfilRepository = perfilRepository;
        this.correoAutorizadoRepository = correoAutorizadoRepository;
        this.auditoriaService = auditoriaService;
        this.configuracionService = configuracionService;
        this.emailService = emailService;
    }

    private static final Pattern PATRON_CORREO_GENERICO = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$", Pattern.CASE_INSENSITIVE);

    /**
     * Mismo dominio configurable que usa AuthService al validar /registro
     * (configuracion_global.dominio_correo_permitido, RF08) - antes estaba
     * hardcodeado a "nexacorp.com" aquí y no reflejaba lo que el
     * Administrador configura en Administrador > Configuración. Vacío o
     * "*" = cualquier correo con formato válido (para invitar con Gmail,
     * @pucp.edu.pe, etc. y poder probar las notificaciones por correo real).
     */
    private Pattern patronCorreoDominio() {
        String dominio = configuracionService.valor(configuracionService.obtenerMapa(), "dominio_correo_permitido", "nexacorp.com").trim();
        if (dominio.isEmpty() || dominio.equals("*")) {
            return PATRON_CORREO_GENERICO;
        }
        return Pattern.compile("^[A-Za-z0-9+_.-]+@" + Pattern.quote(dominio) + "$", Pattern.CASE_INSENSITIVE);
    }

    public List<UsuarioFila> listar() {
        Map<Long, String> cargoPorUsuarioId = new HashMap<>();
        for (Perfil p : perfilRepository.findAll()) {
            cargoPorUsuarioId.put(p.getUsuarioId(), p.getCargo());
        }
        return usuarioRepository.findAllByOrderByNombreCompletoAsc().stream()
                .map(u -> new UsuarioFila(u.getId(), u.getNombreCompleto(), u.getCorreo(),
                        cargoPorUsuarioId.get(u.getId()),
                        u.getRolOrganizacional(), u.getEstado(), u.getFechaCreacion()))
                .collect(Collectors.toList());
    }

    public Map<String, Long> kpis() {
        return Map.of(
                "total", usuarioRepository.count(),
                "sinRol", usuarioRepository.countByRolOrganizacionalIsNull(),
                "suspendidos", usuarioRepository.countByEstado("inactivo"),
                "administradores", usuarioRepository.countByRolOrganizacional(Roles.ADMINISTRADOR)
        );
    }

    @Transactional
    public void invitar(String correoCrudo, Long actorId) {
        if (correoCrudo == null || correoCrudo.isBlank()) {
            throw new OperacionInvalidaException("Ingresa un correo corporativo.");
        }
        String correo = correoCrudo.trim().toLowerCase();
        String dominioPermitido = configuracionService.valor(configuracionService.obtenerMapa(), "dominio_correo_permitido", "nexacorp.com").trim();
        boolean dominioLibre = dominioPermitido.isEmpty() || dominioPermitido.equals("*");
        if (!patronCorreoDominio().matcher(correo).matches()) {
            throw new OperacionInvalidaException(dominioLibre ? "Ingresa un correo válido." : "Ingresa un correo corporativo válido (@" + dominioPermitido + ").");
        }
        if (usuarioRepository.existsByCorreoIgnoreCase(correo)) {
            throw new OperacionInvalidaException("Ese correo ya tiene una cuenta creada.");
        }
        if (correoAutorizadoRepository.existsByCorreoIgnoreCase(correo)) {
            throw new OperacionInvalidaException("Ese correo ya fue invitado anteriormente.");
        }
        CorreoAutorizado acceso = new CorreoAutorizado();
        acceso.setCorreo(correo);
        acceso.setAutorizadoPorId(actorId);
        acceso.setOrigenCarga("individual");
        acceso.setUtilizado(false);
        correoAutorizadoRepository.save(acceso);

        auditoriaService.registrar(actorId, "USUARIO_INVITADO", "correo_autorizado", null,
                null, AuditoriaService.json("correo", correo),
                "Se autorizó el correo " + correo + " para autoregistro.");

        // Correo real al invitado: todavía no existe su perfil (recién se
        // crea al completar /auth/registro.html), así que se manda directo
        // con EmailService en vez de pasar por NotificacionService (que
        // necesita un perfilId ya existente).
        String linkInicio = (baseUrl != null && !baseUrl.isBlank())
                ? baseUrl + "/auth/login.html"
                : "/auth/login.html";
        emailService.enviarNotificacion(correo, "Fuiste invitado a SkillBridge AI",
                "¡Ya puedes registrarte en SkillBridge AI!",
                "Un administrador autorizó tu correo (" + correo + ") para crear tu cuenta. "
                        + "Ingresa a la página de inicio de SkillBridge AI y haz clic en \"Crear cuenta\" para completar tu registro.",
                linkInicio, "Ir a SkillBridge AI");
    }

    // ─────────────── CRUD de Correos autorizados (lista blanca de registro) ───────────────

    private static final java.time.format.DateTimeFormatter FORMATO_FECHA_AUTORIZACION =
            java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public List<com.skillbridge.ai.dto.CorreoAutorizadoFila> listarAutorizados() {
        return correoAutorizadoRepository.findAllByOrderByFechaAutorizacionDesc().stream()
                .map(c -> new com.skillbridge.ai.dto.CorreoAutorizadoFila(
                        c.getId(), c.getCorreo(),
                        c.getFechaAutorizacion() != null ? c.getFechaAutorizacion().format(FORMATO_FECHA_AUTORIZACION) : "—",
                        Boolean.TRUE.equals(c.getUtilizado())))
                .collect(Collectors.toList());
    }

    /**
     * Revoca una autorización que todavía no fue usada (nadie se registró
     * con ese correo). Si ya se usó, ya existe la cuenta y esto no debe
     * borrar el rastro de auditoría de cómo se creó - se elimina el usuario
     * desde "Usuarios y roles" en su lugar.
     */
    @Transactional
    public void revocarAutorizacion(Long id, Long actorId) {
        CorreoAutorizado acceso = correoAutorizadoRepository.findById(id)
                .orElseThrow(() -> new OperacionInvalidaException("Esa autorización ya no existe."));
        if (Boolean.TRUE.equals(acceso.getUtilizado())) {
            throw new OperacionInvalidaException("Ese correo ya se registró; no se puede revocar. Elimina la cuenta desde la lista de usuarios si corresponde.");
        }
        String correo = acceso.getCorreo();
        correoAutorizadoRepository.delete(acceso);
        auditoriaService.registrar(actorId, "USUARIO_INVITACION_REVOCADA", "correo_autorizado", id, null, null,
                "Se revocó la autorización de " + correo + ".");
    }

    @Transactional
    public void cambiarRol(Long usuarioId, String nuevoRolOrganizacional, Long actorId) {
        if (usuarioId.equals(actorId)) {
            throw new OperacionInvalidaException("No puedes cambiar tu propio rol.");
        }
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new OperacionInvalidaException("El usuario ya no existe."));

        String normalizado = (nuevoRolOrganizacional == null || nuevoRolOrganizacional.isBlank()
                || "NINGUNO".equalsIgnoreCase(nuevoRolOrganizacional)) ? null : nuevoRolOrganizacional;
        if (normalizado != null && !normalizado.equals(Roles.ADMINISTRADOR) && !normalizado.equals(Roles.RESOURCE_MANAGER)) {
            throw new OperacionInvalidaException("Rol no reconocido.");
        }

        String anterior = usuario.getRolOrganizacional();
        usuario.setRolOrganizacional(normalizado);
        usuarioRepository.save(usuario);

        auditoriaService.registrar(actorId, "USUARIO_ROL_CAMBIADO", "usuario", usuarioId,
                AuditoriaService.json("rol_organizacional", anterior),
                AuditoriaService.json("rol_organizacional", normalizado),
                "Rol de " + usuario.getCorreo() + " actualizado.");
    }

    @Transactional
    public void cambiarEstado(Long usuarioId, boolean activar, Long actorId) {
        if (usuarioId.equals(actorId)) {
            throw new OperacionInvalidaException("No puedes suspender o reactivar tu propia cuenta.");
        }
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new OperacionInvalidaException("El usuario ya no existe."));

        String anterior = usuario.getEstado();
        String nuevo = activar ? "activo" : "inactivo";
        usuario.setEstado(nuevo);
        usuarioRepository.save(usuario);

        auditoriaService.registrar(actorId, activar ? "USUARIO_REACTIVADO" : "USUARIO_SUSPENDIDO",
                "usuario", usuarioId, AuditoriaService.json("estado", anterior), AuditoriaService.json("estado", nuevo),
                usuario.getCorreo());
    }

    @Transactional
    public void eliminar(Long usuarioId, Long actorId) {
        if (usuarioId.equals(actorId)) {
            throw new OperacionInvalidaException("No puedes eliminar tu propia cuenta.");
        }
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new OperacionInvalidaException("El usuario ya no existe."));
        String correo = usuario.getCorreo();
        try {
            usuarioRepository.delete(usuario);
            usuarioRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new OperacionInvalidaException(
                    "No se puede eliminar: " + correo + " tiene asignaciones, publicaciones u otros registros asociados. Suspende la cuenta en su lugar.");
        }
        auditoriaService.registrar(actorId, "USUARIO_ELIMINADO", "usuario", usuarioId, null, null, correo);
    }
}
