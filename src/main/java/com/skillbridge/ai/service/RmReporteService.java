package com.skillbridge.ai.service;

import com.skillbridge.ai.dto.CoberturaHabilidadFila;
import com.skillbridge.ai.dto.OcupacionColaboradorFila;
import com.skillbridge.ai.dto.ReporteExportable;
import com.skillbridge.ai.dto.ReporteRmDatos;
import com.skillbridge.ai.model.Habilidad;
import com.skillbridge.ai.model.Perfil;
import com.skillbridge.ai.model.PerfilHabilidad;
import com.skillbridge.ai.model.Proyecto;
import com.skillbridge.ai.repository.AsignacionRepository;
import com.skillbridge.ai.repository.HabilidadRepository;
import com.skillbridge.ai.repository.PerfilHabilidadRepository;
import com.skillbridge.ai.repository.PerfilRepository;
import com.skillbridge.ai.repository.ProyectoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Reportes del Resource Manager (resource-manager/reportes.html y sus
 * exportaciones a Excel / PDF).
 *
 * <ul>
 *   <li><b>Ocupación por colaborador</b>: todos los Miembros activos, incluidos
 *       los que no tienen ninguna asignación ("Sin asignación"), evaluados
 *       contra el límite de carga de Configuración.</li>
 *   <li><b>Oferta contra demanda de habilidades</b>: para cada habilidad que
 *       piden los proyectos en planificación o activos (regla de
 *       {@link DemandaHabilidadesService}), cuántos Miembros la tienen al nivel
 *       pedido y cuántos de ellos tienen capacidad libre.</li>
 * </ul>
 *
 * Los filtros se aplican aquí (no en el navegador) para que la vista y lo
 * exportado coincidan siempre.
 */
@Service
public class RmReporteService {

    /** Estados de proyecto que generan demanda de personas. */
    private static final Set<String> ESTADOS_CON_DEMANDA = Set.of("planificacion", "activo");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final ZoneId ZONA = ZoneId.of("America/Lima");

    private final PerfilRepository perfilRepository;
    private final AsignacionRepository asignacionRepository;
    private final PerfilHabilidadRepository perfilHabilidadRepository;
    private final HabilidadRepository habilidadRepository;
    private final ProyectoRepository proyectoRepository;
    private final DemandaHabilidadesService demandaService;
    private final ConfiguracionService configuracionService;

    public RmReporteService(PerfilRepository perfilRepository, AsignacionRepository asignacionRepository,
                            PerfilHabilidadRepository perfilHabilidadRepository, HabilidadRepository habilidadRepository,
                            ProyectoRepository proyectoRepository, DemandaHabilidadesService demandaService,
                            ConfiguracionService configuracionService) {
        this.perfilRepository = perfilRepository;
        this.asignacionRepository = asignacionRepository;
        this.perfilHabilidadRepository = perfilHabilidadRepository;
        this.habilidadRepository = habilidadRepository;
        this.proyectoRepository = proyectoRepository;
        this.demandaService = demandaService;
        this.configuracionService = configuracionService;
    }

    /**
     * Filtros del reporte (todos opcionales).
     *
     * @param q          texto a buscar en nombre o cargo
     * @param estados    estados de carga a mostrar ("Sin asignación", "Subutilizado", "Óptimo", "Al límite", "Sobre-asignado")
     * @param minimo     ocupación mínima (%), 0 = sin mínimo
     * @param soloBrecha true para ver solo habilidades sin cobertura o ajustadas
     */
    public record Filtros(String q, List<String> estados, int minimo, boolean soloBrecha) {
        public Filtros {
            q = q == null ? "" : q.trim();
            estados = estados == null ? List.of() : estados.stream().filter(e -> e != null && !e.isBlank()).toList();
            minimo = Math.max(0, Math.min(100, minimo));
        }

        public boolean vacios() {
            return q.isEmpty() && estados.isEmpty() && minimo == 0 && !soloBrecha;
        }
    }

    @Transactional(readOnly = true)
    public ReporteRmDatos generar(Filtros f) {
        int limite = configuracionService.limiteCarga();

        // ── Miembros activos y su carga ──────────────────────────────────
        Map<Long, Integer> carga = new HashMap<>();
        for (Object[] fila : asignacionRepository.sumarCargaActivaAgrupadaPorPerfil()) {
            carga.put((Long) fila[0], ((Number) fila[1]).intValue());
        }
        List<Perfil> miembros = perfilRepository.listarActivosConUsuario().stream()
                .filter(p -> p.getUsuario() != null && p.getUsuario().getRolOrganizacional() == null
                        && "activo".equals(p.getUsuario().getEstado()))
                .collect(Collectors.toList());

        List<OcupacionColaboradorFila> todas = miembros.stream()
                .map(p -> carga.containsKey(p.getId())
                        ? new OcupacionColaboradorFila(p.getId(), p.getUsuario().getNombreCompleto(), p.getCargo(), carga.get(p.getId()), limite)
                        : OcupacionColaboradorFila.sinAsignacion(p.getId(), p.getUsuario().getNombreCompleto(), p.getCargo(), limite))
                .sorted(Comparator.comparingInt(OcupacionColaboradorFila::getPromedio).reversed()
                        .thenComparing(OcupacionColaboradorFila::getNombre))
                .collect(Collectors.toList());

        List<OcupacionColaboradorFila> conAsignacion = todas.stream()
                .filter(o -> !"Sin asignación".equals(o.getEstadoLabel())).toList();
        int promedio = (int) Math.round(conAsignacion.stream().mapToInt(OcupacionColaboradorFila::getPromedio).average().orElse(0));
        int sobre = (int) todas.stream().filter(OcupacionColaboradorFila::isSobrecargado).count();
        int banca = todas.size() - conAsignacion.size();
        int libre = todas.stream().mapToInt(o -> Math.max(0, limite - o.getPromedio())).sum();

        String q = normalizar(f.q());
        List<OcupacionColaboradorFila> ocupacion = todas.stream()
                .filter(o -> q.isEmpty() || normalizar(o.getNombre() + " " + o.getCargo()).contains(q))
                .filter(o -> f.estados().isEmpty() || f.estados().contains(o.getEstadoLabel()))
                .filter(o -> o.getPromedio() >= f.minimo())
                .collect(Collectors.toList());

        // ── Oferta contra demanda de habilidades ─────────────────────────
        List<Habilidad> catalogo = habilidadRepository.findAllConCategoriaOrderByNombre();
        Map<Long, Habilidad> porId = catalogo.stream().collect(Collectors.toMap(Habilidad::getId, Function.identity()));
        List<Proyecto> vigentes = proyectoRepository.findAll().stream()
                .filter(p -> ESTADOS_CON_DEMANDA.contains(p.getEstado()))
                .sorted(Comparator.comparing(Proyecto::getNombre, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
        Map<Long, Map<Long, Integer>> requisitos = demandaService.requisitosPorProyecto(vigentes, catalogo);

        Map<Long, Integer> nivelPedido = new HashMap<>();
        Map<Long, List<String>> proyectosQueLaPiden = new HashMap<>();
        for (Proyecto p : vigentes) {
            requisitos.getOrDefault(p.getId(), Map.of()).forEach((habilidadId, nivel) -> {
                nivelPedido.merge(habilidadId, nivel, Math::max);
                proyectosQueLaPiden.computeIfAbsent(habilidadId, k -> new ArrayList<>()).add(p.getNombre());
            });
        }

        Set<Long> idsMiembros = miembros.stream().map(Perfil::getId).collect(Collectors.toSet());
        Map<Long, List<PerfilHabilidad>> declaradas = perfilHabilidadRepository.findAll().stream()
                .filter(ph -> idsMiembros.contains(ph.getId().getPerfilId()))
                .collect(Collectors.groupingBy(ph -> ph.getId().getHabilidadId()));

        List<CoberturaHabilidadFila> coberturaTodas = new ArrayList<>();
        for (Map.Entry<Long, Integer> e : nivelPedido.entrySet()) {
            Habilidad h = porId.get(e.getKey());
            if (h == null) continue;
            int nivel = e.getValue();
            List<Long> calificadas = declaradas.getOrDefault(h.getId(), List.of()).stream()
                    .filter(ph -> ph.getNivel() != null && ph.getNivel() >= nivel)
                    .map(ph -> ph.getId().getPerfilId()).toList();
            int disponibles = (int) calificadas.stream().filter(id -> carga.getOrDefault(id, 0) < limite).count();
            coberturaTodas.add(new CoberturaHabilidadFila(h.getId(), h.getNombre(), h.getCategoria().getId(),
                    h.getCategoria().getNombre(), proyectosQueLaPiden.get(h.getId()).size(), nivel,
                    calificadas.size(), disponibles, proyectosQueLaPiden.get(h.getId())));
        }
        coberturaTodas.sort(Comparator.comparingInt(CoberturaHabilidadFila::severidad)
                .thenComparing(Comparator.comparingInt(CoberturaHabilidadFila::proyectos).reversed())
                .thenComparing(CoberturaHabilidadFila::nombre, String.CASE_INSENSITIVE_ORDER));
        int conBrecha = (int) coberturaTodas.stream().filter(CoberturaHabilidadFila::conBrecha).count();
        List<CoberturaHabilidadFila> cobertura = f.soloBrecha()
                ? coberturaTodas.stream().filter(CoberturaHabilidadFila::conBrecha).toList()
                : coberturaTodas;

        return new ReporteRmDatos(limite, promedio, sobre, banca, libre, todas.size(), ocupacion, cobertura,
                conBrecha, coberturaTodas.size(), vigentes.size(), describir(f),
                LocalDateTime.now(ZONA).format(FECHA_HORA));
    }

    /** Contenido para el Excel / PDF: exactamente lo que se ve con los filtros aplicados. */
    public ReporteExportable paraExportar(ReporteRmDatos d) {
        ReporteExportable r = new ReporteExportable("Reporte de ocupación y habilidades", d.filtrosLabel())
                .kpi("Ocupación promedio", d.ocupacionPromedio() + "%")
                .kpi("Sobre-asignados", d.sobreAsignados() + " (límite " + d.limiteCarga() + "%)")
                .kpi("Sin asignación", d.enBanca())
                .kpi("Capacidad libre", d.capacidadLibre() + "% (" + d.capacidadLibrePersonas() + " personas a tiempo completo)")
                .kpi("Habilidades con brecha", d.habilidadesConBrecha() + " de " + d.habilidadesPedidas() + " pedidas");

        ReporteExportable.Tabla oc = r.tabla("Ocupación por colaborador",
                new String[]{"Colaborador", "Cargo", "Ocupación", "Estado"}, new float[]{2.4f, 2f, 1f, 1.3f});
        d.ocupacion().forEach(o -> oc.fila(o.getNombre(), o.getCargo(), ReporteExportable.porcentaje(o.getPromedio()), o.getEstadoLabel()));

        ReporteExportable.Tabla cb = r.tabla("Oferta contra demanda de habilidades",
                new String[]{"Habilidad", "Categoría", "Proyectos que la piden", "Nivel pedido", "Personas con ese nivel", "Con capacidad libre", "Cobertura"},
                new float[]{1.6f, 1.4f, 1.3f, 1.1f, 1.3f, 1.2f, 1.2f});
        d.cobertura().forEach(c -> cb.fila(c.nombre(), c.categoria(), c.proyectos(), c.nivelLabel(), c.calificadas(), c.disponibles(), c.estado()));
        return r;
    }

    private static String describir(Filtros f) {
        if (f.vacios()) return "Sin filtros: todos los miembros activos y todas las habilidades pedidas";
        List<String> partes = new ArrayList<>();
        if (!f.q().isEmpty()) partes.add("búsqueda \"" + f.q() + "\"");
        if (!f.estados().isEmpty()) partes.add("estado: " + String.join(", ", f.estados()));
        if (f.minimo() > 0) partes.add("ocupación desde " + f.minimo() + "%");
        if (f.soloBrecha()) partes.add("solo habilidades con brecha");
        return "Filtros: " + String.join("; ", partes);
    }

    private static String normalizar(String v) {
        return Normalizer.normalize(v == null ? "" : v, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase().trim();
    }
}
