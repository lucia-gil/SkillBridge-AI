package com.skillbridge.ai.service.agente;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.model.Proyecto;
import com.skillbridge.ai.repository.AgenteAuditoriaRepository;
import com.skillbridge.ai.repository.AsignacionRepository;
import com.skillbridge.ai.repository.PerfilRepository;
import com.skillbridge.ai.repository.PropuestaAsignacionRepository;
import com.skillbridge.ai.repository.ProyectoRepository;
import com.skillbridge.ai.repository.UsuarioRepository;
import com.skillbridge.ai.service.AsistenteContextoService;
import com.skillbridge.ai.service.EventoService;
import com.skillbridge.ai.service.ProyectoService;
import com.skillbridge.ai.service.TalentMatchingService;
import com.skillbridge.ai.service.UsuarioService;
import com.skillbridge.ai.util.Roles;
import jakarta.validation.Validator;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.skillbridge.ai.service.agente.ArgumentosAgente.*;

/**
 * Herramientas que el agente IA puede usar, por rol. Las de lectura
 * responden al momento; las de escritura solo se ejecutan desde
 * ConfirmacionAgenteService, después de que la persona confirma.
 * Los identificadores que recibe son de recursos (proyecto, propuesta,
 * cuenta), nunca de la identidad del actor: esa sale siempre de la sesión.
 */
@Service
public class HerramientasAgente {

    public record Definicion(String nombre, String descripcion, String rol, boolean escritura, Class<?> tipo) {}

    private static final List<Definicion> DEFINICIONES = List.of(
            new Definicion("consultar_mi_perfil_proyectos_entregables", "Consulta perfil, habilidades, carga y pendientes propios.", Roles.COLABORADOR, false, Consulta.class),
            new Definicion("consultar_mis_proyectos_y_candidatos", "Consulta proyectos liderados y cobertura.", Roles.PROJECT_MANAGER, false, Consulta.class),
            new Definicion("consultar_capacidad_y_solicitudes", "Consulta disponibilidad y solicitudes organizacionales.", Roles.RESOURCE_MANAGER, false, Consulta.class),
            new Definicion("consultar_metricas_globales", "Consulta métricas agregadas.", Roles.ADMINISTRADOR, false, Consulta.class),
            new Definicion("listar_mis_proyectos", "Lista IDs y nombres de proyectos liderados.", Roles.PROJECT_MANAGER, false, Vacio.class),
            new Definicion("buscar_candidatos", "Ranking explicado para un proyecto propio.", Roles.PROJECT_MANAGER, false, Candidatos.class),
            new Definicion("listar_solicitudes", "Lista propuestas pendientes de asignación.", Roles.RESOURCE_MANAGER, false, Vacio.class),
            new Definicion("consultar_mi_agenda", "Consulta los próximos eventos de la agenda personal y los hitos y entregables de los proyectos en curso.", Roles.RESOURCE_MANAGER, false, Vacio.class),
            new Definicion("listar_cuentas", "Lista hasta 30 cuentas: ID, nombre, rol y estado; sin secretos.", Roles.ADMINISTRADOR, false, Vacio.class),
            new Definicion("consultar_auditoria_agente", "Últimas 30 operaciones del agente, sin prompts ni argumentos.", Roles.ADMINISTRADOR, false, Vacio.class),
            new Definicion("actualizar_mi_biografia", "PREPARA actualización de presentación profesional propia; no cambia resumen IA validado. Requiere botón de confirmación.", Roles.COLABORADOR, true, Biografia.class),
            new Definicion("crear_proyecto", "PREPARA proyecto con el actor como PM. Requiere confirmación.", Roles.PROJECT_MANAGER, true, ProyectoNuevo.class),
            new Definicion("editar_proyecto", "PREPARA edición de proyecto propio conservando GitHub y estado. Requiere confirmación.", Roles.PROJECT_MANAGER, true, ProyectoEdicion.class),
            new Definicion("proponer_candidato", "PREPARA propuesta de candidato al RM, no asignación. Requiere confirmación.", Roles.PROJECT_MANAGER, true, Propuesta.class),
            new Definicion("resolver_propuesta", "PREPARA aprobación o rechazo; aprobar asigna según disponibilidad. Requiere confirmación.", Roles.RESOURCE_MANAGER, true, Resolucion.class),
            new Definicion("crear_evento_agenda", "PREPARA un evento en la agenda personal del Resource Manager (solo él lo ve). "
                    + "tipo: reunion, entregable o hito. fechaInicio: yyyy-MM-ddTHH:mm en hora de Lima. "
                    + "ubicacion, enlace (http/https) y descripcion pueden ir vacíos. Requiere confirmación.", Roles.RESOURCE_MANAGER, true, EventoAgenda.class),
            new Definicion("cambiar_estado_cuenta", "PREPARA activar/suspender cuenta. No fuerza salida de proyectos. Requiere confirmación.", Roles.ADMINISTRADOR, true, EstadoCuenta.class)
    );

    /** Máximo de filas que recibe el modelo en las listas. */
    private static final Pageable MAXIMO_FILAS = PageRequest.of(0, 30);
    private static final int MAXIMO_CARACTERES_ARGUMENTOS = 12000;

    private final PoliticaAgente politica;
    private final AsistenteContextoService contexto;
    private final TalentMatchingService matching;
    private final ProyectoService proyectos;
    private final UsuarioService usuarios;
    private final AsignacionRepository asignacionRepository;
    private final PropuestaAsignacionRepository propuestaRepository;
    private final UsuarioRepository usuarioRepository;
    private final PerfilRepository perfilRepository;
    private final ProyectoRepository proyectoRepository;
    private final AgenteAuditoriaRepository auditoriaRepository;
    private final EventoService eventos;
    private final Validator validator;
    private final ObjectMapper mapper;

    public HerramientasAgente(PoliticaAgente politica, AsistenteContextoService contexto, TalentMatchingService matching,
                              ProyectoService proyectos, UsuarioService usuarios,
                              AsignacionRepository asignacionRepository, PropuestaAsignacionRepository propuestaRepository,
                              UsuarioRepository usuarioRepository, PerfilRepository perfilRepository,
                              ProyectoRepository proyectoRepository, AgenteAuditoriaRepository auditoriaRepository,
                              EventoService eventos, Validator validator, ObjectMapper mapper) {
        this.eventos = eventos;
        this.politica = politica;
        this.contexto = contexto;
        this.matching = matching;
        this.proyectos = proyectos;
        this.usuarios = usuarios;
        this.asignacionRepository = asignacionRepository;
        this.propuestaRepository = propuestaRepository;
        this.usuarioRepository = usuarioRepository;
        this.perfilRepository = perfilRepository;
        this.proyectoRepository = proyectoRepository;
        this.auditoriaRepository = auditoriaRepository;
        this.validator = validator;
        // Lector estricto: sin campos desconocidos ni conversiones de tipo (un "5" no es un número).
        this.mapper = mapper.copy()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        this.mapper.coercionConfigFor(LogicalType.Integer)
                .setCoercion(CoercionInputShape.String, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail);
        this.mapper.coercionConfigFor(LogicalType.Boolean)
                .setCoercion(CoercionInputShape.String, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail);
        this.mapper.coercionConfigFor(LogicalType.Textual)
                .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
    }

    /** La herramienta, solo si existe para el rol efectivo del usuario. */
    public Definicion definir(UsuarioSesion s, String nombre) {
        politica.validar(s);
        return DEFINICIONES.stream()
                .filter(d -> d.nombre().equals(nombre) && d.rol().equals(s.getRolEfectivo()))
                .findFirst()
                .orElseThrow(() -> new AccessDeniedException("Herramienta no autorizada."));
    }

    /** Convierte y valida (Bean Validation) los argumentos que mandó el modelo. */
    public Object argumentos(Definicion d, JsonNode args) {
        if (args == null || !args.isObject() || args.toString().length() > MAXIMO_CARACTERES_ARGUMENTOS) {
            throw new IllegalArgumentException("Argumentos inválidos.");
        }
        try {
            Object dto = mapper.treeToValue(args, d.tipo());
            if (!validator.validate(dto).isEmpty()) throw new IllegalArgumentException();
            return dto;
        } catch (Exception e) {
            throw new IllegalArgumentException("Argumentos inválidos.");
        }
    }

    /** Catálogo de herramientas del rol, en el formato "tools" de la API de Claude. */
    public List<Map<String, Object>> catalogo(UsuarioSesion s) {
        politica.validar(s);
        return DEFINICIONES.stream().filter(d -> d.rol().equals(s.getRolEfectivo())).map(d -> {
            Map<String, Object> props = new LinkedHashMap<>();
            for (var c : d.tipo().getRecordComponents()) {
                String tipo = tipoJson(c.getType());
                props.put(c.getName(), "array".equals(tipo)
                        ? Map.of("type", tipo, "items", Map.of("type", "string"), "maxItems", 15)
                        : Map.of("type", tipo));
            }
            return Map.<String, Object>of("name", d.nombre(), "description", d.descripcion(),
                    "input_schema", Map.of("type", "object", "properties", props,
                            "required", new ArrayList<>(props.keySet()), "additionalProperties", false));
        }).toList();
    }

    private static String tipoJson(Class<?> tipo) {
        if (tipo == String.class || tipo == LocalDate.class) return "string";
        if (tipo == Boolean.class) return "boolean";
        if (tipo == List.class) return "array";
        return "integer";
    }

    /**
     * Si la herramienta apunta a un proyecto, el PM debe liderarlo. Además,
     * un evento se revisa antes de proponerlo, para no pedir confirmación de
     * algo que después fallaría.
     */
    public void autorizarRecurso(UsuarioSesion s, Object a) {
        if (a instanceof Candidatos x) politica.proyecto(s, x.proyectoId());
        if (a instanceof Propuesta x) politica.proyecto(s, x.proyectoId());
        if (a instanceof ProyectoEdicion x) politica.proyecto(s, x.proyectoId());
        if (a instanceof EventoAgenda x) {
            if (!x.tipo().matches("reunion|entregable|hito")) throw new IllegalArgumentException("Tipo de evento no válido.");
            try {
                java.time.LocalDateTime.parse(x.fechaInicio());
            } catch (java.time.format.DateTimeParseException e) {
                throw new IllegalArgumentException("Fecha no válida.");
            }
            if (!x.enlace().isBlank() && !x.enlace().matches("(?i)https?://\\S+")) throw new IllegalArgumentException("Enlace no válido.");
        }
    }

    /** Ejecuta una herramienta de lectura y devuelve el resultado en JSON (sin secretos). */
    public String leer(UsuarioSesion s, String nombre, JsonNode args) {
        Definicion d = definir(s, nombre);
        if (d.escritura()) throw new AccessDeniedException("Requiere confirmación.");
        Object a = argumentos(d, args);
        autorizarRecurso(s, a);

        Object resultado;
        if (a instanceof Consulta x) {
            resultado = contexto.construir(s, s.getRolEfectivo(), x.pregunta()).texto();
        } else if (a instanceof Candidatos x) {
            int[] pesos = matching.pesosConfigurados();
            resultado = matching.calcular(s.getPerfilId(), x.proyectoId(), x.dedicacion(), pesos[0], pesos[1], pesos[2])
                    .stream().limit(10).toList();
        } else {
            resultado = switch (nombre) {
                case "listar_mis_proyectos" -> asignacionRepository.proyectosLideradosParaAgente(s.getPerfilId(), MAXIMO_FILAS);
                case "listar_solicitudes" -> propuestaRepository.pendientesParaAgente(MAXIMO_FILAS);
                case "listar_cuentas" -> usuarioRepository.cuentasParaAgente(MAXIMO_FILAS);
                case "consultar_auditoria_agente" -> auditoriaRepository.ultimas(MAXIMO_FILAS);
                case "consultar_mi_agenda" -> agendaDesdeHoy(s);
                default -> throw new AccessDeniedException("Herramienta no autorizada.");
            };
        }
        try {
            return SalidaAgente.limpiar(mapper.writeValueAsString(resultado));
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo preparar el resultado.");
        }
    }

    /** Agenda del RM desde hoy (hasta 30 eventos): lo propio y los hitos de proyectos, sin datos de más. */
    private List<Map<String, Object>> agendaDesdeHoy(UsuarioSesion s) {
        String hoy = java.time.LocalDate.now(java.time.ZoneId.of("America/Lima")).toString();
        return eventos.listarResourceManager(s.getUsuarioId()).stream()
                .filter(e -> e.getFechaInicioIso().compareTo(hoy) >= 0)
                .limit(30)
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("titulo", e.getTitulo());
                    m.put("tipo", e.getTipoCodigo());
                    m.put("fecha", e.getFechaInicioIso());
                    m.put("origen", e.getProyectoNombre());
                    m.put("ubicacion", e.getUbicacionLabel());
                    return m;
                })
                .toList();
    }

    /** Solo la invoca ConfirmacionAgenteService, dentro de su transacción y con la confirmación bloqueada. */
    public void escribir(UsuarioSesion s, String nombre, JsonNode args) {
        Definicion d = definir(s, nombre);
        if (!d.escritura()) throw new AccessDeniedException("Operación inválida.");
        Object a = argumentos(d, args);
        autorizarRecurso(s, a);

        if (a instanceof Biografia x) {
            if (perfilRepository.actualizarBiografia(s.getPerfilId(), s.getUsuarioId(), x.biografia()) != 1) {
                throw new AccessDeniedException("Perfil no autorizado.");
            }
        } else if (a instanceof ProyectoNuevo x) {
            proyectos.crear(x.nombre(), x.descripcion(), x.tecnologias(), x.fechaInicio(), x.fechaFin(),
                    x.colaboradores(), s.getPerfilId(), s.getUsuarioId(), null);
        } else if (a instanceof ProyectoEdicion x) {
            // El agente no cambia el enlace de GitHub: se conserva el actual.
            Proyecto p = proyectoRepository.bloquear(x.proyectoId())
                    .orElseThrow(() -> new IllegalArgumentException("El proyecto no existe."));
            proyectos.editarProyecto(x.proyectoId(), x.nombre(), x.descripcion(), x.tecnologias(),
                    x.fechaInicio(), x.fechaFin(), p.getGithubUrl(), s.getUsuarioId());
        } else if (a instanceof Propuesta x) {
            matching.proponerManual(s.getPerfilId(), s.getUsuarioId(), x.proyectoId(), x.candidatoId(), x.dedicacion());
        } else if (a instanceof Resolucion x) {
            propuestaRepository.bloquear(x.propuestaId());
            matching.resolver(x.propuestaId(), x.aprobar(), x.motivo(), s.getPerfilId(), s.getUsuarioId());
        } else if (a instanceof EventoAgenda x) {
            eventos.crearPersonal(s.getUsuarioId(), x.tipo(), x.titulo(), x.descripcion(), x.fechaInicio(), x.ubicacion(), x.enlace());
        } else if (a instanceof EstadoCuenta x) {
            usuarios.cambiarEstado(x.cuentaId(), x.activar(), x.motivo(), false, s.getUsuarioId());
        } else {
            throw new AccessDeniedException("Operación inválida.");
        }
    }
}
