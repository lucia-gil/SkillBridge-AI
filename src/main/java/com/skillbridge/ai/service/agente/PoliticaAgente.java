package com.skillbridge.ai.service.agente;
import com.skillbridge.ai.dto.UsuarioSesion;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;
@Service
public class PoliticaAgente {
    private final JdbcTemplate db;
    public PoliticaAgente(JdbcTemplate db){this.db=db;}
    public void validar(UsuarioSesion s) {
        var a=SecurityContextHolder.getContext().getAuthentication();
        if(s==null || a==null || !a.isAuthenticated() || !(a.getPrincipal() instanceof UsuarioSesion p)
            || !Objects.equals(p.getUsuarioId(),s.getUsuarioId()) || a.getAuthorities().stream().noneMatch(x->x.getAuthority().equals(s.getRolEfectivo()))) denegar();
        var filas=db.queryForList("SELECT rol_organizacional, estado FROM usuarios WHERE id=?",s.getUsuarioId());
        if(filas.size()!=1 || !"activo".equals(filas.get(0).get("estado"))) denegar();
        Object org=filas.get(0).get("rol_organizacional");
        String rol=s.getRolEfectivo();
        if("administrador".equals(rol) || "resource_manager".equals(rol)) {
            if(!rol.equals(org)) denegar();
        } else if("colaborador".equals(rol) || "project_manager".equals(rol)) {
            if(org!=null || s.getPerfilId()==null || count("SELECT COUNT(*) FROM perfiles WHERE id=? AND usuario_id=?",s.getPerfilId(),s.getUsuarioId())!=1) denegar();
            if("project_manager".equals(rol) && count("SELECT COUNT(*) FROM asignaciones WHERE perfil_id=? AND rol_en_proyecto='project_manager' AND estado='activa'",s.getPerfilId())==0) denegar();
        } else denegar();
    }
    public void proyecto(UsuarioSesion s,Long id) {
        validar(s);
        if(!"project_manager".equals(s.getRolEfectivo()) || count("SELECT COUNT(*) FROM asignaciones WHERE proyecto_id=? AND perfil_id=? AND estado='activa' AND rol_en_proyecto='project_manager'",id,s.getPerfilId())==0) denegar();
    }
    public String firma(UsuarioSesion s) {
        validar(s);
        var recursos=s.getPerfilId()==null?List.of():db.queryForList("SELECT proyecto_id,rol_en_proyecto,estado FROM asignaciones WHERE perfil_id=? ORDER BY proyecto_id,rol_en_proyecto,estado",s.getPerfilId());
        try {return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest((s.getUsuarioId()+":"+s.getRolEfectivo()+":"+recursos).getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
    private long count(String sql,Object... args){return db.queryForObject(sql,Long.class,args);}
    private static void denegar(){throw new AccessDeniedException("Operación no autorizada.");}
}
