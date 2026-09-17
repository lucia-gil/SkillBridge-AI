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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
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
    private final JdbcTemplate jdbcTemplate;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private final ConcurrentHashMap<String, int[]> intentosFallidos = new ConcurrentHashMap<>();

    public AuthService(UsuarioRepository usuarioRepository,
                        PerfilRepository perfilRepository,
                        CorreoAutorizadoRepository correoAutorizadoRepository,
                        HabilidadRepository habilidadRepository,
                        PerfilHabilidadRepository perfilHabilidadRepository,
                        AuditoriaService auditoriaService,
                        ConfiguracionService configuracionService,
                        JdbcTemplate jdbcTemplate) {
        this.usuarioRepository = usuarioRepository;
        this.perfilRepository = perfilRepository;
        this.correoAutorizadoRepository = correoAutorizadoRepository;
        this.habilidadRepository = habilidadRepository;
        this.perfilHabilidadRepository = perfilHabilidadRepository;
        this.auditoriaService = auditoriaService;
        this.configuracionService = configuracionService;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Dominio de correo aceptado en el registro - antes era un Pattern fijo
     * a "nexacorp.com"; ahora lee configuracion_global.dominio_correo_permitido
     * (RF08) para que el campo "Dominio corporativo permitido" del panel de
     * Configuración del Administrador tenga un efecto real en vez de ser
     * decorativo.
     */
    private Pattern patronCorreoDominio() {
        String dominio = configuracionService.valor(configuracionService.obtenerMapa(), "dominio_correo_permitido", "nexacorp.com");
        return Pattern.compile("^[A-Za-z0-9+_.-]+@" + Pattern.quote(dominio.trim()) + "$", Pattern.CASE_INSENSITIVE);
    }

    public BCryptPasswordEncoder passwordEncoder() {
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
        sesion.setIniciales(iniciales(usuario.getNombreCompleto()));

        auditoriaService.registrar(usuario.getId(), "LOGIN_EXITOSO",
                "Login exitoso. Rol efectivo: " + rolEfectivo + " (ip=" + ip + ")");

        return ResultadoLogin.exito(sesion);
    }

    /**
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

        String dominioPermitido = configuracionService.valor(configuracionService.obtenerMapa(), "dominio_correo_permitido", "nexacorp.com");
        if (correo == null || correo.isEmpty()) {
            errores.put("correo", "El correo corporativo es obligatorio.");
        } else if (!patronCorreoDominio().matcher(correo).matches()) {
            errores.put("correo", "Usa un correo @" + dominioPermitido + ".");
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
            errores.put("correo", "Correo no autorizado. Contacta a un administrador para que lo habilite.");
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
        usuario.setRolOrganizacional(null); // sin puesto fijo: lo asigna el Administrador despues
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
                null, null, "Cuenta creada por autoregistro (correo pre-autorizado).");

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
