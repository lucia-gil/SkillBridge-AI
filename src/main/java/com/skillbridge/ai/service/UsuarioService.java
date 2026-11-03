package com.skillbridge.ai.service;

import com.skillbridge.ai.dto.UsuarioFila;
import com.skillbridge.ai.model.CorreoAutorizado;
import com.skillbridge.ai.model.Perfil;
import com.skillbridge.ai.model.Usuario;
import com.skillbridge.ai.model.Asignacion;
import com.skillbridge.ai.repository.AsignacionRepository;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * CRUD real de "Usuarios y roles" (administrador/usuarios.html).
 *
 * "Invitar usuario" NO crea la fila en usuarios directamente: el esquema
 * (correos_autorizados, ver skillbridge_db.sql seccion 2) modela el
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
    private final AsignacionRepository asignacionRepository;
    private final ProyectoService proyectoService;
    private final NotificacionService notificacionService;

    @Value("${app.base-url:}")
    private String baseUrl;

    public UsuarioService(UsuarioRepository usuarioRepository,
                           PerfilRepository perfilRepository,
                           CorreoAutorizadoRepository correoAutorizadoRepository,
                           AuditoriaService auditoriaService,
                           ConfiguracionService configuracionService,
                           EmailService emailService,
                           AsignacionRepository asignacionRepository,
                           ProyectoService proyectoService,
                           NotificacionService notificacionService) {
        this.usuarioRepository = usuarioRepository;
        this.perfilRepository = perfilRepository;
        this.correoAutorizadoRepository = correoAutorizadoRepository;
        this.auditoriaService = auditoriaService;
        this.configuracionService = configuracionService;
        this.emailService = emailService;
        this.asignacionRepository = asignacionRepository;
        this.proyectoService = proyectoService;
        this.notificacionService = notificacionService;
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

    @Transactional(readOnly = true)
    public List<UsuarioFila> listar() {
        Map<Long, Perfil> perfilPorUsuarioId = new HashMap<>();
        for (Perfil p : perfilRepository.findAll()) {
            perfilPorUsuarioId.put(p.getUsuarioId(), p);
        }
        return usuarioRepository.findAllByOrderByNombreCompletoAsc().stream()
                .map(u -> {
                    Perfil perfil = perfilPorUsuarioId.get(u.getId());
                    UsuarioFila fila = new UsuarioFila(u.getId(), u.getNombreCompleto(), u.getCorreo(),
                            perfil != null ? perfil.getCargo() : null,
                            u.getRolOrganizacional(), u.getEstado(), u.getFechaCreacion());
                    if (perfil != null) {
                        Participacion part = participacionDe(perfil.getId());
                        fila.setProyectosQueLidera(part.lidera());
                        fila.setAsignacionesComoColaborador(part.colabora());
                    }
                    return fila;
                })
                .collect(Collectors.toList());
    }

    // ─────────────── Modelo v7 (fase 3): participación en proyectos ───────────────

    /** Lo que una persona hace HOY en proyectos (filas activas de asignaciones). */
    private record Participacion(List<String> lidera, List<String> colabora, List<Asignacion> filasColaborador) {
        boolean vacia() { return lidera.isEmpty() && colabora.isEmpty(); }
    }

    private Participacion participacionDe(Long perfilId) {
        List<String> lidera = new ArrayList<>();
        List<String> colabora = new ArrayList<>();
        List<Asignacion> filasColaborador = new ArrayList<>();
        if (perfilId != null) {
            for (Asignacion a : asignacionRepository.listarPorPerfilYEstado(perfilId, "activa")) {
                String proyecto = a.getProyecto() != null ? a.getProyecto().getNombre() : "Proyecto";
                if (Roles.PROJECT_MANAGER.equals(a.getRolEnProyecto())) {
                    lidera.add(proyecto);
                } else {
                    colabora.add(proyecto + " (" + (a.getCargaPorcentaje() != null ? a.getCargaPorcentaje() : 0) + "%)");
                    filasColaborador.add(a);
                }
            }
        }
        return new Participacion(lidera, colabora, filasColaborador);
    }

    /**
     * Regla común para dejar de participar en proyectos (cambio a rol fijo o
     * suspensión), modelo v7:
     *  - si LIDERA algún proyecto -> bloqueado: primero "Cambiar PM";
     *  - si COLABORA en alguno -> exige confirmación explícita y, al
     *    confirmar, finaliza esas asignaciones con el motivo (Auditoría).
     * Pública porque también la usa el Resource Manager al suspender un perfil.
     */
    @Transactional
    public void salirDeProyectos(Long perfilId, String nombre, String accion, boolean confirmarSalida,
                                 String motivo, Long actorId) {
        Participacion part = participacionDe(perfilId);
        if (!part.lidera().isEmpty()) {
            throw new OperacionInvalidaException(nombre + " lidera " + part.lidera().size() + " proyecto(s): "
                    + String.join(", ", part.lidera()) + ". Antes de " + accion
                    + ", el Resource Manager debe usar \"Cambiar PM\" en esos proyectos.");
        }
        if (part.filasColaborador().isEmpty()) return;
        if (!confirmarSalida) {
            throw new OperacionInvalidaException(nombre + " colabora en: " + String.join(", ", part.colabora())
                    + ". Confirma para finalizar esas asignaciones y " + accion + ".");
        }
        for (Asignacion a : part.filasColaborador()) {
            proyectoService.finalizarAsignacion(a.getId(),
                    "Sale del proyecto al " + accion + (AuditoriaService.limpiarMotivo(motivo) != null ? ": " + AuditoriaService.limpiarMotivo(motivo) : ""),
                    actorId);
        }
    }

    /** Nunca puede quedar el sistema sin un Administrador / Resource Manager activo. */
    private void exigirQueQuedeOtroActivo(Usuario usuario, String accion) {
        String rol = usuario.getRolOrganizacional();
        if (rol == null || !usuario.isActivo()) return;
        long activos = usuarioRepository.countByRolOrganizacionalAndEstado(rol, "activo");
        if (activos <= 1) {
            throw new OperacionInvalidaException("No puedes " + accion + ": " + usuario.getNombreCompleto()
                    + " es el único " + (Roles.ADMINISTRADOR.equals(rol) ? "Administrador" : "Resource Manager")
                    + " activo y el sistema no puede quedarse sin uno.");
        }
    }

    private static String etiquetaRol(String rol) {
        if (Roles.ADMINISTRADOR.equals(rol)) return "Administrador";
        if (Roles.RESOURCE_MANAGER.equals(rol)) return "Resource Manager";
        return "Miembro";
    }

    /** "NINGUNO"/vacío -> null (Miembro); valida que sea un rol organizacional real. */
    private static String normalizarRol(String rol) {
        String r = (rol == null || rol.isBlank() || "NINGUNO".equalsIgnoreCase(rol) || "miembro".equalsIgnoreCase(rol)) ? null : rol;
        if (r != null && !r.equals(Roles.ADMINISTRADOR) && !r.equals(Roles.RESOURCE_MANAGER)) {
            throw new OperacionInvalidaException("Rol no reconocido.");
        }
        return r;
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
        invitar(correoCrudo, null, actorId);
    }

    /**
     * Invitar con rol (modelo v7, fase 3): el rol queda guardado en
     * correos_autorizados.rol_asignado y la cuenta nace con él al registrarse,
     * así su primer inicio de sesión ya abre el panel que le corresponde.
     * "Project Manager" no es una opción: es un papel por proyecto.
     */
    @Transactional
    public void invitar(String correoCrudo, String rolCrudo, Long actorId) {
        String rolAsignado = normalizarRol(rolCrudo);
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
        acceso.setRolAsignado(rolAsignado);
        correoAutorizadoRepository.save(acceso);

        auditoriaService.registrar(actorId, "USUARIO_INVITADO", "correo_autorizado", acceso.getId(),
                null, "{\"correo\":\"" + correo + "\",\"rol_organizacional\":" + (rolAsignado == null ? "null" : "\"" + rolAsignado + "\"") + "}",
                "Se autorizó el correo " + correo + " para autoregistro como " + etiquetaRol(rolAsignado) + ".");

        // Correo real al invitado: todavía no existe su perfil (recién se
        // crea al completar /auth/registro.html), así que se manda directo
        // con EmailService en vez de pasar por NotificacionService (que
        // necesita un perfilId ya existente).
        // El link lleva directo al registro con el correo ya escrito: ese
        // correo exacto es el único que puede usar para crear la cuenta.
        String rutaRegistro = "/auth/registro.html?correo="
                + java.net.URLEncoder.encode(correo, java.nio.charset.StandardCharsets.UTF_8);
        String linkRegistro = (baseUrl != null && !baseUrl.isBlank()) ? baseUrl + rutaRegistro : rutaRegistro;
        String comoRol = rolAsignado == null
                ? "como Miembro (podrás liderar o colaborar en proyectos)"
                : "como " + etiquetaRol(rolAsignado);
        emailService.enviarNotificacion(correo, "Fuiste invitado a SkillBridge AI " + (rolAsignado == null ? "" : "como " + etiquetaRol(rolAsignado)),
                "¡Ya puedes registrarte en SkillBridge AI!",
                "Un administrador autorizó tu correo (" + correo + ") para crear tu cuenta " + comoRol + ". "
                        + "Haz clic en el botón para completar tu registro y confirmar tus datos. Usa este mismo correo: es el único autorizado para tu cuenta. "
                        + "Cuando inicies sesión verás directamente tu panel.",
                linkRegistro, "Crear mi cuenta");
    }

    // ─────────────── CRUD de Correos autorizados (lista blanca de registro) ───────────────

    private static final java.time.format.DateTimeFormatter FORMATO_FECHA_AUTORIZACION =
            java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public List<com.skillbridge.ai.dto.CorreoAutorizadoFila> listarAutorizados() {
        return correoAutorizadoRepository.findAllByOrderByFechaAutorizacionDesc().stream()
                .map(c -> new com.skillbridge.ai.dto.CorreoAutorizadoFila(
                        c.getId(), c.getCorreo(),
                        c.getFechaAutorizacion() != null ? c.getFechaAutorizacion().format(FORMATO_FECHA_AUTORIZACION) : "—",
                        Boolean.TRUE.equals(c.getUtilizado()), c.getRolAsignado()))
                .collect(Collectors.toList());
    }

    /**
     * Revoca una autorización que todavía no fue usada (nadie se registró
     * con ese correo). Si ya se usó, ya existe la cuenta y esto no debe
     * borrar el rastro de auditoría de cómo se creó - se elimina el usuario
     * desde "Usuarios y roles" en su lugar.
     */
    /** Cambia el rol de una invitación todavía no usada (modelo v7, fase 3). */
    @Transactional
    public void cambiarRolInvitacion(Long id, String rolCrudo, Long actorId) {
        CorreoAutorizado acceso = correoAutorizadoRepository.findById(id)
                .orElseThrow(() -> new OperacionInvalidaException("Esa invitación ya no existe."));
        if (Boolean.TRUE.equals(acceso.getUtilizado())) {
            throw new OperacionInvalidaException("Ese correo ya se registró: cambia su rol desde la lista de usuarios.");
        }
        String nuevo = normalizarRol(rolCrudo);
        String anterior = acceso.getRolAsignado();
        if (java.util.Objects.equals(anterior, nuevo)) {
            throw new OperacionInvalidaException("La invitación ya tiene ese rol.");
        }
        acceso.setRolAsignado(nuevo);
        correoAutorizadoRepository.save(acceso);
        auditoriaService.registrar(actorId, "INVITACION_ROL_CAMBIADO", "correo_autorizado", id,
                AuditoriaService.json("rol_organizacional", anterior), AuditoriaService.json("rol_organizacional", nuevo),
                "Invitación de " + acceso.getCorreo() + ": " + etiquetaRol(anterior) + " → " + etiquetaRol(nuevo) + ".");
    }

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

    /**
     * Cambio de rol organizacional (solo Administrador), modelo v7:
     *  - nadie cambia su propio rol;
     *  - Miembro -> Admin/RM: no puede seguir en proyectos (Admin y RM nunca
     *    participan): si lidera, bloqueado; si colabora, se finalizan sus
     *    asignaciones al confirmar;
     *  - quitar el rol al único Admin / RM activo: bloqueado.
     * Su sesión abierta toma el rol nuevo en su siguiente clic (SesionInterceptor).
     */
    @Transactional
    public void cambiarRol(Long usuarioId, String nuevoRolOrganizacional, String motivo, boolean confirmarSalida, Long actorId) {
        if (usuarioId.equals(actorId)) {
            throw new OperacionInvalidaException("No puedes cambiar tu propio rol.");
        }
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new OperacionInvalidaException("El usuario ya no existe."));
        String normalizado = normalizarRol(nuevoRolOrganizacional);
        String anterior = usuario.getRolOrganizacional();
        if (java.util.Objects.equals(anterior, normalizado)) {
            throw new OperacionInvalidaException(usuario.getNombreCompleto() + " ya es " + etiquetaRol(normalizado) + ".");
        }
        if (anterior != null) {
            exigirQueQuedeOtroActivo(usuario, "quitarle el rol de " + etiquetaRol(anterior));
        }
        if (anterior == null && normalizado != null) {
            Long perfilId = perfilRepository.findByUsuarioId(usuarioId).map(Perfil::getId).orElse(null);
            salirDeProyectos(perfilId, usuario.getNombreCompleto(), "convertirlo en " + etiquetaRol(normalizado),
                    confirmarSalida, motivo, actorId);
        }

        usuario.setRolOrganizacional(normalizado);
        usuarioRepository.save(usuario);

        auditoriaService.registrar(actorId, "USUARIO_ROL_CAMBIADO", "usuario", usuarioId,
                AuditoriaService.json("rol_organizacional", anterior),
                AuditoriaService.json("rol_organizacional", normalizado),
                AuditoriaService.conMotivo("Rol organizacional de " + usuario.getCorreo() + ": "
                        + etiquetaRol(anterior) + " → " + etiquetaRol(normalizado), motivo) + ".");
        perfilRepository.findByUsuarioId(usuarioId).ifPresent(p -> notificacionService.crear(p.getId(), "info",
                "Tu rol en SkillBridge cambió",
                "Ahora eres " + etiquetaRol(normalizado) + ". Tu panel se actualizará en tu próximo clic.", null));
    }

    @Transactional
    public void cambiarEstado(Long usuarioId, boolean activar, String motivo, Long actorId) {
        cambiarEstado(usuarioId, activar, motivo, false, actorId);
    }

    /**
     * Suspender / reactivar (modelo v7, fase 3). Al suspender: motivo
     * obligatorio; si lidera proyectos, bloqueado; si colabora, se finalizan
     * sus asignaciones al confirmar; nunca el único Admin / RM activo. Se le
     * avisa por correo en ambos sentidos.
     */
    @Transactional
    public void cambiarEstado(Long usuarioId, boolean activar, String motivo, boolean confirmarSalida, Long actorId) {
        if (usuarioId.equals(actorId)) {
            throw new OperacionInvalidaException("No puedes suspender o reactivar tu propia cuenta.");
        }
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new OperacionInvalidaException("El usuario ya no existe."));

        if (!activar && AuditoriaService.limpiarMotivo(motivo) == null) {
            throw new OperacionInvalidaException("Indica el motivo de la suspensión: queda registrado en Auditoría.");
        }
        if (activar == usuario.isActivo()) {
            throw new OperacionInvalidaException(usuario.getNombreCompleto() + (activar ? " ya está activo." : " ya está suspendido."));
        }
        if (!activar) {
            exigirQueQuedeOtroActivo(usuario, "suspender esta cuenta");
            Long perfilId = perfilRepository.findByUsuarioId(usuarioId).map(Perfil::getId).orElse(null);
            salirDeProyectos(perfilId, usuario.getNombreCompleto(), "suspender su cuenta", confirmarSalida, motivo, actorId);
        }
        String anterior = usuario.getEstado();
        String nuevo = activar ? "activo" : "inactivo";
        usuario.setEstado(nuevo);
        usuarioRepository.save(usuario);

        auditoriaService.registrar(actorId, activar ? "USUARIO_REACTIVADO" : "USUARIO_SUSPENDIDO",
                "usuario", usuarioId, AuditoriaService.json("estado", anterior), AuditoriaService.json("estado", nuevo),
                AuditoriaService.conMotivo(usuario.getCorreo(), motivo));
        String motivoLimpio = AuditoriaService.limpiarMotivo(motivo);
        emailService.enviarNotificacion(usuario.getCorreo(),
                activar ? "Tu cuenta de SkillBridge AI fue reactivada" : "Tu cuenta de SkillBridge AI fue suspendida",
                activar ? "Tu cuenta está activa de nuevo" : "Tu cuenta fue suspendida",
                activar ? "Un administrador reactivó tu cuenta. Ya puedes iniciar sesión."
                        : "Un administrador suspendió tu cuenta" + (motivoLimpio != null ? " (motivo: " + motivoLimpio + ")" : "")
                          + ". Si crees que es un error, comunícate con el equipo de administración.");
    }

    @Transactional
    public void eliminar(Long usuarioId, String motivo, Long actorId) {
        if (usuarioId.equals(actorId)) {
            throw new OperacionInvalidaException("No puedes eliminar tu propia cuenta.");
        }
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new OperacionInvalidaException("El usuario ya no existe."));
        if (AuditoriaService.limpiarMotivo(motivo) == null) {
            throw new OperacionInvalidaException("Indica el motivo de la eliminación: queda registrado en Auditoría.");
        }
        String correo = usuario.getCorreo();
        try {
            usuarioRepository.delete(usuario);
            usuarioRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new OperacionInvalidaException(
                    "No se puede eliminar: " + correo + " tiene asignaciones, publicaciones u otros registros asociados. Suspende la cuenta en su lugar.");
        }
        auditoriaService.registrar(actorId, "USUARIO_ELIMINADO", "usuario", usuarioId, null, null,
                AuditoriaService.conMotivo(correo, motivo));
    }
}
