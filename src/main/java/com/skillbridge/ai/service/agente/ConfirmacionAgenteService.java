package com.skillbridge.ai.service.agente;
import com.fasterxml.jackson.databind.*;
import com.skillbridge.ai.dto.UsuarioSesion;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import java.util.*;
@Service
public class ConfirmacionAgenteService {
 private final JdbcTemplate db;private final HerramientasAgente tools;private final PoliticaAgente politica;private final ObjectMapper mapper;
 public ConfirmacionAgenteService(JdbcTemplate db,HerramientasAgente tools,PoliticaAgente politica,ObjectMapper mapper){this.db=db;this.tools=tools;this.politica=politica;this.mapper=mapper;}
 @Transactional public void preparar(UsuarioSesion s,String nombre,JsonNode args){
  var d=tools.definir(s,nombre);if(!d.escritura())throw new IllegalArgumentException();
  tools.autorizarRecurso(s,tools.argumentos(d,args));
  // Una propuesta pendiente por actor/rol; la última reemplaza la anterior.
  db.queryForList("SELECT id FROM usuarios WHERE id=? FOR UPDATE",s.getUsuarioId());
  db.update("UPDATE agente_confirmaciones SET estado='cancelada' WHERE usuario_id=? AND rol=? AND estado='pendiente'",s.getUsuarioId(),s.getRolEfectivo());
  db.update("INSERT INTO agente_confirmaciones(id,usuario_id,rol,herramienta,argumentos,estado,expira) VALUES (?,?,?,?,?,'pendiente',?)",UUID.randomUUID().toString(),s.getUsuarioId(),s.getRolEfectivo(),nombre,args.toString(),java.sql.Timestamp.from(db.queryForObject("SELECT CURRENT_TIMESTAMP",java.sql.Timestamp.class).toInstant().plusSeconds(300)));
  auditar(s,nombre,"PREPARADA",null);
 }
 @Transactional(readOnly=true) public List<Map<String,Object>> pendientes(UsuarioSesion s){
  politica.validar(s);
  return db.queryForList("SELECT id,herramienta,argumentos,expira FROM agente_confirmaciones WHERE usuario_id=? AND rol=? AND estado='pendiente' AND expira>CURRENT_TIMESTAMP ORDER BY creada DESC LIMIT 1",s.getUsuarioId(),s.getRolEfectivo());
 }
 @Transactional public void resolver(UsuarioSesion s,String id,boolean confirmar){
  if(id==null || !id.matches("[a-f0-9-]{36}"))throw new IllegalArgumentException("Confirmación inválida.");
  // Orden único de locks para serializar escrituras del agente entre instancias.
  db.queryForList("SELECT id FROM agente_mutex WHERE id=1 FOR UPDATE");
  db.queryForList("SELECT id FROM usuarios WHERE id=? FOR UPDATE",s.getUsuarioId());
  politica.validar(s);
  var rows=db.queryForList("SELECT herramienta,argumentos FROM agente_confirmaciones WHERE id=? AND usuario_id=? AND rol=? AND estado='pendiente' AND expira>CURRENT_TIMESTAMP FOR UPDATE",id,s.getUsuarioId(),s.getRolEfectivo());
  if(rows.size()!=1)throw new AccessDeniedException("Confirmación inexistente, vencida o utilizada.");
  var row=rows.get(0);String nombre=(String)row.get("herramienta");
  if(confirmar){
   try{tools.escribir(s,nombre,mapper.readTree((String)row.get("argumentos")));}
   catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalArgumentException("Confirmación inválida.");}
  }
  db.update("UPDATE agente_confirmaciones SET estado=? WHERE id=?",confirmar?"ejecutada":"cancelada",id);
  auditar(s,nombre,confirmar?"EJECUTADA":"CANCELADA",id);
 }
 public void auditar(UsuarioSesion s,String nombre,String resultado,String id){
  // Nunca guardar prompts, respuestas o argumentos en el log de auditoría.
  db.update("INSERT INTO agente_auditoria(usuario_id,herramienta,resultado,correlacion) VALUES (?,?,?,?)",s.getUsuarioId(),nombre.length()>80?"desconocida":nombre,resultado,id);
 }
}
