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
import com.skillbridge.ai.repository.UsuarioRepository;
import com.skillbridge.ai.util.PasswordPolicy;
import com.skillbridge.ai.util.Roles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
 *    de la referencia; el propio seed de skillbridge_db_v4.sql ya trae un
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

    private static final Pattern PATRON_NOMBRE = Pattern.compile("^[A-Za-zÁÉÍÓÚáéíóúÑñÜü ]{2,50}$");

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

    private final ConcurrentHashMap<String, int[]> intentosFallidos = new ConcurrentHashMap<>();

    public AuthService(UsuarioRepository usuarioRepository,
                        PerfilRepository perfilRepository,
                        CorreoAutorizadoRepository correoAutorizadoRepository,
                        HabilidadRepository habilidadRepository,
                        PerfilHabilidadRepository perfilHabilidadRepository,
                        AuditoriaService auditoriaService,
                        ConfiguracionService configuracionService,
                        NotificacionService notificacionService,
                        JdbcTemplate jdbcTemplate,
                        PasswordEncoder passwordEncoder) {
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

    public PasswordEncoder passwordEncoder() {
        return passwordEncoder;
    }

    // ───────────────────────── Login ─────────────────────────

    public boolean estaBloqueada(String ip) {
        int[] datos = intentosFallidos.get(ip);
        if (datos == null) return false;
        long ahora = System.currentTimeMillis();
        if (ahora - datos[1] > VENTANA_MS) {
            intentosFallidos.remove(ip);
            return false;
        }
        return datos[0] >= MAX_INTENTOS;
    }

    private void registrarIntentoFallido(String ip) {
        long ahora = System.currentTimeMillis();
        intentosFallidos.compute(ip, (k, v) -> {
            if (v == null || ahora - v[1] > VENTANA_MS) {
                return new int[]{1, (int) (ahora / 1000) * 1000};
            }
            v[0]++;
            return v;
        });
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

        if (!usuario.isActivo()) {
            auditoriaService.registrar(usuario.getId(), "LOGIN_BLOQUEADO", "Cuenta inactiva (ip=" + ip + ")");
            return ResultadoLogin.error("Tu cuenta está inactiva. Contacta a un administrador.");
        }

        limpiarIntentos(ip);

        Optional<Perfil> operfil = perfilRepository.findByUsuarioId(usuario.getId());
        Long perfilId = operfil.map(Perfil::getId).orElse(null);
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
     * ver comentario de la tabla asignaciones en skillbridge_db_v4.sql):
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
    public ResultadoRegistro registrar(RegistroRequest req) {
        Map<String, String> errores = new LinkedHashMap<>();

        String nombres = limpiar(req.nombres());
        String apellidos = limpiar(req.apellidos());
        String correo = limpiar(req.correo());
        String correoFinal = correo != null ? correo.toLowerCase() : null;

        if (nombres == null || nombres.isEmpty()) {
            errores.put("nombres", "Escribe tus nombres.");
        } else if (!PATRON_NOMBRE.matcher(nombres).matches()) {
            errores.put("nombres", "Solo letras y espacios (2-50 caracteres).");
        }

        if (apellidos == null || apellidos.isEmpty()) {
            errores.put("apellidos", "Escribe tus apellidos.");
        } else if (!PATRON_NOMBRE.matcher(apellidos).matches()) {
            errores.put("apellidos", "Solo letras y espacios (2-50 caracteres).");
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
        Optional<CorreoAutorizado> oAcceso = correoAutorizadoRepository.findByCorreoIgnoreCase(correoFinal);
        if (oAcceso.isEmpty() || Boolean.TRUE.equals(oAcceso.get().getUtilizado())) {
            errores.put("correo", "Este correo no está autorizado. Usa el correo al que te llegó la invitación, o pide a un Administrador que lo habilite.");
            return ResultadoRegistro.conErrores(errores);
        }

        if (usuarioRepository.existsByCorreoIgnoreCase(correoFinal)) {
            errores.put("_global", "Ya existe una cuenta registrada con ese correo.");
            return ResultadoRegistro.conErrores(errores);
        }

        // ── Crear usuario + perfil ──
        Usuario usuario = new Usuario();
        usuario.setCorreo(correoFinal);
        usuario.setContrasenaHash(passwordEncoder.encode(contrasena));
        usuario.setNombreCompleto(nombres + " " + apellidos);
        // Modelo v7 (fase 3): la cuenta nace con el rol con el que se la
        // invitó (null = Miembro). Así el primer inicio de sesión ya abre el
        // panel correcto, sin pasos manuales del Administrador.
        String rolInvitado = oAcceso.get().getRolAsignado();
        usuario.setRolOrganizacional(Roles.ADMINISTRADOR.equals(rolInvitado) || Roles.RESOURCE_MANAGER.equals(rolInvitado)
                ? rolInvitado : null);
        usuario.setEstado("activo");
        usuario = usuarioRepository.save(usuario);

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
                "Tu cuenta se creó correctamente con el correo " + correoFinal + ". Ya puedes iniciar sesión y explorar tus proyectos.", null);

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
}
