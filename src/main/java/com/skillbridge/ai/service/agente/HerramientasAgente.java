package com.skillbridge.ai.service.agente;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.service.*;
import jakarta.validation.Validator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import java.util.*;
import static com.skillbridge.ai.service.agente.ArgumentosAgente.*;

@Service
public class HerramientasAgente {
 public record Definicion(String nombre,String descripcion,String rol,boolean escritura,Class<?> tipo){}
 private static final List<Definicion> DEFINICIONES=List.of(
  new Definicion("consultar_mi_perfil_proyectos_entregables","Consulta perfil, habilidades, carga y pendientes propios.","colaborador",false,Consulta.class),
  new Definicion("consultar_mis_proyectos_y_candidatos","Consulta proyectos liderados y cobertura.","project_manager",false,Consulta.class),
  new Definicion("consultar_capacidad_y_solicitudes","Consulta disponibilidad y solicitudes organizacionales.","resource_manager",false,Consulta.class),
  new Definicion("consultar_metricas_globales","Consulta métricas agregadas.","administrador",false,Consulta.class),
  new Definicion("listar_mis_proyectos","Lista IDs y nombres de proyectos liderados.","project_manager",false,Vacio.class),
  new Definicion("buscar_candidatos","Ranking explicado para un proyecto propio.","project_manager",false,Candidatos.class),
  new Definicion("listar_solicitudes","Lista propuestas pendientes de asignación.","resource_manager",false,Vacio.class),
  new Definicion("listar_cuentas","Lista hasta 30 cuentas: ID, nombre, rol y estado; sin secretos.","administrador",false,Vacio.class),
  new Definicion("consultar_auditoria_agente","Últimas 30 operaciones del agente, sin prompts ni argumentos.","administrador",false,Vacio.class),
  new Definicion("actualizar_mi_biografia","PREPARA actualización de presentación profesional propia; no cambia resumen IA validado. Requiere botón de confirmación.","colaborador",true,Biografia.class),
  new Definicion("crear_proyecto","PREPARA proyecto con el actor como PM. Requiere confirmación.","project_manager",true,ProyectoNuevo.class),
  new Definicion("editar_proyecto","PREPARA edición de proyecto propio conservando GitHub y estado. Requiere confirmación.","project_manager",true,ProyectoEdicion.class),
  new Definicion("proponer_candidato","PREPARA propuesta de candidato al RM, no asignación. Requiere confirmación.","project_manager",true,Propuesta.class),
  new Definicion("resolver_propuesta","PREPARA aprobación o rechazo; aprobar asigna según disponibilidad. Requiere confirmación.","resource_manager",true,Resolucion.class),
  new Definicion("cambiar_estado_cuenta","PREPARA activar/suspender cuenta. No fuerza salida de proyectos. Requiere confirmación.","administrador",true,EstadoCuenta.class)
 );
 private final PoliticaAgente politica;
 private final AsistenteContextoService contexto;
 private final TalentMatchingService matching;
 private final ProyectoService proyectos;
 private final UsuarioService usuarios;
 private final JdbcTemplate db;
 private final Validator validator;
 private final ObjectMapper mapper;
 public HerramientasAgente(PoliticaAgente politica,AsistenteContextoService contexto,TalentMatchingService matching,
   ProyectoService proyectos,UsuarioService usuarios,JdbcTemplate db,Validator validator,ObjectMapper mapper){
  this.politica=politica;this.contexto=contexto;this.matching=matching;this.proyectos=proyectos;this.usuarios=usuarios;this.db=db;this.validator=validator;
  this.mapper=mapper.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
  this.mapper.coercionConfigFor(LogicalType.Integer).setCoercion(CoercionInputShape.String,CoercionAction.Fail).setCoercion(CoercionInputShape.Float,CoercionAction.Fail);
  this.mapper.coercionConfigFor(LogicalType.Boolean).setCoercion(CoercionInputShape.String,CoercionAction.Fail).setCoercion(CoercionInputShape.Integer,CoercionAction.Fail);
  this.mapper.coercionConfigFor(LogicalType.Textual).setCoercion(CoercionInputShape.Integer,CoercionAction.Fail).setCoercion(CoercionInputShape.Boolean,CoercionAction.Fail);
 }
 public Definicion definir(UsuarioSesion s,String nombre){
  politica.validar(s);
  return DEFINICIONES.stream().filter(d->d.nombre().equals(nombre)&&d.rol().equals(s.getRolEfectivo())).findFirst().orElseThrow(()->new AccessDeniedException("Herramienta no autorizada."));
 }
 public Object argumentos(Definicion d,JsonNode args){
  if(args==null || !args.isObject() || args.toString().length()>12000) throw new IllegalArgumentException("Argumentos inválidos.");
  try{Object dto=mapper.treeToValue(args,d.tipo());if(!validator.validate(dto).isEmpty())throw new IllegalArgumentException();return dto;}
  catch(Exception e){throw new IllegalArgumentException("Argumentos inválidos.");}
 }
 public List<Map<String,Object>> catalogo(UsuarioSesion s){
  politica.validar(s);
  return DEFINICIONES.stream().filter(d->d.rol().equals(s.getRolEfectivo())).map(d->{
   Map<String,Object> props=new LinkedHashMap<>();
   for(var c:d.tipo().getRecordComponents()) {
    String tipo=c.getType()==String.class||c.getType()==java.time.LocalDate.class?"string":c.getType()==Boolean.class?"boolean":c.getType()==List.class?"array":"integer";
    props.put(c.getName(),"array".equals(tipo)?Map.of("type",tipo,"items",Map.of("type","string"),"maxItems",15):Map.of("type",tipo));
   }
   return Map.<String,Object>of("name",d.nombre(),"description",d.descripcion(),"input_schema",Map.of("type","object","properties",props,"required",new ArrayList<>(props.keySet()),"additionalProperties",false));
  }).toList();
 }
 public void autorizarRecurso(UsuarioSesion s,Object a){
  if(a instanceof Candidatos x)politica.proyecto(s,x.proyectoId());
  if(a instanceof Propuesta x)politica.proyecto(s,x.proyectoId());
  if(a instanceof ProyectoEdicion x)politica.proyecto(s,x.proyectoId());
 }
 public String leer(UsuarioSesion s,String nombre,JsonNode args){
  var d=definir(s,nombre);if(d.escritura())throw new AccessDeniedException("Requiere confirmación.");
  Object a=argumentos(d,args);autorizarRecurso(s,a);
  Object resultado;
  if(a instanceof Consulta x)resultado=contexto.construir(s,s.getRolEfectivo(),x.pregunta()).texto();
  else if(a instanceof Candidatos x){
   int[] pesos=matching.pesosConfigurados();
   resultado=matching.calcular(s.getPerfilId(),x.proyectoId(),x.dedicacion(),pesos[0],pesos[1],pesos[2]).stream().limit(10).toList();
  } else resultado=switch(nombre){
   case "listar_mis_proyectos" -> db.queryForList("SELECT DISTINCT p.id,p.nombre,p.estado FROM proyectos p JOIN asignaciones a ON a.proyecto_id=p.id WHERE a.perfil_id=? AND a.rol_en_proyecto='project_manager' AND a.estado='activa' ORDER BY p.id LIMIT 30",s.getPerfilId());
   case "listar_solicitudes" -> db.queryForList("SELECT id,proyecto_id,candidato_perfil_id,dedicacion_porcentaje,estado FROM propuestas_asignacion WHERE estado='pendiente' ORDER BY id DESC LIMIT 30");
   case "listar_cuentas" -> db.queryForList("SELECT id,nombre_completo,rol_organizacional,estado FROM usuarios ORDER BY id LIMIT 30");
   case "consultar_auditoria_agente" -> db.queryForList("SELECT usuario_id,herramienta,resultado,fecha FROM agente_auditoria ORDER BY id DESC LIMIT 30");
   default -> throw new AccessDeniedException("Herramienta no autorizada.");
  };
  try{return SalidaAgente.limpiar(mapper.writeValueAsString(resultado));}catch(Exception e){throw new IllegalStateException("No se pudo preparar el resultado.");}
 }
 /** Solo invocado por ConfirmacionAgenteService dentro de una transacción con fila bloqueada. */
 public void escribir(UsuarioSesion s,String nombre,JsonNode args){
  var d=definir(s,nombre);if(!d.escritura())throw new AccessDeniedException("Operación inválida.");
  Object a=argumentos(d,args);autorizarRecurso(s,a);
  if(a instanceof Biografia x){
   if(db.update("UPDATE perfiles SET biografia=? WHERE id=? AND usuario_id=?",x.biografia(),s.getPerfilId(),s.getUsuarioId())!=1)throw new AccessDeniedException("Perfil no autorizado.");
  }else if(a instanceof ProyectoNuevo x)proyectos.crear(x.nombre(),x.descripcion(),x.tecnologias(),x.fechaInicio(),x.fechaFin(),x.colaboradores(),s.getPerfilId(),s.getUsuarioId(),null);
  else if(a instanceof ProyectoEdicion x){
   String github=db.queryForObject("SELECT github_url FROM proyectos WHERE id=? FOR UPDATE",String.class,x.proyectoId());
   proyectos.editarProyecto(x.proyectoId(),x.nombre(),x.descripcion(),x.tecnologias(),x.fechaInicio(),x.fechaFin(),github,s.getUsuarioId());
  }else if(a instanceof Propuesta x)matching.proponerManual(s.getPerfilId(),s.getUsuarioId(),x.proyectoId(),x.candidatoId(),x.dedicacion());
  else if(a instanceof Resolucion x){
   db.queryForList("SELECT id FROM propuestas_asignacion WHERE id=? FOR UPDATE",x.propuestaId());
   matching.resolver(x.propuestaId(),x.aprobar(),x.motivo(),s.getPerfilId(),s.getUsuarioId());
  }else if(a instanceof EstadoCuenta x)usuarios.cambiarEstado(x.cuentaId(),x.activar(),x.motivo(),false,s.getUsuarioId());
  else throw new AccessDeniedException("Operación inválida.");
 }
}
