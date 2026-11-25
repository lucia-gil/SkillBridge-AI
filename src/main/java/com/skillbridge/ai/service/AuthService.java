package com.skillbridge.ai.service;

import com.skillbridge.ai.dto.*;
import com.skillbridge.ai.model.CorreoAutorizado;
import com.skillbridge.ai.model.Habilidad;
import com.skillbridge.ai.model.Perfil;
import com.skillbridge.ai.model.PerfilHabilidad;
import com.skillbridge.ai.model.Usuario;
import com.skillbridge.ai.repository.CorreoAutorizadoRepository;
import com.skillbridge.ai.repository.HabilidadRepository;
import com.skillbridge.ai.repository.PerfilHabilidadRepository;
import com.skillbridge.ai.repository.PerfilRepository;
import com.skillbridge.ai.repository.TokenRecuperacionPasswordRepository;
import com.skillbridge.ai.repository.UsuarioRepository;
import com.skillbridge.ai.model.TokenRecuperacionPassword;
import com.skillbridge.ai.util.PasswordPolicy;
import com.skillbridge.ai.util.Roles;
import com.skillbridge.ai.util.OperacionInvalidaException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Autenticacion y registro de SkillBridge AI.
 *
 * Funcionalidad adaptada de LoginServlet/RegistroServlet/PasswordPolicy de
 * QuintaOla-SGA a Spring Boot:
 *  - rate limiting de login por IP (5 intentos / 10 min),
 *  - mensaje de error generico en login (no revela si el correo existe),
 *  - hash de contrasena con BCrypt (mas fuerte que el SHA2(_,256) sin sal
 *    de la referencia; el propio seed de skillbridge_db.sql ya trae un
 *    hash BCrypt para el admin, asi que es el algoritmo consistente con el
 *    esquema entregado),
 *  - registro solo permitido para correos previamente autorizados
 *    (correos_autorizados, RF01/RF02 - la referencia de QuintaOla usa el
 *    mismo patron de "acceso pre-autorizado" con AccesoRegistroDAO),
 *  - validacion de todos los campos acumulando errores por campo,
 *  - auditoria de login/registro en auditoria_logs.
 */
@Service
public class AuthService {

    private static final int MAX_INTENTOS = 5;
    private static final long VENTANA_MS = 10 * 60 * 1000L;

    private final UsuarioRepository usuarioRepository;
    private final PerfilRepository perfilRepository;
    private final CorreoAutorizadoRepository correoAutorizadoRepository;
    private final HabilidadRepository habilidadRepository;
    private final PerfilHabilidadRepository perfilHabilidadRepository;
    private final AuditoriaService auditoriaService;
    private final ConfiguracionService configuracionService;
    private final NotificacionService notificacionService;
    private final JdbcTemplate jdbcTemplate;
    /** Bean BCrypt de SecurityConfig (Clase 6.1). */
    private final PasswordEncoder passwordEncoder;
    private final TokenRecuperacionPasswordRepository tokenRecuperacionRepository;
    private final EmailService emailService;
    private final ConsultaPeruDniService consultaDni;
    private final IdentidadDniService identidadDni;

    @Value("${app.base-url:}")
    private String baseUrl;

    /** Contraseña de las cuentas de ejemplo de db/skillbridge_db.sql (publicada en el README). */
    static final String CLAVE_DEMO = "admin123";

    /** En producción (application-prod.properties) no se puede entrar con la contraseña de ejemplo. */
    @Value("${app.seguridad.bloquear-clave-demo:false}")
    private boolean bloquearClaveDemo;

    /** Cierra las sesiones abiertas al restablecer la contraseña (null en tests unitarios). */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.skillbridge.ai.security.CierreSesiones cierreSesiones;

    /** Vigencia de un enlace de "olvide mi contraseña" (ver recuperar-password.html). */
    private static final long RECUPERACION_MINUTOS = 30;

    private final ConcurrentHashMap<String, Intentos> intentosFallidos = new ConcurrentHashMap<>();

    /** Fallos de una IP y cuándo empezó su ventana (epoch en ms, siempre en long). */
    private record Intentos(int cantidad, long inicio) {}

    public AuthService(UsuarioRepository usuarioRepository,
                        PerfilRepository perfilRepository,
                        CorreoAutorizadoRepository correoAutorizadoRepository,
                        HabilidadRepository habilidadRepository,
                        PerfilHabilidadRepository perfilHabilidadRepository,
                        AuditoriaService auditoriaService,
                        ConfiguracionService configuracionService,
                        NotificacionService notificacionService,
                        JdbcTemplate jdbcTemplate,
                        PasswordEncoder passwordEncoder,
                        TokenRecuperacionPasswordRepository tokenRecuperacionRepository,
                        EmailService emailService,
                        ConsultaPeruDniService consultaDni, IdentidadDniService identidadDni) {
        this.passwordEncoder = passwordEncoder;
        this.usuarioRepository = usuarioRepository;
        this.perfilRepository = perfilRepository;
        this.correoAutorizadoRepository = correoAutorizadoRepository;
        this.habilidadRepository = habilidadRepository;
        this.perfilHabilidadRepository = perfilHabilidadRepository;
        this.auditoriaService = auditoriaService;
        this.configuracionService = configuracionService;
        this.notificacionService = notificacionService;
        this.jdbcTemplate = jdbcTemplate;
        this.tokenRecuperacionRepository = tokenRecuperacionRepository;
        this.emailService = emailService;
        this.consultaDni = consultaDni;
        this.identidadDni = identidadDni;
    }

    /**
     * Formato de correo aceptado en el registro. Antes el registro además
     * exigía el dominio de configuracion_global.dominio_correo_permitido;
     * ahora el ÚNICO filtro es la lista blanca correos_autorizados: puede
     * registrarse exactamente el correo que un Administrador autorizó (al
     * que le llegó la invitación), sea del dominio que sea. El dominio
     * sigue aplicando, si se configura, al momento de INVITAR
     * (UsuarioService.invitar).
     */
    private static final Pattern PATRON_CORREO_GENERICO = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$", Pattern.CASE_INSENSITIVE);

    /** Estados que devuelve estadoCorreoRegistro (los usa la verificación en vivo de registro.html). */
    public static final String CORREO_FORMATO_INVALIDO = "formato";
    public static final String CORREO_AUTORIZADO = "autorizado";
    public static final String CORREO_NO_AUTORIZADO = "no_autorizado";
    public static final String CORREO_YA_REGISTRADO = "registrado";
    public static final String CORREO_REQUIERE_ENLACE = "requiere_enlace";
    public static final String ENLACE_REQUERIDO = "Abre el enlace vigente que recibiste en tu correo de invitación. Si venció, solicita el reenvío.";

    /**
     * Estado de un correo para el registro, consultando la BD:
     *  - "registrado"    ya existe una cuenta con ese correo;
     *  - "autorizado"    está en correos_autorizados y la invitación no se usó;
     *  - "no_autorizado" no está en la lista, o la invitación ya se usó;
     *  - "formato"       no tiene formato de correo.
     */
    @Transactional(readOnly = true)
    public String estadoCorreoRegistro(String correoCrudo) {
        String correo = correoCrudo == null ? "" : correoCrudo.trim().toLowerCase();
        if (correo.length() > 150 || !PATRON_CORREO_GENERICO.matcher(correo).matches()) {
            return CORREO_FORMATO_INVALIDO;
        }
        if (usuarioRepository.existsByCorreoIgnoreCase(correo)) {
            return CORREO_YA_REGISTRADO;
        }
        return correoAutorizadoRepository.findByCorreoIgnoreCase(correo)
                .filter(c -> !Boolean.TRUE.equals(c.getUtilizado()))
                .map(c -> CORREO_AUTORIZADO)
                .orElse(CORREO_NO_AUTORIZADO);
    }

    @Transactional(readOnly = true)
    public Optional<Long> invitacionParaConsultarDni(String correo, String firma) {
        if (!CORREO_AUTORIZADO.equals(estadoCorreoRegistro(correo))) return Optional.empty();
        return correoAutorizadoRepository.findByCorreoIgnoreCase(correo.strip())
                .filter(invitacion -> identidadDni.firmaValida(invitacion, firma))
                .map(CorreoAutorizado::getId);
    }

    public PasswordEncoder passwordEncoder() {
        return passwordEncoder;
    }

    // ───────────────────────── Login ─────────────────────────

    public boolean estaBloqueada(String ip) {
        Intentos datos = intentosFallidos.get(ip);
        if (datos == null) return false;
        long ahora = System.currentTimeMillis();
        if (ahora - datos.inicio() > VENTANA_MS) {
            intentosFallidos.remove(ip, datos);
            return false;
        }
        return datos.cantidad() >= MAX_INTENTOS;
    }

    private void registrarIntentoFallido(String ip) {
        long ahora = System.currentTimeMillis();
        intentosFallidos.compute(ip, (k, v) -> v == null || ahora - v.inicio() > VENTANA_MS
                ? new Intentos(1, ahora)
                : new Intentos(v.cantidad() + 1, v.inicio()));
    }

    private void limpiarIntentos(String ip) {
        intentosFallidos.remove(ip);
    }

    @Transactional
    public ResultadoLogin intentarLogin(String correoCrudo, String contrasena, String ip) {
        if (estaBloqueada(ip)) {
            return ResultadoLogin.error("Demasiados intentos fallidos. Intenta de nuevo en 10 minutos.");
        }

        if (correoCrudo == null || contrasena == null || correoCrudo.isBlank() || contrasena.isEmpty()) {
            registrarIntentoFallido(ip);
            auditoriaService.registrar(null, "LOGIN_FALLIDO", "Intento con campos vacíos (ip=" + ip + ")");
            return ResultadoLogin.error("Credenciales incorrectas.");
        }

        String correo = correoCrudo.trim().toLowerCase();
        Optional<Usuario> ou = usuarioRepository.findByCorreoIgnoreCase(correo);

        if (ou.isEmpty() || !passwordEncoder.matches(contrasena, ou.get().getContrasenaHash())) {
            registrarIntentoFallido(ip);
            auditoriaService.registrar(null, "LOGIN_FALLIDO",
                    "Intento fallido para: " + correo + " (ip=" + ip + ")");
            return ResultadoLogin.error("Credenciales incorrectas.");
        }

        Usuario usuario = ou.get();

        if (bloquearClaveDemo && CLAVE_DEMO.equals(contrasena)) {
            registrarIntentoFallido(ip);
            auditoriaService.registrar(usuario.getId(), "LOGIN_BLOQUEADO",
                    "Contraseña de ejemplo deshabilitada en producción (ip=" + ip + ")");
            return ResultadoLogin.error("Esta cuenta todavía usa la contraseña de ejemplo, que no está permitida en producción. "
                    + "Usa «¿Olvidaste tu contraseña?» para crear una nueva.");
        }

        if (!usuario.isActivo()) {
            auditoriaService.registrar(usuario.getId(), "LOGIN_BLOQUEADO", "Cuenta inactiva (ip=" + ip + ")");
            return ResultadoLogin.error("Tu cuenta está inactiva. Contacta a un administrador.");
        }

        limpiarIntentos(ip);

        Optional<Perfil> operfil = perfilRepository.findByUsuarioId(usuario.getId());
        Long perfilId = operfil.map(Perfil::getId).orElse(null);
        if (perfilId == null && Roles.ADMINISTRADOR.equals(usuario.getRolOrganizacional())) {
            // Compatibilidad con administradores cargados a mano o desde un seed antiguo.
            perfilRepository.asegurarPerfilAdministrador(usuario.getId());
            perfilId = perfilRepository.findByUsuarioId(usuario.getId()).map(Perfil::getId).orElseThrow();
        }
        String rolEfectivo = calcularRolEfectivo(perfilId, usuario.getRolOrganizacional());

        UsuarioSesion sesion = new UsuarioSesion();
        sesion.setUsuarioId(usuario.getId());
        sesion.setPerfilId(perfilId);
        sesion.setCorreo(usuario.getCorreo());
        sesion.setNombreCompleto(usuario.getNombreCompleto());
        sesion.setRolOrganizacional(usuario.getRolOrganizacional());
        sesion.setRolEfectivo(rolEfectivo);
        sesion.setLideraProyectos(Roles.PROJECT_MANAGER.equals(rolEfectivo));
        sesion.setIniciales(iniciales(usuario.getNombreCompleto()));

        auditoriaService.registrar(usuario.getId(), "LOGIN_EXITOSO",
                "Login exitoso. Rol efectivo: " + rolEfectivo + " (ip=" + ip + ")");

        return ResultadoLogin.exito(sesion);
    }

    /**
     * Vista INICIAL al iniciar sesión (modelo v7). Un Miembro que lidera algún
     * proyecto entra a la vista Project Manager y puede pasar a la de
     * Colaborador con el selector "Ver como" (ver SesionInterceptor).
     *
     * Rol efectivo para navegacion (no confundir con usuarios.rol_organizacional,
     * ver comentario de la tabla asignaciones en skillbridge_db.sql):
     * administrador/resource_manager son puestos fijos; si no aplica ninguno,
     * se revisa si el perfil tiene una asignacion ACTIVA como project_manager
     * en algun proyecto; si tampoco, es colaborador.
     */
    public String calcularRolEfectivo(Long perfilId, String rolOrganizacional) {
        if (Roles.ADMINISTRADOR.equals(rolOrganizacional)) return Roles.ADMINISTRADOR;
        if (Roles.RESOURCE_MANAGER.equals(rolOrganizacional)) return Roles.RESOURCE_MANAGER;
        if (perfilId != null && tieneAsignacionActivaComoPM(perfilId)) return Roles.PROJECT_MANAGER;
        return Roles.COLABORADOR;
    }

    /**
     * ¿Este perfil lidera hoy algún proyecto (fila activa como
     * project_manager)? Decide si un Miembro puede entrar a la vista
     * Project Manager además de la de Colaborador (modelo v7).
     */
    public boolean lideraProyectos(Long perfilId) {
        return perfilId != null && tieneAsignacionActivaComoPM(perfilId);
    }

    private boolean tieneAsignacionActivaComoPM(Long perfilId) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM asignaciones WHERE perfil_id = ? AND rol_en_proyecto = 'project_manager' AND estado = 'activa'",
                    Integer.class, perfilId);
            return count != null && count > 0;
        } catch (Exception ex) {
            // Si la tabla asignaciones aun no tiene datos o hay un problema de
            // conexion puntual, no se debe romper el login por esto: se asume
            // colaborador (el caso mas restrictivo) y se sigue.
            return false;
        }
    }

    private String iniciales(String nombreCompleto) {
        if (nombreCompleto == null || nombreCompleto.isBlank()) return "??";
        String[] partes = nombreCompleto.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < partes.length && sb.length() < 2; i++) {
            if (!partes[i].isEmpty()) sb.append(Character.toUpperCase(partes[i].charAt(0)));
        }
        return sb.length() > 0 ? sb.toString() : "??";
    }

    // ───────────────────────── Registro ─────────────────────────

    @Transactional
    public ResultadoRegistro registrar(RegistroRequest req, String firma) {
        Map<String, String> errores = new LinkedHashMap<>();

        String correo = limpiar(req.correo());
        String correoFinal = correo != null ? correo.toLowerCase() : null;

        if (!consultaDni.formatoValido(req.dni())) {
            errores.put("dni", "Escribe un DNI de 8 dígitos.");
        }

        // Sin filtro de dominio: vale el correo exacto que autorizó el Administrador
        // (se verifica más abajo contra correos_autorizados).
        if (correo == null || correo.isEmpty()) {
            errores.put("correo", "Escribe el correo al que te llegó la invitación.");
        } else if (!PATRON_CORREO_GENERICO.matcher(correo).matches()) {
            errores.put("correo", "Ingresa un correo válido.");
        }

        String contrasena = req.contrasena();
        if (contrasena == null || contrasena.isEmpty()) {
            errores.put("contrasena", "La contraseña es obligatoria.");
        } else {
            String errorPwd = PasswordPolicy.validar(contrasena);
            if (errorPwd != null) {
                errores.put("contrasena", errorPwd);
            } else if (req.contrasena2() == null || !contrasena.equals(req.contrasena2())) {
                errores.put("contrasena2", "Las contraseñas no coinciden.");
            }
        }

        if (req.habilidades() == null || req.habilidades().isEmpty()) {
            errores.put("habilidades", "Agrega al menos una habilidad para continuar.");
        }

        if (!errores.isEmpty()) {
            return ResultadoRegistro.conErrores(errores);
        }

        // ── Correo pre-autorizado (RF01/RF02) ──
        Optional<CorreoAutorizado> oAcceso = correoAutorizadoRepository.bloquearPorCorreo(correoFinal);
        if (oAcceso.isEmpty() || Boolean.TRUE.equals(oAcceso.get().getUtilizado())) {
            errores.put("correo", "Este correo no está autorizado. Usa el correo al que te llegó la invitación, o pide a un Administrador que lo habilite.");
            return ResultadoRegistro.conErrores(errores);
        }

        if (!identidadDni.firmaValida(oAcceso.get(), firma)) {
            errores.put("correo", ENLACE_REQUERIDO);
            return ResultadoRegistro.conErrores(errores);
        }

        if (usuarioRepository.existsByCorreoIgnoreCase(correoFinal)) {
            errores.put("_global", "Ya existe una cuenta registrada con ese correo.");
            return ResultadoRegistro.conErrores(errores);
        }

        Long invitacionId = oAcceso.get().getId();
        ConsultaPeruDniService.Persona persona;
        try {
            persona = identidadDni.validarRegistro(req.dni(), invitacionId);
        } catch (OperacionInvalidaException ex) {
            errores.put("dni", ex.getMessage());
            return ResultadoRegistro.conErrores(errores);
        }

        // ── Crear usuario + perfil ──
        Usuario usuario = new Usuario();
        usuario.setCorreo(correoFinal);
        usuario.setContrasenaHash(passwordEncoder.encode(contrasena));
        usuario.setNombreCompleto(persona.nombres() + " " + persona.apellidos());
        // Modelo v7 (fase 3): la cuenta nace con el rol con el que se la
        // invitó (null = Miembro). Así el primer inicio de sesión ya abre el
        // panel correcto, sin pasos manuales del Administrador.
        String rolInvitado = oAcceso.get().getRolAsignado();
        usuario.setRolOrganizacional(Roles.ADMINISTRADOR.equals(rolInvitado) || Roles.RESOURCE_MANAGER.equals(rolInvitado)
                ? rolInvitado : null);
        usuario.setEstado("activo");
        usuario = usuarioRepository.saveAndFlush(usuario);
        identidadDni.registrar(req.dni(), invitacionId, usuario.getId());

        Perfil perfil = new Perfil();
        perfil.setUsuarioId(usuario.getId());
        perfil.setCargo(limpiar(req.cargo()));
        perfil.setDisponibilidadPorcentaje(100);
        perfil.setExperienciaAnios(0);
        perfil.setEstado("activo");
        perfil = perfilRepository.save(perfil);

        // ── Habilidades declaradas ──
        List<Habilidad> catalogo = habilidadRepository.findAllConCategoriaOrderByNombre();
        for (HabilidadDeclarada hd : req.habilidades()) {
            if (hd.nombre() == null || hd.nombre().isBlank()) continue;
            Optional<Habilidad> oh = catalogo.stream()
                    .filter(h -> h.getNombre().equalsIgnoreCase(hd.nombre().trim()))
                    .findFirst();
            if (oh.isEmpty()) continue; // habilidad inexistente en el catalogo: se ignora (posible manipulacion del form)
            int nivel = nivelDesdeTexto(hd.nivel());
            PerfilHabilidad ph = new PerfilHabilidad(perfil.getId(), oh.get().getId(), nivel);
            perfilHabilidadRepository.save(ph);
        }

        // ── Marcar correo autorizado como usado ──
        CorreoAutorizado acceso = oAcceso.get();
        acceso.setUtilizado(true);
        acceso.setFechaUso(LocalDateTime.now());
        correoAutorizadoRepository.save(acceso);

        auditoriaService.registrar(usuario.getId(), "REGISTRO_EXITOSO", "usuario", usuario.getId(),
                null, AuditoriaService.json("rol_organizacional", usuario.getRolOrganizacional()),
                "Cuenta creada por autoregistro (correo pre-autorizado) con rol "
                        + (usuario.getRolOrganizacional() == null ? "Miembro" : Roles.etiqueta(usuario.getRolOrganizacional())) + ".");

        // Correo de bienvenida real (además de quedar en la campana de notificaciones):
        // "info" no tiene preferencia configurable en preferencias_notificacion, así que
        // NotificacionService.crear(...) siempre manda el correo para este tipo.
        notificacionService.crear(perfil.getId(), "info", "¡Bienvenido a SkillBridge AI!",
                "Tu cuenta se creó correctamente con el correo " + correoFinal + ". Ya puedes iniciar sesión y explorar tus proyectos.", "inicio.html");

        notificacionService.avisarAdministradores("info", "Nueva cuenta registrada",
                usuario.getNombreCompleto() + " completó el registro con una invitación autorizada.");

        return ResultadoRegistro.exito();
    }

    /**
     * Mapeo de la etiqueta textual del medidor de nivel del wizard (Básico/
     * Intermedio/Avanzado/Experto) a la escala numerica 1-5 de
     * perfil_habilidad.nivel. Supuesto declarado: el documento fuente no fija
     * esta correspondencia exacta; se eligio una que conserva el orden y dista
     * lo mismo entre escalones consecutivos.
     */
    private int nivelDesdeTexto(String nivelTexto) {
        if (nivelTexto == null) return 1;
        return switch (nivelTexto.trim().toLowerCase()) {
            case "básico", "basico" -> 1;
            case "intermedio" -> 3;
            case "avanzado" -> 4;
            case "experto" -> 5;
            default -> 1;
        };
    }

    private String limpiar(String s) {
        return s != null ? s.trim() : null;
    }

    // ───────────────────────── Recuperar contraseña ─────────────────────────

    /**
     * Pide un enlace de "olvide mi contraseña". Si el correo no existe en el
     * sistema, el metodo NO lanza error ni avisa nada distinto - la pantalla
     * siempre dice "si el correo existe, te llegara un enlace", para no
     * revelar a un atacante que correos estan registrados (enumeracion de
     * usuarios). Cualquier token anterior del mismo usuario queda invalidado.
     */
    @Transactional
    public void solicitarRecuperacion(String correoCrudo) {
        String correo = correoCrudo == null ? "" : correoCrudo.trim().toLowerCase();
        if (correo.isBlank()) return;
        Optional<Usuario> ou = usuarioRepository.findByCorreoIgnoreCase(correo);
        if (ou.isEmpty()) return;
        Usuario usuario = ou.get();

        tokenRecuperacionRepository.deleteByUsuarioId(usuario.getId());

        String token = generarToken();
        TokenRecuperacionPassword t = new TokenRecuperacionPassword();
        t.setUsuarioId(usuario.getId());
        t.setToken(token);
        t.setFechaCreacion(LocalDateTime.now());
        t.setFechaExpiracion(LocalDateTime.now().plusMinutes(RECUPERACION_MINUTOS));
        t.setUsado(false);
        tokenRecuperacionRepository.save(t);

        String ruta = "/auth/restablecer-password.html?token=" + token;
        String link = (baseUrl != null && !baseUrl.isBlank()) ? baseUrl + ruta : ruta;
        emailService.enviar(new EmailService.Correo(usuario.getCorreo(), "Restablece tu contraseña de SkillBridge AI",
                "Seguridad de tu cuenta",
                "Restablece tu contraseña",
                "Recibimos una solicitud para restablecer la contraseña de " + usuario.getCorreo() + ". "
                        + "El enlace es válido por " + RECUPERACION_MINUTOS + " minutos y se puede usar una sola vez.\n\n"
                        + "Si no fuiste tú, ignora este correo: tu contraseña actual sigue funcionando.",
                link, "Restablecer contraseña", false).conNombre(usuario.getNombreCompleto()));
    }

    /** true si el token existe, no fue usado y todavia no vencio (para pintar el formulario o el error en restablecer-password.html). */
    public boolean tokenDeRecuperacionValido(String token) {
        if (token == null || token.isBlank()) return false;
        return tokenRecuperacionRepository.findByToken(token)
                .filter(t -> !t.isUsado() && t.getFechaExpiracion().isAfter(LocalDateTime.now()))
                .isPresent();
    }

    /**
     * Aplica la nueva contraseña si el token sigue siendo valido. Devuelve
     * null si todo salio bien, o el mensaje de error listo para mostrar en
     * el formulario si no (igual que el resto de validaciones de este
     * servicio - ver registrar()).
     */
    @Transactional
    public String restablecerContrasena(String token, String nuevaContrasena, String nuevaContrasena2) {
        Optional<TokenRecuperacionPassword> ot = token == null ? Optional.empty() : tokenRecuperacionRepository.findByToken(token);
        if (ot.isEmpty() || ot.get().isUsado() || ot.get().getFechaExpiracion().isBefore(LocalDateTime.now())) {
            return "Este enlace ya no es válido. Solicita uno nuevo desde \"¿Olvidaste tu contraseña?\".";
        }
        if (nuevaContrasena == null || nuevaContrasena.isEmpty()) {
            return "La contraseña es obligatoria.";
        }
        String errorPwd = PasswordPolicy.validar(nuevaContrasena);
        if (errorPwd != null) return errorPwd;
        if (nuevaContrasena2 == null || !nuevaContrasena.equals(nuevaContrasena2)) {
            return "Las contraseñas no coinciden.";
        }

        TokenRecuperacionPassword t = ot.get();
        Optional<Usuario> ou = usuarioRepository.findById(t.getUsuarioId());
        if (ou.isEmpty()) return "El enlace ya no es válido.";
        Usuario usuario = ou.get();
        usuario.setContrasenaHash(passwordEncoder.encode(nuevaContrasena));
        usuarioRepository.save(usuario);

        t.setUsado(true);
        tokenRecuperacionRepository.save(t);

        // Quien tenía la contraseña vieja (o una sesión robada) queda fuera en todos los navegadores.
        int cerradas = cierreSesiones != null ? cierreSesiones.cerrarTodas(usuario.getCorreo()) : 0;

        auditoriaService.registrar(usuario.getId(), "CONTRASENA_RESTABLECIDA", "usuario", usuario.getId(), null, null,
                "Restablecimiento via enlace de \"olvide mi contraseña\" (" + usuario.getCorreo() + ")"
                        + (cerradas > 0 ? "; se cerraron " + cerradas + " sesión(es) abierta(s)" : ""));
        return null;
    }

    private String generarToken() {
        byte[] datos = new byte[32];
        new SecureRandom().nextBytes(datos);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(datos);
    }
}
