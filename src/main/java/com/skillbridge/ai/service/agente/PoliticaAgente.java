package com.skillbridge.ai.service.agente;

import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.model.Asignacion;
import com.skillbridge.ai.model.Usuario;
import com.skillbridge.ai.repository.AsignacionRepository;
import com.skillbridge.ai.repository.PerfilRepository;
import com.skillbridge.ai.repository.UsuarioRepository;
import com.skillbridge.ai.util.Roles;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Reglas de acceso del agente IA. Antes de cada herramienta vuelve a leer de
 * la base al usuario (estado, rol fijo y asignaciones) en lugar de confiar
 * en lo que guarda la sesión o en lo que diga el modelo.
 */
@Service
public class PoliticaAgente {

    private static final String ACTIVA = "activa";

    private final UsuarioRepository usuarioRepository;
    private final PerfilRepository perfilRepository;
    private final AsignacionRepository asignacionRepository;

    public PoliticaAgente(UsuarioRepository usuarioRepository, PerfilRepository perfilRepository,
                          AsignacionRepository asignacionRepository) {
        this.usuarioRepository = usuarioRepository;
        this.perfilRepository = perfilRepository;
        this.asignacionRepository = asignacionRepository;
    }

    /** La sesión coincide con el usuario autenticado y su rol efectivo sigue vigente en la base. */
    public void validar(UsuarioSesion s) {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (s == null || a == null || !a.isAuthenticated()
                || !(a.getPrincipal() instanceof UsuarioSesion p)
                || !Objects.equals(p.getUsuarioId(), s.getUsuarioId())
                || a.getAuthorities().stream().noneMatch(x -> x.getAuthority().equals(s.getRolEfectivo()))) {
            denegar();
        }

        Usuario usuario = usuarioRepository.findById(s.getUsuarioId()).orElse(null);
        if (usuario == null || !"activo".equals(usuario.getEstado())) denegar();

        String org = usuario.getRolOrganizacional();
        String rol = s.getRolEfectivo();
        if (Roles.ADMINISTRADOR.equals(rol) || Roles.RESOURCE_MANAGER.equals(rol)) {
            if (!rol.equals(org)) denegar();
        } else if (Roles.COLABORADOR.equals(rol) || Roles.PROJECT_MANAGER.equals(rol)) {
            // Miembro: sin rol fijo y con un perfil propio.
            if (org != null || s.getPerfilId() == null
                    || !perfilRepository.existsByIdAndUsuarioId(s.getPerfilId(), s.getUsuarioId())) denegar();
            if (Roles.PROJECT_MANAGER.equals(rol) && !asignacionRepository
                    .existsByPerfilIdAndRolEnProyectoAndEstado(s.getPerfilId(), Roles.PROJECT_MANAGER, ACTIVA)) denegar();
        } else {
            denegar();
        }
    }

    /** Además de validar, exige que el PM lidere ese proyecto. */
    public void proyecto(UsuarioSesion s, Long proyectoId) {
        validar(s);
        if (!Roles.PROJECT_MANAGER.equals(s.getRolEfectivo()) || !asignacionRepository
                .existsByProyectoIdAndPerfilIdAndRolEnProyectoAndEstado(proyectoId, s.getPerfilId(), Roles.PROJECT_MANAGER, ACTIVA)) {
            denegar();
        }
    }

    /**
     * Huella (SHA-256) del usuario, su rol y todas sus asignaciones. Se guarda
     * al crear una conversación; si después cambia, la conversación se oculta.
     * El texto conserva el formato de la versión anterior
     * ({proyecto_id=.., rol_en_proyecto=.., estado=..}) para que las huellas
     * ya guardadas sigan siendo válidas.
     */
    public String firma(UsuarioSesion s) {
        validar(s);
        List<Map<String, Object>> recursos = s.getPerfilId() == null ? List.of()
                : asignacionRepository.findByPerfilIdOrderByProyectoIdAscRolEnProyectoAscEstadoAsc(s.getPerfilId())
                        .stream().map(PoliticaAgente::recurso).toList();
        String base = s.getUsuarioId() + ":" + s.getRolEfectivo() + ":" + recursos;
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(base.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Map<String, Object> recurso(Asignacion a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("proyecto_id", a.getProyectoId());
        m.put("rol_en_proyecto", a.getRolEnProyecto());
        m.put("estado", a.getEstado());
        return m;
    }

    private static void denegar() {
        throw new AccessDeniedException("Operación no autorizada.");
    }
}
