package com.skillbridge.ai.service;

import com.skillbridge.ai.dto.CuentaResumen;
import com.skillbridge.ai.model.Perfil;
import com.skillbridge.ai.model.Usuario;
import com.skillbridge.ai.repository.PerfilRepository;
import com.skillbridge.ai.repository.UsuarioRepository;
import com.skillbridge.ai.util.OperacionInvalidaException;
import com.skillbridge.ai.util.PasswordPolicy;
import com.skillbridge.ai.util.Roles;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * "Mi cuenta" (administrador/mi-cuenta.html y colaborador/mi-cuenta.html,
 * ambas paginas comparten un unico controlador y este servicio).
 *
 * El mockup original incluia funcionalidades decorativas que esta
 * iteracion NO implementa por no tener soporte real en el esquema ni
 * haberse pedido explicitamente: activar/desactivar 2FA, una segunda
 * "sesion activa" fabricada, y un aviso por correo de nuevos inicios de
 * sesion. Se deja solo lo que mapea a datos y operaciones reales: editar
 * cargo/biografia/anios de experiencia (perfiles), y cambiar la
 * contrasena real (usuarios.contrasena_hash), reutilizando el mismo
 * BCrypt + PasswordPolicy de AuthService/registro - declarado en el
 * README.
 */
@Service
public class CuentaService {

    private static final DateTimeFormatter FORMATO_MES_ANIO =
            DateTimeFormatter.ofPattern("MMMM yyyy", new Locale("es", "ES"));

    private final PerfilRepository perfilRepository;
    private final UsuarioRepository usuarioRepository;
    private final AuthService authService;
    private final AuditoriaService auditoriaService;

    public CuentaService(PerfilRepository perfilRepository, UsuarioRepository usuarioRepository,
                          AuthService authService, AuditoriaService auditoriaService) {
        this.perfilRepository = perfilRepository;
        this.usuarioRepository = usuarioRepository;
        this.authService = authService;
        this.auditoriaService = auditoriaService;
    }

    /** rolEfectivo se recibe de la sesión (UsuarioSesion.getRolEfectivo()): ya se calculó en el login, no hace falta recalcularlo aquí. */
    public CuentaResumen obtenerResumen(Long perfilId, String rolEfectivo) {
        Perfil perfil = perfilRepository.buscarConUsuario(perfilId)
                .orElseThrow(() -> new OperacionInvalidaException("Tu perfil ya no existe."));
        Usuario usuario = perfil.getUsuario();
        String cuentaDesde = usuario.getFechaCreacion() != null
                ? usuario.getFechaCreacion().format(FORMATO_MES_ANIO)
                : "";
        return new CuentaResumen(
                usuario.getNombreCompleto(),
                usuario.getCorreo(),
                perfil.getCargo(),
                perfil.getBiografia(),
                perfil.getExperienciaAnios() != null ? perfil.getExperienciaAnios() : 0,
                perfil.getDisponibilidadPorcentaje() != null ? perfil.getDisponibilidadPorcentaje() : 0,
                Roles.etiqueta(rolEfectivo),
                cuentaDesde);
    }

    @Transactional
    public void actualizarDatos(Long perfilId, String cargo, String biografia, int experienciaAnios, Long actorUsuarioId) {
        Perfil perfil = perfilRepository.findById(perfilId)
                .orElseThrow(() -> new OperacionInvalidaException("Tu perfil ya no existe."));
        if (experienciaAnios < 0 || experienciaAnios > 60) {
            throw new OperacionInvalidaException("Los años de experiencia deben ser un valor entre 0 y 60.");
        }
        String cargoAnterior = perfil.getCargo();
        perfil.setCargo(cargo != null ? cargo.trim() : null);
        perfil.setBiografia(biografia != null ? biografia.trim() : null);
        perfil.setExperienciaAnios(experienciaAnios);
        perfilRepository.save(perfil);

        auditoriaService.registrar(actorUsuarioId, "CUENTA_ACTUALIZADA", "perfil", perfilId,
                AuditoriaService.json("cargo", cargoAnterior), AuditoriaService.json("cargo", perfil.getCargo()),
                "El usuario actualizó los datos de su cuenta.");
    }

    @Transactional
    public void cambiarContrasena(Long usuarioId, String actual, String nueva, String nueva2, Long actorUsuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new OperacionInvalidaException("Tu usuario ya no existe."));
        BCryptPasswordEncoder encoder = authService.passwordEncoder();

        if (actual == null || actual.isEmpty() || !encoder.matches(actual, usuario.getContrasenaHash())) {
            throw new OperacionInvalidaException("Tu contraseña actual no es correcta.");
        }
        String errorPwd = PasswordPolicy.validar(nueva);
        if (errorPwd != null) {
            throw new OperacionInvalidaException(errorPwd);
        }
        if (nueva2 == null || !nueva.equals(nueva2)) {
            throw new OperacionInvalidaException("Las contraseñas nuevas no coinciden.");
        }
        if (encoder.matches(nueva, usuario.getContrasenaHash())) {
            throw new OperacionInvalidaException("La nueva contraseña debe ser distinta de la actual.");
        }

        usuario.setContrasenaHash(encoder.encode(nueva));
        usuarioRepository.save(usuario);

        auditoriaService.registrar(actorUsuarioId, "CONTRASENA_CAMBIADA", "usuario", usuarioId,
                null, null, "El usuario cambió su propia contraseña desde Mi cuenta.");
    }
}
