package com.skillbridge.ai.service;

import com.skillbridge.ai.repository.PerfilRepository;
import com.skillbridge.ai.util.Roles;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class IncidenteService {
    public enum Regla {
        AUTENTICACION(5, "A07:2025"), AUTORIZACION(3, "A01:2025");
        public final int umbral;
        public final String categoria;
        Regla(int umbral, String categoria) {
            this.umbral = umbral;
            this.categoria = categoria;
        }
    }

    private final JdbcTemplate jdbc;
    private final PerfilRepository perfiles;
    private final NotificacionService avisos;
    private final EmailService correo;
    private final String baseUrl;

    public IncidenteService(JdbcTemplate jdbc, PerfilRepository perfiles, NotificacionService avisos,
                            EmailService correo, @Value("${app.base-url}") String baseUrl) {
        this.jdbc = jdbc;
        this.perfiles = perfiles;
        this.avisos = avisos;
        this.correo = correo;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    @Transactional
    public void registrar(Regla regla) {
        long ventana = jdbc.queryForObject("SELECT FLOOR(UNIX_TIMESTAMP()/600)", Long.class);
        jdbc.update("""
                INSERT INTO seguridad_incidente(regla, ventana, categoria) VALUES(?,?,?)
                ON DUPLICATE KEY UPDATE cantidad=cantidad+1, ultimo=CURRENT_TIMESTAMP
                """, regla.name(), ventana, regla.categoria);
        var grupo = jdbc.queryForMap("SELECT id,cantidad FROM seguridad_incidente WHERE regla=? AND ventana=?",
                regla.name(), ventana);
        if (((Number) grupo.get("cantidad")).intValue() != regla.umbral) return;
        long id = ((Number) grupo.get("id")).longValue();
        jdbc.update("UPDATE seguridad_incidente SET severidad='alta',estado='en revisión' WHERE id=?", id);
        historial(id, null, "Umbral alcanzado", regla.name());
        for (Long perfil : perfiles.listarIdsConCuentaActivaPorRol(Roles.ADMINISTRADOR)) {
            avisos.crearInternoSeguridad(perfil, enlace(id), regla.name());
            jdbc.update("INSERT INTO seguridad_aviso(incidente,perfil) VALUES(?,?)", id, perfil);
        }
    }

    @Transactional
    public long abrirManual(long actor) {
        var key = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbc.update(con -> {
            var stmt = con.prepareStatement("""
                    INSERT INTO seguridad_incidente(regla,ventana,categoria,estado)
                    VALUES('MANUAL',UUID_SHORT(),'Sin asignar','en revisión')
                    """, java.sql.Statement.RETURN_GENERATED_KEYS);
            return stmt;
        }, key);
        long id = key.getKey().longValue();
        historial(id, actor, "Revisión manual", null);
        return id;
    }

    static String enlace(long id) { return "/administrador/seguridad.html?id=" + id; }

    // Los lectores concurrentes toman destinatarios diferentes. SMTP se ejecuta después
    // del commit de detección. Un fallo queda en el historial operativo del incidente.
    @Scheduled(fixedDelayString = "${seguridad.correo.intervalo-ms:5000}")
    @Transactional
    public void enviarPendiente() {
        jdbc.update("""
                UPDATE seguridad_aviso a JOIN perfiles p ON p.id=a.perfil JOIN usuarios u ON u.id=p.usuario_id
                SET a.estado='destinatario inactivo' WHERE a.estado='en cola' AND
                (u.estado<>'activo' OR p.estado<>'activo' OR COALESCE(u.rol_organizacional,'')<>'administrador')
                """);
        var filas = jdbc.queryForList("""
                SELECT a.incidente,a.perfil,u.correo FROM seguridad_aviso a JOIN perfiles p ON p.id=a.perfil
                JOIN usuarios u ON u.id=p.usuario_id WHERE a.estado='en cola'
                AND u.estado='activo' AND u.rol_organizacional='administrador'
                ORDER BY a.incidente,a.perfil LIMIT 1 FOR UPDATE SKIP LOCKED
                """);
        if (filas.isEmpty()) return;
        var fila = filas.get(0);
        long id = ((Number) fila.get("incidente")).longValue();
        boolean ok = correo.probarEnvio(new EmailService.Correo((String) fila.get("correo"),
                "Revisar incidente " + id, "Seguridad", "Se alcanzó un umbral de seguridad",
                "Inicia sesión para consultar la evidencia.", baseUrl + enlace(id), "Ver incidente", false));
        jdbc.update("UPDATE seguridad_aviso SET estado=? WHERE incidente=? AND perfil=?",
                ok ? "aceptado por SMTP" : "fallo SMTP", id, fila.get("perfil"));
    }

    public void historial(Long id, Long actor, String accion, String motivo) {
        jdbc.update("INSERT INTO seguridad_historial(incidente,actor,accion,motivo) VALUES(?,?,?,?)",
                id, actor, accion, motivo);
    }

    public List<Map<String,Object>> listar(String estado, String regla, long antes) {
        return jdbc.queryForList("""
                SELECT * FROM seguridad_incidente WHERE (?='' OR estado=?) AND (?='' OR regla=?)
                AND id<? ORDER BY id DESC LIMIT 50
                """, estado, estado, regla, regla, antes);
    }

    @Transactional
    public void confirmar(long id, long actor) {
        if (jdbc.update("UPDATE seguridad_incidente SET estado='confirmado' WHERE id=? AND estado='en revisión'", id) == 1)
            historial(id, actor, "Incidente confirmado", null);
    }
}
