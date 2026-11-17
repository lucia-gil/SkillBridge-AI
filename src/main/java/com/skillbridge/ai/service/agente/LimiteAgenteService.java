package com.skillbridge.ai.service.agente;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.*;
@Service
public class LimiteAgenteService {
 private final JdbcTemplate db;private final int max;
 public LimiteAgenteService(JdbcTemplate db,@Value("${agente.solicitudes-minuto:8}") int max){this.db=db;this.max=Math.max(1,max);}
 @Transactional public String adquirir(Long usuario){
  long ahora=db.queryForObject("SELECT UNIX_TIMESTAMP()",Long.class);long ventana=ahora/60;
  db.update("INSERT IGNORE INTO agente_limites(usuario_id,ventana,cantidad,lease_hasta) VALUES (?,?,0,0)",usuario,ventana);
  var row=db.queryForMap("SELECT ventana,cantidad,lease_hasta FROM agente_limites WHERE usuario_id=? FOR UPDATE",usuario);
  int cantidad=((Number)row.get("ventana")).longValue()==ventana?((Number)row.get("cantidad")).intValue():0;
  if(cantidad>=max || ((Number)row.get("lease_hasta")).longValue()>ahora)throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Límite del agente; intenta más tarde.");
  String id=UUID.randomUUID().toString();
  db.update("UPDATE agente_limites SET ventana=?,cantidad=?,lease_id=?,lease_hasta=? WHERE usuario_id=?",ventana,cantidad+1,id,ahora+240,usuario);
  return id;
 }
 @Transactional public void liberar(Long usuario,String id){db.update("UPDATE agente_limites SET lease_hasta=0,lease_id=NULL WHERE usuario_id=? AND lease_id=?",usuario,id);}
}
