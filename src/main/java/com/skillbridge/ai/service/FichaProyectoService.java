package com.skillbridge.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillbridge.ai.dto.EstadoProyectoUtil;
import com.skillbridge.ai.dto.FichaProyecto;
import com.skillbridge.ai.dto.ReportePmFila;
import com.skillbridge.ai.model.Asignacion;
import com.skillbridge.ai.model.Entregable;
import com.skillbridge.ai.model.EventoProyecto;
import com.skillbridge.ai.model.Habilidad;
import com.skillbridge.ai.model.PerfilHabilidad;
import com.skillbridge.ai.model.Proyecto;
import com.skillbridge.ai.repository.AsignacionRepository;
import com.skillbridge.ai.repository.EntregableRepository;
import com.skillbridge.ai.repository.EventoProyectoRepository;
import com.skillbridge.ai.repository.HabilidadRepository;
import com.skillbridge.ai.repository.PerfilHabilidadRepository;
import com.skillbridge.ai.util.Roles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Arma la ficha completa de cada proyecto para los reportes (PM y RM):
 * de qué trata, equipo, cobertura de habilidades, entregables, próximos
 * hitos, alertas y, si se pide, un resumen corto escrito por la IA.
 * Los datos salen siempre de la base; la IA solo redacta el resumen.
 */
@Service
public class FichaProyectoService {

    /** Máximo de proyectos con ficha por reporte (el archivo no debe volverse enorme). */
    public static final int MAXIMO_FICHAS = 20;

    private static final Logger log = LoggerFactory.getLogger(FichaProyectoService.class);
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final AsignacionRepository asignacionRepository;
    private final EntregableRepository entregableRepository;
    private final EventoProyectoRepository eventoRepository;
    private final HabilidadRepository habilidadRepository;
    private final PerfilHabilidadRepository perfilHabilidadRepository;
    private final DemandaHabilidadesService demandaService;
    private final TalentMatchingService talentMatchingService;
    private final AsistenteIaService asistenteIaService;
    private final ObjectMapper mapper;

    public FichaProyectoService(AsignacionRepository asignacionRepository, EntregableRepository entregableRepository,
                                EventoProyectoRepository eventoRepository, HabilidadRepository habilidadRepository,
                                PerfilHabilidadRepository perfilHabilidadRepository, DemandaHabilidadesService demandaService,
                                TalentMatchingService talentMatchingService, AsistenteIaService asistenteIaService,
                                ObjectMapper mapper) {
        this.asignacionRepository = asignacionRepository;
        this.entregableRepository = entregableRepository;
        this.eventoRepository = eventoRepository;
        this.habilidadRepository = habilidadRepository;
        this.perfilHabilidadRepository = perfilHabilidadRepository;
        this.demandaService = demandaService;
        this.talentMatchingService = talentMatchingService;
        this.asistenteIaService = asistenteIaService;
        this.mapper = mapper;
    }

    /** Fichas de los proyectos (hasta MAXIMO_FICHAS, en el orden recibido) y, si se pide, con resumen IA. */
    @Transactional(readOnly = true)
    public List<FichaProyecto> fichas(List<Proyecto> proyectos, boolean conResumenIa) {
        Map<Long, Habilidad> catalogo = habilidadRepository.findAll().stream()
                .collect(Collectors.toMap(Habilidad::getId, Function.identity()));
        List<FichaProyecto> fichas = proyectos.stream().limit(MAXIMO_FICHAS).map(p -> ficha(p, catalogo)).toList();
        return conResumenIa ? conResumenIa(fichas) : fichas;
    }

    private FichaProyecto ficha(Proyecto p, Map<Long, Habilidad> catalogo) {
        List<Asignacion> equipo = asignacionRepository.listarEquipoDeProyecto(p.getId(), "activa");
        List<FichaProyecto.Miembro> miembros = equipo.stream().map(a -> new FichaProyecto.Miembro(
                a.getPerfil().getUsuario().getNombreCompleto(),
                Optional.ofNullable(a.getPerfil().getCargo()).orElse("Colaborador"),
                Roles.PROJECT_MANAGER.equals(a.getRolEnProyecto()) ? "Project Manager" : "Colaborador",
                Optional.ofNullable(a.getCargaPorcentaje()).orElse(0))).toList();
        String pm = miembros.stream().filter(m -> "Project Manager".equals(m.rol())).map(FichaProyecto.Miembro::nombre)
                .findFirst().orElse("Sin PM asignado");

        // Cobertura: el mejor nivel del equipo en cada habilidad que pide el proyecto.
        Map<Long, Integer> requisitos = demandaService.requisitos(p, catalogo.values());
        Map<Long, Map<Long, Integer>> nivelesPorPerfil = new HashMap<>();
        for (Asignacion a : equipo) {
            nivelesPorPerfil.put(a.getPerfilId(), perfilHabilidadRepository.findById_PerfilId(a.getPerfilId()).stream()
                    .collect(Collectors.toMap(ph -> ph.getId().getHabilidadId(), PerfilHabilidad::getNivel, Math::max)));
        }
        List<FichaProyecto.HabilidadCobertura> habilidades = new ArrayList<>();
        for (Map.Entry<Long, Integer> req : requisitos.entrySet()) {
            int mejor = 0;
            String quien = null;
            for (Asignacion a : equipo) {
                int n = nivelesPorPerfil.getOrDefault(a.getPerfilId(), Map.of()).getOrDefault(req.getKey(), 0);
                if (n > mejor) { mejor = n; quien = a.getPerfil().getUsuario().getNombreCompleto(); }
            }
            Habilidad h = catalogo.get(req.getKey());
            habilidades.add(new FichaProyecto.HabilidadCobertura(h != null ? h.getNombre() : "Habilidad", nivel(req.getValue()),
                    mejor >= req.getValue(), nivel(mejor), quien != null ? quien : "Nadie del equipo"));
        }
        habilidades.sort(Comparator.comparing(FichaProyecto.HabilidadCobertura::cubierta).thenComparing(FichaProyecto.HabilidadCobertura::nombre));

        // Entregables (los cancelados no cuentan) y próximos hitos del calendario.
        LocalDateTime ahora = LocalDateTime.now();
        List<Entregable> entregables = entregableRepository.findByProyectoIdOrderByFechaCierreAsc(p.getId()).stream()
                .filter(e -> !"cancelado".equals(e.getEstado())).toList();
        int vencidos = (int) entregables.stream().filter(e -> "activo".equals(e.getEstado()) && e.getFechaCierre() != null
                && e.getFechaCierre().isBefore(ahora)).count();
        int abiertos = (int) entregables.stream().filter(e -> "activo".equals(e.getEstado()) && (e.getFechaCierre() == null
                || !e.getFechaCierre().isBefore(ahora))).count();
        List<FichaProyecto.Hito> proximos = new ArrayList<>();
        entregables.stream().filter(e -> "activo".equals(e.getEstado()) && e.getFechaCierre() != null && !e.getFechaCierre().isBefore(ahora))
                .limit(3).forEach(e -> proximos.add(new FichaProyecto.Hito(e.getFechaCierre().format(FECHA_HORA), e.getTitulo(), "Entregable")));
        eventoRepository.listarPorProyectos(List.of(p.getId())).stream()
                .filter(e -> e.getFechaInicio() != null && !e.getFechaInicio().isBefore(ahora))
                .sorted(Comparator.comparing(EventoProyecto::getFechaInicio)).limit(3)
                .forEach(e -> proximos.add(new FichaProyecto.Hito(e.getFechaInicio().format(FECHA_HORA), e.getTitulo(),
                        e.getTipo() != null ? e.getTipo().getNombre() : "Evento")));
        proximos.sort(Comparator.comparing(h -> LocalDateTime.parse(h.fecha(), FECHA_HORA)));
        List<FichaProyecto.Hito> proximos5 = proximos.stream().limit(5).toList();

        int carga = miembros.stream().mapToInt(FichaProyecto.Miembro::dedicacion).sum();
        ReportePmFila fila = new ReportePmFila(p.getId(), p.getNombre(), p.getEstado(), p.getFechaInicio(), p.getFechaFinEstimada(),
                ProyectoService.avanceHeuristico(p.getFechaInicio(), p.getFechaFinEstimada(), p.getEstado()), equipo.size(), carga);
        long vacantes = talentMatchingService.vacantesDe(p.getId());

        List<String> alertas = new ArrayList<>();
        if (vencidos > 0) alertas.add(vencidos + " entregable(s) vencido(s) sin cerrar.");
        if (vacantes > 0 && ProyectoService.admiteNuevosIntegrantes(p)) alertas.add(vacantes + " vacante(s) sin cubrir.");
        long sinCubrir = habilidades.stream().filter(h -> !h.cubierta()).count();
        if (sinCubrir > 0) alertas.add(sinCubrir + " habilidad(es) requerida(s) sin cubrir por el equipo.");
        if (fila.isAtrasado()) alertas.add("La fecha de entrega estimada ya pasó.");
        if (p.getFechaFinEstimada() == null) alertas.add("No tiene fecha de entrega estimada.");

        return new FichaProyecto(p.getId(), p.getNombre(), Optional.ofNullable(p.getDescripcion()).filter(d -> !d.isBlank())
                .orElse("Sin descripción registrada."), tecnologias(p), EstadoProyectoUtil.label(p.getEstado()),
                p.getFechaInicio() != null ? p.getFechaInicio().format(FECHA) : "—",
                p.getFechaFinEstimada() != null ? p.getFechaFinEstimada().format(FECHA) : "Sin definir",
                fila.getAvance(), fila.getSituacion(),
                fila.getRiesgo() == null || fila.getRiesgo().isBlank() ? "No aplica" : fila.getRiesgo(), pm, vacantes, miembros, habilidades,
                entregables.size(), abiertos, vencidos, proximos5, alertas, null);
    }

    // ───────────────────────── Resumen IA ─────────────────────────

    private static final String PROMPT_RESUMEN = "Eres el analista de proyectos de SkillBridge AI. Recibes fichas de proyectos con "
            + "datos reales. Para CADA proyecto escribe un resumen ejecutivo en español de 2 o 3 oraciones (máximo 70 palabras): "
            + "de qué trata, cómo va (estado, avance, equipo) y el principal riesgo o la siguiente acción recomendada. "
            + "Usa solo los datos recibidos; no inventes cifras ni nombres. Responde SOLO con JSON válido, sin texto adicional: "
            + "{\"proyectos\":[{\"id\":123,\"resumen\":\"...\"}]}." + AsistenteIaService.REGLA_DATOS;

    private List<FichaProyecto> conResumenIa(List<FichaProyecto> fichas) {
        if (fichas.isEmpty()) return fichas;
        StringBuilder sb = new StringBuilder();
        for (FichaProyecto f : fichas) {
            sb.append("- id ").append(f.id()).append(" · ").append(f.nombre()).append(" · estado ").append(f.estado())
                    .append(" · ").append(f.fechaInicio()).append(" a ").append(f.fechaFin()).append(" · avance estimado ")
                    .append(f.avance()).append("% · situación ").append(f.situacion()).append(" · riesgo ").append(f.riesgo())
                    .append(" · equipo ").append(f.equipo().size()).append(" persona(s), PM ").append(f.projectManager())
                    .append(" · vacantes ").append(f.vacantes()).append(" · tecnologías ").append(String.join(", ", f.tecnologias()))
                    .append(" · habilidades cubiertas ").append(f.habilidadesCubiertas()).append(" de ").append(f.habilidades().size())
                    .append(" · entregables ").append(f.entregablesTotal()).append(" (").append(f.entregablesVencidos()).append(" vencidos)")
                    .append(" · alertas: ").append(f.alertas().isEmpty() ? "ninguna" : String.join(" ", f.alertas()))
                    .append("\n  Descripción: ").append(recortar(f.descripcion(), 600)).append('\n');
        }
        AsistenteIaService.Resultado r = asistenteIaService.consultar(PROMPT_RESUMEN,
                AsistenteIaService.comoDatos("proyectos", sb.toString()), Math.min(4000, 300 + fichas.size() * 180));
        Map<Long, String> resumenes = new LinkedHashMap<>();
        if (r.ok()) {
            try {
                String texto = r.texto().trim();
                int i = texto.indexOf('{'), j = texto.lastIndexOf('}');
                JsonNode json = mapper.readTree(i >= 0 && j > i ? texto.substring(i, j + 1) : texto);
                for (JsonNode n : json.path("proyectos")) {
                    String s = n.path("resumen").asText("").replaceAll("[\\r\\n]+", " ").trim();
                    if (!s.isEmpty()) resumenes.put(n.path("id").asLong(), recortar(s, 600));
                }
            } catch (Exception e) {
                log.warn("Fichas: la IA respondió en un formato inesperado: {}", e.getMessage());
            }
        }
        return fichas.stream().map(f -> resumenes.containsKey(f.id()) ? f.conResumenIa(resumenes.get(f.id())) : f).toList();
    }

    // ───────────────────────── utilidades ─────────────────────────

    private List<String> tecnologias(Proyecto p) {
        try {
            return p.getTecnologias() == null || p.getTecnologias().isBlank() ? List.of()
                    : List.of(mapper.readValue(p.getTecnologias(), String[].class));
        } catch (Exception e) {
            return List.of();
        }
    }

    private static String recortar(String s, int max) {
        return s == null ? "" : s.length() <= max ? s : s.substring(0, max) + "…";
    }

    private static String nivel(int n) {
        return switch (n) { case 1, 2 -> "Básico"; case 3 -> "Intermedio"; case 4 -> "Avanzado"; case 5 -> "Experto"; default -> "Sin nivel"; };
    }
}
