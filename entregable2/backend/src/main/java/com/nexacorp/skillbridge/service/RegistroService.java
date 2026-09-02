package com.nexacorp.skillbridge.service;

import com.nexacorp.skillbridge.model.CorreoAutorizado;
import com.nexacorp.skillbridge.model.EstadoCuenta;
import com.nexacorp.skillbridge.model.Perfil;
import com.nexacorp.skillbridge.model.Usuario;
import com.nexacorp.skillbridge.repository.CorreoAutorizadoRepository;
import com.nexacorp.skillbridge.repository.PerfilRepository;
import com.nexacorp.skillbridge.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Implementa el flujo de registro contra lista blanca (RF01/RF02):
 * 1) el correo debe existir en correos_autorizados con utilizado=false,
 * 2) se crea usuarios + perfiles,
 * 3) se marca el correo autorizado como usado.
 * Todo en una transacción: si algo falla, no queda un correo "gastado" sin
 * usuario creado.
 */
@Service
public class RegistroService {

    private final CorreoAutorizadoRepository correoAutorizadoRepository;
    private final UsuarioRepository usuarioRepository;
    private final PerfilRepository perfilRepository;
    private final PasswordEncoder passwordEncoder;

    public RegistroService(CorreoAutorizadoRepository correoAutorizadoRepository,
                            UsuarioRepository usuarioRepository,
                            PerfilRepository perfilRepository,
                            PasswordEncoder passwordEncoder) {
        this.correoAutorizadoRepository = correoAutorizadoRepository;
        this.usuarioRepository = usuarioRepository;
        this.perfilRepository = perfilRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public static class CorreoNoAutorizadoException extends RuntimeException {
        public CorreoNoAutorizadoException(String correo) {
            super("El correo " + correo + " no está autorizado para registrarse (o ya fue usado)");
        }
    }

    @Transactional
    public Perfil registrar(String correo, String passwordPlano, String nombreCompleto, String cargo) {
        CorreoAutorizado autorizacion = correoAutorizadoRepository
                .findByCorreoAndUtilizadoFalse(correo)
                .orElseThrow(() -> new CorreoNoAutorizadoException(correo));

        Usuario usuario = new Usuario();
        usuario.setCorreo(correo);
        usuario.setContrasenaHash(passwordEncoder.encode(passwordPlano));
        usuario.setEstado(EstadoCuenta.activo);
        usuario = usuarioRepository.save(usuario);

        Perfil perfil = new Perfil();
        perfil.setUsuario(usuario);
        perfil.setNombreCompleto(nombreCompleto);
        perfil.setCargo(cargo);
        perfil = perfilRepository.save(perfil);

        autorizacion.setUtilizado(true);
        autorizacion.setFechaUso(LocalDateTime.now());
        correoAutorizadoRepository.save(autorizacion);

        return perfil;
    }
}
