package com.skillbridge.ai.controller;
import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.util.SesionKeys;
import com.skillbridge.ai.service.agente.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController
@RequestMapping("/api/asistente/confirmaciones")
public class ConfirmacionAgenteController {
 private final ConfirmacionAgenteService servicio;private final LimiteAgenteService limites;private final boolean habilitado;
 public ConfirmacionAgenteController(ConfirmacionAgenteService servicio,LimiteAgenteService limites,@Value("${agente.enabled:true}") boolean habilitado){this.servicio=servicio;this.limites=limites;this.habilitado=habilitado;}
 private UsuarioSesion actor(HttpSession session){
  if(!habilitado)throw new AccessDeniedException("Agente deshabilitado.");
  var s=(UsuarioSesion)session.getAttribute(SesionKeys.USUARIO);if(s==null)throw new AccessDeniedException("Sesión requerida.");return s;
 }
 @GetMapping public List<Map<String,Object>> pendientes(HttpSession session){return servicio.pendientes(actor(session));}
 @PostMapping("/{id}/confirmar") public Map<String,Boolean> confirmar(@PathVariable String id,HttpSession session){
  var s=actor(session);String lease=limites.adquirir(s.getUsuarioId());
  try{servicio.resolver(s,id,true);return Map.of("ok",true);}finally{limites.liberar(s.getUsuarioId(),lease);}
 }
 @PostMapping("/{id}/cancelar") public Map<String,Boolean> cancelar(@PathVariable String id,HttpSession session){servicio.resolver(actor(session),id,false);return Map.of("ok",true);}
 @ExceptionHandler(AccessDeniedException.class) public ResponseEntity<?> denegado(){return ResponseEntity.status(403).body(Map.of("error","Operación no autorizada o confirmación vencida/utilizada."));}
 @ExceptionHandler({IllegalArgumentException.class,com.skillbridge.ai.util.OperacionInvalidaException.class}) public ResponseEntity<?> invalido(){return ResponseEntity.badRequest().body(Map.of("error","No se pudo aplicar: revisa los datos y las reglas de negocio en la pantalla correspondiente. No se ha confirmado el cambio."));}
}
