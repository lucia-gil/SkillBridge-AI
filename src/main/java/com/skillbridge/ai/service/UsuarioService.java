package com.skillbridge.ai.service;

import com.skillbridge.ai.dto.UsuarioFila;
import com.skillbridge.ai.model.CorreoAutorizado;
import com.skillbridge.ai.model.Perfil;
import com.skillbridge.ai.model.Usuario;
import com.skillbridge.ai.repository.CorreoAutorizadoRepository;
import com.skillbridge.ai.repository.PerfilRepository;
import com.skillbridge.ai.repository.UsuarioRepository;
import com.skillbridge.ai.util.OperacionInvalidaException;
import com.skillbridge.ai.util.Roles;
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

    private static final Pattern PATRON_CORREO_DOMINIO = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@nexacorp\\.com$", Pattern.CASE_INSENSITIVE);

    private final UsuarioRepository usuarioRepository;
    private final PerfilRepository perfilRepository;
    private final CorreoAutorizadoRepository correoAutorizadoRepository;
    private final AuditoriaService auditoriaService;

    public UsuarioService(UsuarioRepository usuarioRepository,
                           PerfilRepository perfilRepository,
                           CorreoAutorizadoRepository correoAutorizadoRepository,
                           AuditoriaService auditoriaService) {
        this.usuarioRepository = usuarioRepository;
        this.perfilRepository = perfilRepository;
        this.correoAutorizadoRepository = correoAutorizadoRepository;
        this.auditoriaService = auditoriaService;
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
        if (!PATRON_CORREO_DOMINIO.matcher(correo).matches()) {
            throw new OperacionInvalidaException("Ingresa un correo corporativo válido (@nexacorp.com).");
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
