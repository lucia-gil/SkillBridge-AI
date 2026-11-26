package com.skillbridge.ai.service;

import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.model.Usuario;
import com.skillbridge.ai.repository.UsuarioRepository;
import com.skillbridge.ai.security.IdentidadGoogle;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class GoogleAccessService {

    private static final String SIN_ACCESO =
            "Usa el correo de tu cuenta de SkillBridge. Si aún tienes una invitación, completa primero tu registro.";
    private static final String VINCULO_OCUPADO =
            "Esta cuenta ya tiene un vínculo con Google. Entra con tu contraseña o contacta a un administrador.";

    private final UsuarioRepository usuarios;
    private final AuthService authService;
    private final AuditoriaService auditoria;
    private final JdbcTemplate jdbc;

    public GoogleAccessService(UsuarioRepository usuarios, AuthService authService, AuditoriaService auditoria,
                               JdbcTemplate jdbc) {
        this.usuarios = usuarios;
        this.authService = authService;
        this.auditoria = auditoria;
        this.jdbc = jdbc;
    }

    /** Un resultado vacío pide la contraseña de SkillBridge para el primer vínculo. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Optional<UsuarioSesion> iniciar(IdentidadGoogle google, String ip) {
        Optional<Long> vinculado = usuarioVinculado(google);
        if (vinculado.isPresent()) {
            Usuario usuario = usuarios.findById(vinculado.get()).orElseThrow(() -> new BadCredentialsException(SIN_ACCESO));
            return Optional.of(autorizar(usuario, ip));
        }

        Usuario usuario = usuarios.buscarPorCorreoParaVinculoGoogle(google.correo())
                .filter(u -> google.coincideCon(u.getCorreo()))
                .orElseThrow(() -> new BadCredentialsException(SIN_ACCESO));
        exigirActivo(usuario);
        if (!google.correoGestionado()) {
            return Optional.empty();
        }

        vincular(google, usuario);
        return Optional.of(autorizar(usuario, ip));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void confirmarVinculo(IdentidadGoogle google, Long usuarioId) {
        vincular(google, usuarioParaVincular(google, usuarioId));
    }

    private Usuario usuarioParaVincular(IdentidadGoogle google, Long usuarioId) {
        Usuario usuario = usuarios.buscarParaVinculoGoogle(usuarioId)
                .filter(u -> google.coincideCon(u.getCorreo()))
                .orElseThrow(() -> new BadCredentialsException(SIN_ACCESO));
        exigirActivo(usuario);
        return usuario;
    }

    private Optional<Long> usuarioVinculado(IdentidadGoogle google) {
        List<Long> ids = jdbc.queryForList("SELECT usuario_id FROM usuarios_google WHERE google_sub = ?",
                Long.class, google.sub());
        return ids.stream().findFirst();
    }

    private void vincular(IdentidadGoogle google, Usuario usuario) {
        try {
            jdbc.update("INSERT INTO usuarios_google (usuario_id, google_sub) VALUES (?, ?)",
                    usuario.getId(), google.sub());
            auditoria.registrar(usuario.getId(), "GOOGLE_VINCULADO", "Cuenta de Google vinculada");
        } catch (DuplicateKeyException ex) {
            if (usuarioVinculado(google).filter(usuario.getId()::equals).isEmpty()) {
                throw new BadCredentialsException(VINCULO_OCUPADO);
            }
        }
    }

    private UsuarioSesion autorizar(Usuario usuario, String ip) {
        exigirActivo(usuario);
        UsuarioSesion sesion = authService.prepararSesion(usuario);
        auditoria.registrar(usuario.getId(), "LOGIN_EXITOSO",
                "Login con Google. Rol efectivo: " + sesion.getRolEfectivo() + " (ip=" + ip + ")");
        return sesion;
    }

    private static void exigirActivo(Usuario usuario) {
        if (!usuario.isActivo()) {
            throw new BadCredentialsException("Tu cuenta está inactiva. Contacta a un administrador.");
        }
    }
}
