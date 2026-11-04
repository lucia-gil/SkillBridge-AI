package com.skillbridge.ai.service;

import com.skillbridge.ai.dto.EntregableFila;
import com.skillbridge.ai.dto.MatchingCandidato;
import com.skillbridge.ai.dto.MiProyectoFila;
import com.skillbridge.ai.dto.PanelRm;
import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.model.Habilidad;
import com.skillbridge.ai.model.Perfil;
import com.skillbridge.ai.model.PerfilHabilidad;
import com.skillbridge.ai.model.Proyecto;
import com.skillbridge.ai.repository.AsignacionRepository;
import com.skillbridge.ai.repository.HabilidadRepository;
import com.skillbridge.ai.repository.PerfilHabilidadRepository;
import com.skillbridge.ai.repository.PerfilRepository;
import com.skillbridge.ai.repository.ProyectoRepository;
import com.skillbridge.ai.repository.UsuarioRepository;
import com.skillbridge.ai.util.Roles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Contexto real que recibe el Asistente IA según quién pregunta y desde qué
 * vista (rol efectivo). Se arma en cada mensaje con JPA, así la IA responde
 * con datos actuales y solo con lo que ese rol puede ver:
 *  - Colaborador: su perfil, habilidades, proyectos, carga y entregables.
 *  - Project Manager: sus proyectos (equipo, vacantes, huecos, entregables).
 *  - Resource Manager: capacidad del equipo, banca, solicitudes y proyectos
 *    con vacantes; si pregunta por candidatos para un proyecto, se agrega el
 *    ranking de AI Talent Matching calculado en Java.
 *  - Administrador: cifras globales de la plataforma.
 * Las mismas piezas se muestran en el panel "Contexto cargado".
 */
@Service
public class AsistenteContextoService {

    private static final Logger log = LoggerFactory.getLogger(AsistenteContextoService.class);
    private static final Pattern PIDE_CANDIDATOS = Pattern.compile(
            "quien|candidat|asign|poner|sumar|recomiend|perfil|refuerz|necesit|busc|libre|disponib");

    /** Tarjeta del panel "Contexto cargado". */
    public record Tarjeta(String icono, String tono, String titulo, String detalle, String enlace) {}

    /** Enlace sugerido debajo de una respuesta ("Ver proyecto", "Abrir AI Talent Matching"…). */
    public record Accion(String etiqueta, String url) {}

    public record Contexto(String texto, List<Tarjeta> tarjetas, List<String> sugerencias, List<Accion> acciones) {}

    private final ProyectoService proyectoService;
    private final ProyectoVistaService proyectoVistaService;
    private final EntregableService entregableService;
    private final TalentMatchingService talentMatchingService;
    private final RmPanelService rmPanelService;
    private final PerfilRepository perfilRepository;
    private final PerfilHabilidadRepository perfilHabilidadRepository;
    private final HabilidadRepository habilidadRepository;
    private final AsignacionRepository asignacionRepository;
    private final ProyectoRepository proyectoRepository;
    private final UsuarioRepository usuarioRepository;

    public AsistenteContextoService(ProyectoService proyectoService, ProyectoVistaService proyectoVistaService,
                                    EntregableService entregableService, TalentMatchingService talentMatchingService,
                                    RmPanelService rmPanelService, PerfilRepository perfilRepository,
                                    PerfilHabilidadRepository perfilHabilidadRepository, HabilidadRepository habilidadRepository,
                                    AsignacionRepository asignacionRepository, ProyectoRepository proyectoRepository,
                                    UsuarioRepository usuarioRepository) {
        this.proyectoService = proyectoService;
        this.proyectoVistaService = proyectoVistaService;
        this.entregableService = entregableService;
        this.talentMatchingService = talentMatchingService;
        this.rmPanelService = rmPanelService;
        this.perfilRepository = perfilRepository;
        this.perfilHabilidadRepository = perfilHabilidadRepository;
        this.habilidadRepository = habilidadRepository;
        this.asignacionRepository = asignacionRepository;
        this.proyectoRepository = proyectoRepository;
        this.usuarioRepository = usuarioRepository;
    }

    /** Contexto para un mensaje (pregunta puede ser null: solo el panel lateral). */
    @Transactional(readOnly = true)
    public Contexto construir(UsuarioSesion s, String rol, String pregunta) {
        try {
            return switch (rol) {
                case Roles.RESOURCE_MANAGER -> resourceManager(s, pregunta);
                case Roles.PROJECT_MANAGER -> projectManager(s, pregunta);
                case Roles.ADMINISTRADOR -> administrador();
                default -> colaborador(s);
            };
        } catch (Exception ex) {
            log.warn("Asistente IA: no se pudo armar el contexto de {} ({}): {}", s.getCorreo(), rol, ex.getMessage());
            return new Contexto("(No se pudieron cargar los datos del usuario en este momento.)", List.of(), sugerencias(rol), List.of());
        }
    }

    public List<String> sugerencias(String rol) {
        return switch (rol) {
            case Roles.RESOURCE_MANAGER -> List.of("¿Quién está en la banca y qué sabe hacer?",
                    "¿Qué proyectos tienen vacantes sin cubrir?", "¿Hay alguien sobrecargado?");
            case Roles.PROJECT_MANAGER -> List.of("¿Cómo va mi equipo y qué me falta cubrir?",
                    "¿Qué entregables tengo abiertos o vencidos?", "Dame 3 acciones para esta semana");
            case Roles.ADMINISTRADOR -> List.of("Resume el estado de la plataforma",
                    "¿Cuántos proyectos hay por estado?", "¿Qué debería revisar hoy?");
            default -> List.of("¿Qué entregables tengo pendientes?", "¿Qué habilidad me conviene reforzar?",
                    "¿Cuánta carga tengo y en qué proyectos?");
        };
    }

    // ───────────────────────── Colaborador ─────────────────────────

    private Contexto colaborador(UsuarioSesion s) {
        StringBuilder t = new StringBuilder();
        List<Tarjeta> tarjetas = new ArrayList<>();
        Long perfilId = s.getPerfilId();
        if (perfilId == null) return new Contexto("(El usuario no tiene perfil de colaborador.)", tarjetas, sugerencias(Roles.COLABORADOR), List.of());
        Perfil p = perfilRepository.buscarConUsuario(perfilId).orElse(null);
        int carga = proyectoService.cargaActivaDe(perfilId);
        int limite = proyectoService.limiteCargaConfigurado();
        if (p != null) {
            t.append("Perfil: ").append(Optional.ofNullable(p.getCargo()).orElse("sin cargo")).append(", ")
                    .append(Optional.ofNullable(p.getExperienciaAnios()).orElse(0)).append(" año(s) de experiencia, disponibilidad declarada ")
                    .append(Optional.ofNullable(p.getDisponibilidadPorcentaje()).orElse(100)).append("%.\n");
        }
        t.append("Carga actual: ").append(carga).append("% (límite ").append(limite).append("%).\n");

        List<MiProyectoFila> proyectos = proyectoService.misProyectosComoColaborador(perfilId);
        t.append("Proyectos activos (").append(proyectos.size()).append("): ");
        t.append(proyectos.isEmpty() ? "ninguno" : proyectos.stream()
                .map(x -> x.getNombre() + " (" + x.getEstadoLabel() + ", " + x.getCargaPorcentaje() + "% de dedicación, avance " + x.getAvance() + "%)")
                .collect(Collectors.joining("; "))).append(".\n");
        for (MiProyectoFila x : proyectos.stream().limit(3).toList()) {
            tarjetas.add(new Tarjeta("icon-folder", "blue", x.getNombre(), "Proyecto · " + x.getCargaPorcentaje() + "% de dedicación", "proyectos.html"));
        }

        Map<Long, String> nombres = habilidadRepository.findAll().stream().collect(Collectors.toMap(Habilidad::getId, Habilidad::getNombre));
        List<PerfilHabilidad> habs = perfilHabilidadRepository.findById_PerfilId(perfilId);
        long porValidar = habs.stream().filter(h -> h.getValidadoPorId() == null).count();
        t.append("Habilidades declaradas: ").append(habs.isEmpty() ? "ninguna" : habs.stream()
                .map(h -> nombres.getOrDefault(h.getId().getHabilidadId(), "?") + " " + nivel(h.getNivel()) + (h.getValidadoPorId() == null ? " (sin validar)" : ""))
                .collect(Collectors.joining(", "))).append(".\n");
        tarjetas.add(new Tarjeta("icon-user-check", "violet", "Mi perfil de habilidades",
                habs.size() + " habilidad(es)" + (porValidar > 0 ? " · " + porValidar + " por validar" : ""), "perfil.html"));

        List<EntregableFila> pendientes = entregableService.misEntregables(perfilId).stream()
                .filter(e -> "activo".equals(e.getEstadoCrudo()) && e.getMiEntrega() == null).toList();
        t.append("Entregables pendientes de entregar: ").append(pendientes.isEmpty() ? "ninguno" : pendientes.stream()
                .map(e -> e.getTitulo() + " de " + e.getProyectoNombre() + " (cierra " + e.getFechaCierreLabel() + (e.isVencido() ? ", VENCIDO" : "") + ")")
                .collect(Collectors.joining("; "))).append(".\n");
        tarjetas.add(new Tarjeta("icon-file-text", pendientes.isEmpty() ? "green" : "amber", "Entregables",
                pendientes.isEmpty() ? "Nada pendiente" : pendientes.size() + " pendiente(s)", "entregables.html"));
        tarjetas.add(0, new Tarjeta("icon-activity", carga > limite ? "red" : "green", "Mi carga", carga + "% de " + limite + "%", null));
        return new Contexto(t.toString(), tarjetas, sugerencias(Roles.COLABORADOR), List.of());
    }

    // ───────────────────────── Project Manager ─────────────────────────

    private Contexto projectManager(UsuarioSesion s, String pregunta) {
        StringBuilder t = new StringBuilder();
        List<Tarjeta> tarjetas = new ArrayList<>();
        List<Accion> acciones = new ArrayList<>();
        List<Proyecto> lidera = s.getPerfilId() == null ? List.of() : asignacionRepository.listarProyectosGestionados(s.getPerfilId()).stream()
                .filter(p -> !"completado".equals(p.getEstado()) && !"cancelado".equals(p.getEstado())).toList();
        t.append("Proyectos que lidera (").append(lidera.size()).append("):\n");
        String q = normalizar(pregunta);
        for (Proyecto p : lidera.stream().limit(5).toList()) {
            ProyectoVistaService.Vista v = proyectoVistaService.vista(p.getId());
            String huecos = v.cobertura().stream().filter(c -> !c.cubierta()).map(c -> c.habilidad() + (c.hueco() ? " (nadie la tiene)" : " (nivel bajo)"))
                    .collect(Collectors.joining(", "));
            t.append("- ").append(p.getNombre()).append(": ").append(v.detalle().getEstadoLabel()).append(", avance ").append(v.detalle().getAvance())
                    .append("%, ").append(v.plazoLabel()).append(", equipo: ")
                    .append(v.equipo().stream().map(m -> m.nombre() + (m.esPm() ? " (PM)" : "") + " " + m.cargaProyecto() + "% (carga total " + m.cargaTotal() + "%)")
                            .collect(Collectors.joining(", ")))
                    .append("; vacantes: ").append(v.vacantes()).append("; propuestas pendientes: ").append(v.propuestasPendientes())
                    .append("; habilidades sin cubrir: ").append(huecos.isEmpty() ? "ninguna" : huecos)
                    .append("; entregables abiertos: ").append(v.entregablesAbiertos()).append(", vencidos: ").append(v.entregablesVencidos()).append(".\n");
            tarjetas.add(new Tarjeta("icon-folder", v.huecos() > 0 || v.entregablesVencidos() > 0 ? "amber" : "blue", p.getNombre(),
                    v.integrantes() + " integrante(s) · " + (v.vacantes() > 0 ? v.vacantes() + " vacante(s)" : "cupo completo"),
                    "/project-manager/proyectos/" + p.getId() + ".html"));
            if (!q.isEmpty() && q.contains(normalizar(p.getNombre()))) {
                acciones.add(new Accion("Ver " + p.getNombre(), "/project-manager/proyectos/" + p.getId() + ".html"));
                if (v.vacantes() > 0 && PIDE_CANDIDATOS.matcher(q).find()) {
                    t.append(candidatos(p));
                    acciones.add(new Accion("Buscar talento para " + p.getNombre(), "/project-manager/ai-talent-matching.html?proyectoId=" + p.getId()));
                }
            }
        }
        if (lidera.isEmpty()) t.append("(ninguno)\n");
        t.append("Nota: el PM no asigna directamente; propone candidatos y el Resource Manager aprueba.\n");
        return new Contexto(t.toString(), tarjetas, sugerencias(Roles.PROJECT_MANAGER), acciones);
    }

    // ───────────────────────── Resource Manager ─────────────────────────

    private Contexto resourceManager(UsuarioSesion s, String pregunta) {
        StringBuilder t = new StringBuilder();
        List<Tarjeta> tarjetas = new ArrayList<>();
        List<Accion> acciones = new ArrayList<>();
        PanelRm panel = rmPanelService.construir();
        PanelRm.Balance b = panel.balance();
        t.append("Equipo (límite de carga ").append(panel.limiteCarga()).append("%): ").append(b.total()).append(" miembros; ")
                .append(b.sinAsignar()).append(" sin asignar (banca), ").append(b.baja()).append(" con carga baja, ")
                .append(b.optimos()).append(" en carga óptima, ").append(b.alta()).append(" al límite y ").append(b.sobre()).append(" sobreasignados.\n");
        t.append("Personas con capacidad libre: ").append(panel.disponibles().isEmpty() ? "ninguna" : panel.disponibles().stream().limit(10)
                .map(d -> d.nombre() + " (" + d.cargo() + ", carga " + d.carga() + "%, libre " + d.libre() + "%"
                        + (d.diasEnBanca() != null ? ", en banca hace " + d.diasEnBanca() + " días" : "")
                        + (d.habilidades().isEmpty() ? "" : ", sabe " + d.habilidades().stream().limit(4).map(h -> h.nombre() + " " + nivel(h.nivel())).collect(Collectors.joining(", ")))
                        + (d.sugerenciaProyecto() != null ? ", encaja en " + d.sugerenciaProyecto() : "") + ")")
                .collect(Collectors.joining("; "))).append(".\n");
        t.append("Solicitudes de asignación pendientes: ").append(panel.solicitudesTotal()).append(panel.solicitudes().isEmpty() ? "" : " — " + panel.solicitudes().stream().limit(5)
                .map(x -> x.candidato() + " para " + x.proyecto() + " al " + x.dedicacion() + "% (pide " + x.solicitante() + (x.superaLimite() ? ", SUPERA el límite" : "") + ")")
                .collect(Collectors.joining("; "))).append(".\n");
        if (!panel.prediccion().enRiesgo().isEmpty()) {
            t.append("En riesgo de sobrecarga en ").append(panel.prediccion().dias()).append(" días: ")
                    .append(panel.prediccion().enRiesgo().stream().map(r -> r.nombre() + " (" + r.cargaFutura() + "%)").collect(Collectors.joining(", "))).append(".\n");
        }

        String q = normalizar(pregunta);
        List<Proyecto> conVacantes = proyectoRepository.findAll().stream()
                .filter(ProyectoService::admiteNuevosIntegrantes)
                .filter(p -> talentMatchingService.vacantesDe(p.getId()) > 0).toList();
        t.append("Proyectos con vacantes: ").append(conVacantes.isEmpty() ? "ninguno" : conVacantes.stream()
                .map(p -> p.getNombre() + " (" + talentMatchingService.vacantesDe(p.getId()) + " vacante(s), " + com.skillbridge.ai.dto.EstadoProyectoUtil.label(p.getEstado()) + ")")
                .collect(Collectors.joining("; "))).append(".\n");
        for (Proyecto p : conVacantes) {
            if (!q.isEmpty() && q.contains(normalizar(p.getNombre()))) {
                acciones.add(new Accion("Ver " + p.getNombre(), "/resource-manager/proyectos/" + p.getId() + ".html"));
                if (PIDE_CANDIDATOS.matcher(q).find()) t.append(candidatos(p));
            }
        }
        if (acciones.isEmpty() && PIDE_CANDIDATOS.matcher(q).find() && q.contains("proyecto")) {
            acciones.add(new Accion("Pregúntale a AI Talent Matching", "/resource-manager/ai-talent-matching.html"));
        }

        tarjetas.add(new Tarjeta("icon-users", "blue", "Capacidad del equipo", b.total() + " miembros · " + panel.enBanca() + " en la banca", "/resource-manager/ocupacion.html"));
        tarjetas.add(new Tarjeta("icon-alert-triangle", b.sobre() > 0 ? "red" : "green", "Sobreasignados", b.sobre() == 0 ? "Nadie sobre el límite" : b.sobre() + " persona(s)", "/resource-manager/ocupacion.html"));
        tarjetas.add(new Tarjeta("icon-target", conVacantes.isEmpty() ? "green" : "amber", "Proyectos con vacantes", conVacantes.size() + " proyecto(s)", "/resource-manager/asignaciones.html"));
        tarjetas.add(new Tarjeta("icon-sparkles", panel.solicitudesTotal() > 0 ? "violet" : "green", "Solicitudes por revisar", panel.solicitudesTotal() + " pendiente(s)", "/resource-manager/ai-talent-matching.html"));
        return new Contexto(t.toString(), tarjetas, sugerencias(Roles.RESOURCE_MANAGER), acciones);
    }

    // ───────────────────────── Administrador ─────────────────────────

    private Contexto administrador() {
        StringBuilder t = new StringBuilder();
        long activos = usuarioRepository.countByEstado("activo"), suspendidos = usuarioRepository.countByEstado("suspendido");
        t.append("Usuarios: ").append(activos).append(" activos, ").append(suspendidos).append(" suspendidos; ")
                .append(usuarioRepository.countByRolOrganizacional(Roles.ADMINISTRADOR)).append(" administradores, ")
                .append(usuarioRepository.countByRolOrganizacional(Roles.RESOURCE_MANAGER)).append(" resource managers, ")
                .append(usuarioRepository.countByRolOrganizacionalIsNull()).append(" miembros.\n");
        String[] estados = {"planificacion", "activo", "en_pausa", "completado", "cancelado"};
        List<String> partes = new ArrayList<>();
        for (String e : estados) partes.add(com.skillbridge.ai.dto.EstadoProyectoUtil.label(e) + ": " + proyectoRepository.countByEstado(e));
        t.append("Proyectos por estado: ").append(String.join(", ", partes)).append(".\n");
        t.append("Habilidades en el catálogo: ").append(habilidadRepository.count()).append(".\n");
        List<Tarjeta> tarjetas = List.of(
                new Tarjeta("icon-users", "blue", "Usuarios", activos + " activos" + (suspendidos > 0 ? " · " + suspendidos + " suspendidos" : ""), "/administrador/usuarios.html"),
                new Tarjeta("icon-folder", "violet", "Proyectos", proyectoRepository.countByEstado("activo") + " activos · " + proyectoRepository.countByEstado("planificacion") + " en planificación", "/administrador/proyectos.html"),
                new Tarjeta("icon-book", "green", "Catálogo de habilidades", habilidadRepository.count() + " habilidades", "/administrador/habilidades.html"));
        return new Contexto(t.toString(), tarjetas, sugerencias(Roles.ADMINISTRADOR), List.of());
    }

    // ───────────────────────── utilidades ─────────────────────────

    /** Ranking de AI Talent Matching (Java) para que la IA recomiende con datos reales. */
    private String candidatos(Proyecto p) {
        try {
            List<MatchingCandidato> top = talentMatchingService.rankingParaIa(p.getId(), MatchingIaService.DEDICACION_POR_DEFECTO, null, 5);
            if (top.isEmpty()) return "Candidatos para " + p.getNombre() + ": nadie con capacidad libre ahora.\n";
            return "Candidatos calculados por AI Talent Matching para " + p.getNombre() + " (50% de dedicación, mejor primero): "
                    + top.stream().map(c -> c.getNombre() + " (" + c.getCargo() + ", puntaje " + c.getScoreTotal() + ", habilidades "
                            + c.getScoreHabilidades() + "%, carga " + c.getCargaActual() + "%→" + c.getCargaProyectada() + "%)")
                    .collect(Collectors.joining("; ")) + ".\n";
        } catch (Exception ex) {
            return "";
        }
    }

    private static String nivel(Integer n) {
        if (n == null) return "";
        return switch (n) { case 1, 2 -> "básico"; case 3 -> "intermedio"; case 4 -> "avanzado"; case 5 -> "experto"; default -> ""; };
    }

    private static String normalizar(String v) {
        return Normalizer.normalize(Optional.ofNullable(v).orElse(""), Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase().trim();
    }
}
