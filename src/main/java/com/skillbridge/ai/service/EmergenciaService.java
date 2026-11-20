package com.skillbridge.ai.service;

import com.skillbridge.ai.model.Usuario;
import com.skillbridge.ai.repository.UsuarioRepository;
import com.skillbridge.ai.util.Roles;
import com.skillbridge.ai.util.Totp;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
public class EmergenciaService {
    public record Portal(boolean activo, Long responsable, Long incidente, long version) {}
    private final JdbcTemplate jdbc;
    private final IncidenteService incidentes;
    private final UsuarioRepository usuarios;
    private final PasswordEncoder passwords;
    private final AuditoriaService auditoria;

    public EmergenciaService(JdbcTemplate jdbc, IncidenteService incidentes, UsuarioRepository usuarios,
                             PasswordEncoder passwords, AuditoriaService auditoria) {
        this.jdbc = jdbc;
        this.incidentes = incidentes;
        this.usuarios = usuarios;
        this.passwords = passwords;
        this.auditoria = auditoria;
    }

    public Portal estado() { return leer(false); }

    private Portal leer(boolean bloquear) {
        return jdbc.queryForObject("SELECT activo,responsable,incidente,version FROM seguridad_portal WHERE id=1"
                        + (bloquear ? " FOR UPDATE" : ""),
                (rs, n) -> new Portal(rs.getBoolean(1), rs.getObject(2, Long.class),
                        rs.getObject(3, Long.class), rs.getLong(4)));
    }

    @Transactional
    public boolean cambiar(Long actor, boolean activar, long version, long incidente, String motivo,
                           String password, String codigo) {
        Portal previo = leer(true);
        Usuario u = usuarios.findById(actor).orElse(null);
        if (u == null || !u.isActivo() || !Roles.ADMINISTRADOR.equals(u.getRolOrganizacional())
                || previo.activo() == activar || previo.version() != version
                || (!activar && (!Objects.equals(actor, previo.responsable()) || incidente != previo.incidente()))
                || motivo == null || motivo.isBlank() || motivo.length() > 300
                || jdbc.queryForObject("SELECT COUNT(*) FROM seguridad_incidente WHERE id=?", Integer.class, incidente) != 1)
            return false;

        long ahora = jdbc.queryForObject("SELECT UNIX_TIMESTAMP()", Long.class);
        jdbc.update("INSERT INTO seguridad_intentos(actor,inicio) VALUES(?,?) ON DUPLICATE KEY UPDATE actor=actor", actor, ahora);
        var intentos = jdbc.queryForMap("SELECT * FROM seguridad_intentos WHERE actor=? FOR UPDATE", actor);
        long inicio = ((Number) intentos.get("inicio")).longValue();
        int fallos = ((Number) intentos.get("fallos")).intValue();
        if (ahora - inicio >= 300) {
            fallos = 0;
            jdbc.update("UPDATE seguridad_intentos SET fallos=0,inicio=? WHERE actor=?", ahora, actor);
        }
        if (fallos >= 5) return false;
        long paso = ahora / Totp.PASO_SEGUNDOS;
        var secretos = jdbc.queryForList("SELECT secreto FROM usuarios_2fa WHERE usuario_id=? AND activo=1",
                String.class, actor);
        boolean valido = !secretos.isEmpty() && codigo != null && codigo.matches("[0-9]{6}")
                && Totp.codigo(Totp.base32Decodificar(secretos.get(0)), paso).equals(codigo)
                && paso > ((Number) intentos.get("ultimo_paso")).longValue()
                && password != null && passwords.matches(password, u.getContrasenaHash());
        if (!valido) {
            jdbc.update("UPDATE seguridad_intentos SET fallos=fallos+1 WHERE actor=?", actor);
            incidentes.historial(incidente, actor, fallos == 4 ? "Confirmaciones limitadas por 5 minutos" : "Confirmación rechazada",
                    activar ? "Activar" : "Restablecer");
            return false;
        }
        jdbc.update("UPDATE seguridad_intentos SET ultimo_paso=?,fallos=0 WHERE actor=?", paso, actor);
        jdbc.update("""
                UPDATE seguridad_portal SET activo=?,responsable=?,incidente=?,motivo=?,
                version=version+1,fecha=CURRENT_TIMESTAMP WHERE id=1
                """, activar, actor, incidente, motivo.trim());
        String accion = activar ? "EMERGENCIA_ACTIVADA" : "SERVICIO_RESTABLECIDO";
        incidentes.historial(incidente, actor, accion, motivo.trim());
        auditoria.registrar(actor, accion, "seguridad_incidente", incidente, null, null, motivo.trim());
        return true;
    }
}
