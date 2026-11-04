package com.skillbridge.ai.service;

import com.skillbridge.ai.dto.CoberturaHabilidadFila;
import com.skillbridge.ai.dto.InicialesUtil;
import com.skillbridge.ai.dto.OcupacionColaboradorFila;
import com.skillbridge.ai.dto.PanelRm;
import com.skillbridge.ai.dto.ReporteRm;
import com.skillbridge.ai.dto.ReporteRm.*;
import com.skillbridge.ai.model.CategoriaHabilidad;
import com.skillbridge.ai.model.Habilidad;
import com.skillbridge.ai.model.Perfil;
import com.skillbridge.ai.model.PerfilHabilidad;
import com.skillbridge.ai.model.Proyecto;
import com.skillbridge.ai.repository.AsignacionRepository;
import com.skillbridge.ai.repository.HabilidadRepository;
import com.skillbridge.ai.repository.PerfilHabilidadRepository;
import com.skillbridge.ai.repository.PerfilRepository;
import com.skillbridge.ai.repository.ProyectoRepository;
import com.skillbridge.ai.repository.RecomendacionIaLogRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.text.Normalizer;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Dashboard de Reportes del Resource Manager y sus exportaciones (Excel/PDF).
 * Reúne, con los mismos filtros, lo que guarda la base:
 *  - capacidad: ocupación, banca (días), próximas liberaciones, riesgo de sobrecarga;
 *  - proyectos: equipo, vacantes, cobertura de habilidades, plazo y riesgo;
 *  - habilidades: oferta contra demanda y brechas;
 *  - decisiones: propuestas (resultado, origen, tiempo de resolución) y acierto de la IA;
 *  - entregas: cumplimiento a tiempo y calificaciones;
 *  - hallazgos: conclusiones automáticas para leer el reporte en 30 segundos.
 * Los filtros se aplican aquí para que pantalla, Excel y PDF coincidan.
 */
@Service
public class RmReporteService {

    private static final Set<String> CON_DEMANDA = Set.of("planificacion", "activo");
    private static final Set<String> VIGENTES = Set.of("planificacion", "activo", "en_pausa");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FECHA_CORTA = DateTimeFormatter.ofPattern("dd MMM", new Locale("es", "PE"));
    private static final ZoneId ZONA = ZoneId.of("America/Lima");

    /** Orden y claves de los estados de carga (coinciden con OcupacionColaboradorFila). */
    public static final List<String[]> ESTADOS_CARGA = List.of(
            new String[]{"banca", "Sin asignación"}, new String[]{"sub", "Subutilizado"}, new String[]{"opt", "Óptimo"},
            new String[]{"lim", "Al límite"}, new String[]{"sobre", "Sobre-asignado"});

    private final PerfilRepository perfilRepository;
    private final AsignacionRepository asignacionRepository;
    private final PerfilHabilidadRepository perfilHabilidadRepository;
    private final HabilidadRepository habilidadRepository;
    private final ProyectoRepository proyectoRepository;
    private final RecomendacionIaLogRepository recomendacionRepository;
    private final DemandaHabilidadesService demandaService;
    private final ConfiguracionService configuracionService;
    private final RmPanelService panelService;
    private final ProyectoVistaService proyectoVistaService;
    private final JdbcTemplate jdbc;

    public RmReporteService(PerfilRepository perfilRepository, AsignacionRepository asignacionRepository,
                            PerfilHabilidadRepository perfilHabilidadRepository, HabilidadRepository habilidadRepository,
                            ProyectoRepository proyectoRepository, RecomendacionIaLogRepository recomendacionRepository,
                            DemandaHabilidadesService demandaService, ConfiguracionService configuracionService,
                            RmPanelService panelService, ProyectoVistaService proyectoVistaService, JdbcTemplate jdbc) {
        this.perfilRepository = perfilRepository;
        this.asignacionRepository = asignacionRepository;
        this.perfilHabilidadRepository = perfilHabilidadRepository;
        this.habilidadRepository = habilidadRepository;
        this.proyectoRepository = proyectoRepository;
        this.recomendacionRepository = recomendacionRepository;
        this.demandaService = demandaService;
        this.configuracionService = configuracionService;
        this.panelService = panelService;
        this.proyectoVistaService = proyectoVistaService;
        this.jdbc = jdbc;
    }

    /**
     * Filtros (todos opcionales).
     *
     * @param q          texto en nombre o cargo
     * @param estados    estados de carga ("banca", "sub", "opt", "lim", "sobre")
     * @param cargo      cargo exacto
     * @param proyectoId solo el equipo, las habilidades y la fila de ese proyecto
     * @param categoriaId solo habilidades de esa categoría
     * @param minimo     ocupación mínima (%)
     * @param soloBrecha solo habilidades sin cobertura o ajustadas
     */
    public record Filtros(String q, List<String> estados, String cargo, Long proyectoId, Long categoriaId,
                          int minimo, boolean soloBrecha) {
        public Filtros {
            q = q == null ? "" : q.trim();
            cargo = cargo == null || cargo.isBlank() ? null : cargo.trim();
            estados = estados == null ? List.of() : estados.stream().filter(e -> e != null && !e.isBlank()).distinct().toList();
            minimo = Math.max(0, Math.min(100, minimo));
        }

        public boolean vacios() {
            return q.isEmpty() && estados.isEmpty() && cargo == null && proyectoId == null && categoriaId == null && minimo == 0 && !soloBrecha;
        }
    }

    @Transactional(readOnly = true)
    public ReporteRm generar(Filtros f) {
        int limite = configuracionService.limiteCarga();
        LocalDate hoy = LocalDate.now(ZONA);
        PanelRm panel = panelService.construir();

        // ── Personas ─────────────────────────────────────────────────────
        Map<Long, Integer> carga = new HashMap<>();
        for (Object[] fila : asignacionRepository.sumarCargaActivaAgrupadaPorPerfil()) {
            carga.put((Long) fila[0], ((Number) fila[1]).intValue());
        }
        List<Perfil> miembros = perfilRepository.listarActivosConUsuario().stream()
                .filter(p -> p.getUsuario() != null && p.getUsuario().getRolOrganizacional() == null
                        && "activo".equals(p.getUsuario().getEstado()))
                .toList();

        // Asignaciones activas: proyectos de cada persona y su fecha de liberación más próxima
        Map<Long, List<String>> proyectosDe = new HashMap<>();
        Map<Long, Set<Long>> proyectoIdsDe = new HashMap<>();
        Map<Long, LocalDate> liberaDe = new HashMap<>();
        jdbc.query("SELECT a.perfil_id, a.proyecto_id, p.nombre, a.carga_porcentaje, COALESCE(a.fecha_fin, p.fecha_fin_estimada) fin "
                + "FROM asignaciones a JOIN proyectos p ON p.id = a.proyecto_id WHERE a.estado = 'activa' ORDER BY a.carga_porcentaje DESC", rs -> {
            long perfil = rs.getLong(1);
            proyectosDe.computeIfAbsent(perfil, k -> new ArrayList<>()).add(rs.getString(3) + " " + rs.getInt(4) + "%");
            proyectoIdsDe.computeIfAbsent(perfil, k -> new HashSet<>()).add(rs.getLong(2));
            java.sql.Date fin = rs.getDate(5);
            if (fin != null && !fin.toLocalDate().isBefore(hoy)) liberaDe.merge(perfil, fin.toLocalDate(), (a, b) -> a.isBefore(b) ? a : b);
        });
        Map<Long, Integer> diasBanca = new HashMap<>();
        panel.disponibles().forEach(d -> { if (d.diasEnBanca() != null) diasBanca.put(d.perfilId(), d.diasEnBanca()); });

        List<Habilidad> catalogo = habilidadRepository.findAllConCategoriaOrderByNombre();
        Map<Long, Habilidad> habPorId = catalogo.stream().collect(Collectors.toMap(Habilidad::getId, Function.identity()));
        Map<Long, List<PerfilHabilidad>> habsDe = perfilHabilidadRepository.findAll().stream()
                .collect(Collectors.groupingBy(ph -> ph.getId().getPerfilId()));

        Set<Long> conFoto = new HashSet<>(jdbc.queryForList("SELECT id FROM usuarios WHERE foto_perfil IS NOT NULL", Long.class));
        List<Persona> todas = new ArrayList<>();
        for (Perfil p : miembros) {
            String nombre = p.getUsuario().getNombreCompleto();
            OcupacionColaboradorFila oc = carga.containsKey(p.getId())
                    ? new OcupacionColaboradorFila(p.getId(), nombre, p.getCargo(), carga.get(p.getId()), limite)
                    : OcupacionColaboradorFila.sinAsignacion(p.getId(), nombre, p.getCargo(), limite);
            int c = carga.getOrDefault(p.getId(), 0);
            List<PerfilHabilidad> habs = habsDe.getOrDefault(p.getId(), List.of()).stream()
                    .sorted(Comparator.comparing((PerfilHabilidad h) -> h.getValidadoPorId() == null)
                            .thenComparing(PerfilHabilidad::getNivel, Comparator.nullsLast(Comparator.reverseOrder())))
                    .toList();
            List<HabilidadChip> chips = habs.stream().limit(3)
                    .map(h -> new HabilidadChip(Optional.ofNullable(habPorId.get(h.getId().getHabilidadId())).map(Habilidad::getNombre).orElse("?"),
                            nivelCorto(h.getNivel()), h.getValidadoPorId() != null)).toList();
            String top = chips.stream().map(x -> x.nombre() + " " + x.nivel()).collect(Collectors.joining(", "));
            LocalDate libera = liberaDe.get(p.getId());
            todas.add(new Persona(p.getId(), p.getUsuario().getId(), conFoto.contains(p.getUsuario().getId()), nombre, InicialesUtil.de(nombre),
                    Optional.ofNullable(p.getCargo()).orElse("—"), c, Math.max(0, limite - c), oc.getEstadoLabel(), clave(oc.getEstadoLabel()),
                    oc.getEstadoBadgeClass(), proyectosDe.getOrDefault(p.getId(), List.of()), c == 0 ? diasBanca.get(p.getId()) : null,
                    libera != null ? libera.format(FECHA) : null, habs.size(),
                    (int) habs.stream().filter(h -> h.getValidadoPorId() == null).count(), top, chips, Math.max(0, habs.size() - 3)));
        }
        todas.sort(Comparator.comparingInt(Persona::carga).reversed().thenComparing(Persona::nombre));

        // Filtros de personas: todo menos el estado → base de la dona; con estado → tabla
        String q = normalizar(f.q());
        List<Persona> base = todas.stream()
                .filter(x -> q.isEmpty() || normalizar(x.nombre() + " " + x.cargo()).contains(q))
                .filter(x -> f.cargo() == null || f.cargo().equals(x.cargo()))
                .filter(x -> f.proyectoId() == null || proyectoIdsDe.getOrDefault(x.perfilId(), Set.of()).contains(f.proyectoId()))
                .filter(x -> x.carga() >= f.minimo())
                .toList();
        List<Persona> personas = base.stream().filter(x -> f.estados().isEmpty() || f.estados().contains(x.estadoClave())).toList();

        List<Segmento> distribucion = ESTADOS_CARGA.stream()
                .map(e -> new Segmento(e[0], e[1], (int) base.stream().filter(x -> e[0].equals(x.estadoClave())).count())).toList();

        // Carga promedio por cargo (de las personas filtradas)
        List<Barra> porCargo = personas.stream().collect(Collectors.groupingBy(Persona::cargo)).entrySet().stream()
                .map(e -> new Barra(e.getKey(), (int) Math.round(e.getValue().stream().mapToInt(Persona::carga).average().orElse(0)),
                        e.getValue().size(), e.getValue().size() + (e.getValue().size() == 1 ? " persona" : " personas")))
                .sorted(Comparator.comparingInt(Barra::valor).reversed().thenComparing(Barra::etiqueta)).toList();

        // ── Proyectos ────────────────────────────────────────────────────
        List<Proyecto> vigentes = proyectoRepository.findAll().stream()
                .filter(p -> VIGENTES.contains(p.getEstado()))
                .sorted(Comparator.comparing(Proyecto::getNombre, String.CASE_INSENSITIVE_ORDER)).toList();
        List<ProyectoFila> proyectos = new ArrayList<>();
        for (Proyecto p : vigentes) {
            if (f.proyectoId() != null && !f.proyectoId().equals(p.getId())) continue;
            ProyectoVistaService.Vista v = proyectoVistaService.vista(p.getId());
            int total = v.cobertura().size();
            int cobPct = total == 0 ? 100 : (int) Math.round(v.coberturaCompleta() * 100.0 / total);
            List<String> sinCubrir = v.cobertura().stream().filter(c -> !c.cubierta()).map(ProyectoVistaService.Cobertura::habilidad).toList();
            List<String> motivos = new ArrayList<>();
            int puntos = 0;
            boolean atrasado = p.getFechaFinEstimada() != null && p.getFechaFinEstimada().isBefore(hoy);
            if (atrasado) { puntos += 3; motivos.add("Fin estimado vencido"); }
            else if (p.getFechaFinEstimada() != null && !p.getFechaFinEstimada().isAfter(hoy.plusDays(14)) && "activo".equals(p.getEstado())) { puntos += 1; motivos.add("Vence en menos de 15 días"); }
            if (v.huecos() > 0) { puntos += 2; motivos.add(v.huecos() + " habilidad(es) que nadie del equipo tiene"); }
            if (v.vacantes() > 0 && "activo".equals(p.getEstado())) { puntos += 1; motivos.add(v.vacantes() + " vacante(s) en un proyecto activo"); }
            if (v.entregablesVencidos() > 0) { puntos += 1; motivos.add(v.entregablesVencidos() + " entregable(s) vencido(s)"); }
            if ("en_pausa".equals(p.getEstado())) motivos.add("En pausa");
            String riesgo = puntos >= 3 ? "Alto" : puntos >= 1 ? "Medio" : "Bajo";
            proyectos.add(new ProyectoFila(p.getId(), p.getNombre(), p.getEstado(), v.detalle().getEstadoLabel(), v.pmNombre(),
                    v.integrantes(), Optional.ofNullable(p.getColaboradoresRequeridos()).orElse(0), v.vacantes(), cobPct, sinCubrir,
                    v.detalle().getAvance(), p.getFechaFinEstimada() != null ? p.getFechaFinEstimada().format(FECHA) : "—",
                    v.plazoLabel(), v.plazoTono(), v.entregablesVencidos(), riesgo, motivos));
        }
        proyectos.sort(Comparator.comparingInt((ProyectoFila x) -> switch (x.riesgo()) { case "Alto" -> 0; case "Medio" -> 1; default -> 2; })
                .thenComparing(ProyectoFila::nombre));
        Map<String, Long> porEstado = proyectoRepository.findAll().stream().collect(Collectors.groupingBy(Proyecto::getEstado, Collectors.counting()));
        List<Segmento> estadosProyecto = List.of(
                new Segmento("planificacion", "Planificación", porEstado.getOrDefault("planificacion", 0L).intValue()),
                new Segmento("activo", "Activo", porEstado.getOrDefault("activo", 0L).intValue()),
                new Segmento("en_pausa", "En pausa", porEstado.getOrDefault("en_pausa", 0L).intValue()),
                new Segmento("completado", "Completado", porEstado.getOrDefault("completado", 0L).intValue()),
                new Segmento("cancelado", "Cancelado", porEstado.getOrDefault("cancelado", 0L).intValue()));

        // ── Habilidades: oferta contra demanda ───────────────────────────
        List<Proyecto> conDemanda = vigentes.stream().filter(p -> CON_DEMANDA.contains(p.getEstado()))
                .filter(p -> f.proyectoId() == null || f.proyectoId().equals(p.getId())).toList();
        Map<Long, Map<Long, Integer>> requisitos = demandaService.requisitosPorProyecto(conDemanda, catalogo);
        Map<Long, Integer> nivelPedido = new HashMap<>();
        Map<Long, List<String>> laPiden = new HashMap<>();
        for (Proyecto p : conDemanda) {
            requisitos.getOrDefault(p.getId(), Map.of()).forEach((hid, nivel) -> {
                nivelPedido.merge(hid, nivel, Math::max);
                laPiden.computeIfAbsent(hid, k -> new ArrayList<>()).add(p.getNombre());
            });
        }
        Set<Long> idsMiembros = miembros.stream().map(Perfil::getId).collect(Collectors.toSet());
        Map<Long, List<PerfilHabilidad>> declaradas = habsDe.values().stream().flatMap(List::stream)
                .filter(ph -> idsMiembros.contains(ph.getId().getPerfilId()))
                .collect(Collectors.groupingBy(ph -> ph.getId().getHabilidadId()));
        List<CoberturaHabilidadFila> coberturaTodas = new ArrayList<>();
        for (Map.Entry<Long, Integer> e : nivelPedido.entrySet()) {
            Habilidad h = habPorId.get(e.getKey());
            if (h == null) continue;
            if (f.categoriaId() != null && !f.categoriaId().equals(h.getCategoria().getId())) continue;
            int nivel = e.getValue();
            List<Long> calificadas = declaradas.getOrDefault(h.getId(), List.of()).stream()
                    .filter(ph -> ph.getNivel() != null && ph.getNivel() >= nivel).map(ph -> ph.getId().getPerfilId()).toList();
            int disponibles = (int) calificadas.stream().filter(id -> carga.getOrDefault(id, 0) < limite).count();
            coberturaTodas.add(new CoberturaHabilidadFila(h.getId(), h.getNombre(), h.getCategoria().getId(), h.getCategoria().getNombre(),
                    laPiden.get(h.getId()).size(), nivel, calificadas.size(), disponibles, laPiden.get(h.getId())));
        }
        coberturaTodas.sort(Comparator.comparingInt(CoberturaHabilidadFila::severidad)
                .thenComparing(Comparator.comparingInt(CoberturaHabilidadFila::proyectos).reversed())
                .thenComparing(CoberturaHabilidadFila::nombre, String.CASE_INSENSITIVE_ORDER));
        List<CoberturaHabilidadFila> habilidades = f.soloBrecha() ? coberturaTodas.stream().filter(CoberturaHabilidadFila::conBrecha).toList() : coberturaTodas;
        List<Segmento> cobertura = List.of(
                new Segmento("cubierta", CoberturaHabilidadFila.CUBIERTA, (int) coberturaTodas.stream().filter(c -> !c.conBrecha()).count()),
                new Segmento("ajustada", CoberturaHabilidadFila.AJUSTADA, (int) coberturaTodas.stream().filter(c -> CoberturaHabilidadFila.AJUSTADA.equals(c.estado())).count()),
                new Segmento("sin", CoberturaHabilidadFila.SIN_COBERTURA, (int) coberturaTodas.stream().filter(c -> CoberturaHabilidadFila.SIN_COBERTURA.equals(c.estado())).count()));
        List<Barra> brechas = coberturaTodas.stream().filter(CoberturaHabilidadFila::conBrecha)
                .map(c -> new Barra(c.nombre(), c.proyectos(), c.disponibles(),
                        c.calificadas() == 0 ? "Nadie al nivel " + c.nivelLabel().toLowerCase() : c.disponibles() + " con capacidad para " + c.proyectos() + " proyecto(s)"))
                .limit(8).toList();

        // ── Cursos publicados que cierran las brechas ───────────────────
        List<CursoSugerido> cursos = new ArrayList<>();
        List<Long> idsBrecha = coberturaTodas.stream().filter(CoberturaHabilidadFila::conBrecha).map(CoberturaHabilidadFila::habilidadId).toList();
        if (!idsBrecha.isEmpty()) {
            String in = idsBrecha.stream().map(String::valueOf).collect(Collectors.joining(","));
            jdbc.query("SELECT h.nombre, c.titulo, c.tipo, c.plataforma, c.url, c.gratuito FROM cursos c JOIN habilidades h ON h.id = c.habilidad_id "
                    + "WHERE c.estado = 'PUBLICADO' AND c.habilidad_id IN (" + in + ") ORDER BY c.destacado DESC, c.id DESC", rs -> {
                if (cursos.size() >= 6) return;
                String url = rs.getString(5);
                String plataforma = rs.getString(4) != null ? com.skillbridge.ai.util.PlataformaCurso.porClave(rs.getString(4)).nombre()
                        : com.skillbridge.ai.util.PlataformaCurso.detectar(url).nombre();
                cursos.add(new CursoSugerido(rs.getString(1), rs.getString(2), tipoCurso(rs.getString(3)), plataforma, url, rs.getBoolean(6)));
            });
        }

        // ── Decisiones (propuestas) y acierto de la IA ──────────────────
        List<PropuestaFila> propuestas = new ArrayList<>();
        int[] cont = new int[3];  // aprobadas, rechazadas, pendientes
        List<Long> minutosResolucion = new ArrayList<>();
        List<Integer> scoresAprobadas = new ArrayList<>();
        Map<String, Integer> origenes = new LinkedHashMap<>();
        jdbc.query("SELECT pa.fecha_solicitud, pr.nombre, uc.nombre_completo, us.nombre_completo, pa.origen, pa.score_total, "
                + "pa.dedicacion_porcentaje, pa.estado, pa.fecha_resolucion, pa.motivo_resolucion, pa.proyecto_id "
                + "FROM propuestas_asignacion pa JOIN proyectos pr ON pr.id = pa.proyecto_id "
                + "JOIN perfiles pc ON pc.id = pa.candidato_perfil_id JOIN usuarios uc ON uc.id = pc.usuario_id "
                + "JOIN perfiles ps ON ps.id = pa.solicitado_por_perfil_id JOIN usuarios us ON us.id = ps.usuario_id "
                + "ORDER BY pa.fecha_solicitud DESC", rs -> {
            if (f.proyectoId() != null && f.proyectoId() != rs.getLong(11)) return;
            String estado = rs.getString(8);
            Timestamp sol = rs.getTimestamp(1), res = rs.getTimestamp(9);
            switch (estado) { case "aprobada" -> cont[0]++; case "rechazada" -> cont[1]++; default -> cont[2]++; }
            if (sol != null && res != null) minutosResolucion.add(Duration.between(sol.toLocalDateTime(), res.toLocalDateTime()).toMinutes());
            if ("aprobada".equals(estado)) scoresAprobadas.add(rs.getInt(6));
            String origen = origenLabel(rs.getString(5));
            origenes.merge(origen, 1, Integer::sum);
            propuestas.add(new PropuestaFila(sol != null ? sol.toLocalDateTime().format(FECHA) : "—", rs.getString(2), rs.getString(3),
                    rs.getString(4), origen, rs.getInt(6), rs.getInt(7), estadoPropuesta(estado),
                    res != null ? res.toLocalDateTime().format(FECHA) : "—", Optional.ofNullable(rs.getString(10)).orElse("")));
        });
        int resueltas = cont[0] + cont[1];
        int tasa = resueltas == 0 ? 0 : (int) Math.round(cont[0] * 100.0 / resueltas);
        Decisiones decisiones = new Decisiones(propuestas.size(), cont[0], cont[1], cont[2], tasa,
                duracion(minutosResolucion.isEmpty() ? null : (long) minutosResolucion.stream().mapToLong(Long::longValue).average().orElse(0)),
                (int) Math.round(scoresAprobadas.stream().mapToInt(Integer::intValue).average().orElse(0)),
                origenes.entrySet().stream().map(e -> new Segmento(normalizar(e.getKey()).replace(' ', '_'), e.getKey(), e.getValue())).toList(),
                List.of(new Segmento("aprobada", "Aprobadas", cont[0]), new Segmento("rechazada", "Rechazadas", cont[1]),
                        new Segmento("pendiente", "Pendientes", cont[2])),
                recomendacionRepository.contarRecomendacionesUnicas(), recomendacionRepository.contarAciertos());

        // ── Entregas: cumplimiento ───────────────────────────────────────
        Entregas entregas = jdbc.queryForObject("SELECT "
                + "(SELECT COUNT(*) FROM entregables e WHERE e.estado <> 'cancelado'" + (f.proyectoId() != null ? " AND e.proyecto_id = " + f.proyectoId() : "") + "), "
                + "COUNT(en.id), SUM(CASE WHEN en.fecha_entrega <= e.fecha_cierre THEN 1 ELSE 0 END), "
                + "SUM(CASE WHEN en.estado = 'revisado' THEN 1 ELSE 0 END), "
                + "AVG(CASE WHEN en.calificacion IS NOT NULL AND e.puntaje_maximo > 0 THEN en.calificacion * 100 / e.puntaje_maximo END), "
                + "(SELECT COUNT(*) FROM entregables e2 WHERE e2.estado = 'activo' AND e2.fecha_cierre < NOW()"
                + (f.proyectoId() != null ? " AND e2.proyecto_id = " + f.proyectoId() : "") + ") "
                + "FROM entregas en JOIN entregables e ON e.id = en.entregable_id WHERE e.estado <> 'cancelado'"
                + (f.proyectoId() != null ? " AND e.proyecto_id = " + f.proyectoId() : ""), (rs, i) -> {
            int total = rs.getInt(2), aTiempo = rs.getInt(3);
            return new Entregas(rs.getInt(1), total, aTiempo, total == 0 ? 0 : (int) Math.round(aTiempo * 100.0 / total),
                    rs.getInt(4), (int) Math.round(rs.getDouble(5)), rs.getInt(6));
        });

        // ── Próximos días ────────────────────────────────────────────────
        Set<Long> visibles = personas.stream().map(Persona::perfilId).collect(Collectors.toSet());
        List<Liberacion> liberaciones = panel.liberados().stream().filter(l -> visibles.contains(l.perfilId()))
                .map(l -> new Liberacion(l.nombre(), l.proyecto(), l.fechaLabel(), l.diasRestantes(), l.cargaQueLibera())).toList();
        List<Riesgo> enRiesgo = panel.prediccion().enRiesgo().stream().filter(r -> visibles.contains(r.perfilId()))
                .map(r -> new Riesgo(r.nombre(), r.cargaFutura())).toList();

        // ── KPIs (sobre las personas filtradas) ──────────────────────────
        List<Persona> conAsig = personas.stream().filter(x -> x.carga() > 0).toList();
        int promedio = (int) Math.round(conAsig.stream().mapToInt(Persona::carga).average().orElse(0));
        int libreTotal = personas.stream().mapToInt(Persona::libre).sum();
        int vacantes = proyectos.stream().filter(p -> CON_DEMANDA.contains(p.estado())).mapToInt(ProyectoFila::vacantes).sum();
        int conBrecha = (int) coberturaTodas.stream().filter(CoberturaHabilidadFila::conBrecha).count();
        int cobPct = coberturaTodas.isEmpty() ? 100 : (int) Math.round((coberturaTodas.size() - conBrecha) * 100.0 / coberturaTodas.size());
        Kpis kpis = new Kpis(promedio, libreTotal, fte(libreTotal), (int) personas.stream().filter(x -> "banca".equals(x.estadoClave())).count(),
                (int) personas.stream().filter(x -> "sobre".equals(x.estadoClave())).count(),
                (int) personas.stream().filter(x -> "lim".equals(x.estadoClave())).count(),
                vacantes, (int) proyectos.stream().filter(p -> CON_DEMANDA.contains(p.estado())).count(), cobPct, conBrecha,
                coberturaTodas.size(), cont[2], tasa, entregas.aTiempoPct());

        List<Hallazgo> hallazgos = hallazgos(kpis, personas, proyectos, coberturaTodas, decisiones, liberaciones, enRiesgo, panel, entregas);

        Opciones opciones = new Opciones(
                todas.stream().map(Persona::cargo).filter(c -> !"—".equals(c)).distinct().sorted().toList(),
                vigentes.stream().map(p -> new Opcion(String.valueOf(p.getId()), p.getNombre())).toList(),
                catalogo.stream().map(Habilidad::getCategoria).filter(Objects::nonNull).collect(Collectors.toMap(CategoriaHabilidad::getId, c -> c, (a, b) -> a, LinkedHashMap::new))
                        .values().stream().sorted(Comparator.comparing(CategoriaHabilidad::getNombre))
                        .map(c -> new Opcion(String.valueOf(c.getId()), c.getNombre())).toList());

        return new ReporteRm(LocalDateTime.now(ZONA).format(FECHA_HORA), describir(f, opciones), f.vacios(), limite, kpis, hallazgos,
                distribucion, estadosProyecto, cobertura, porCargo, brechas, personas, base.size(), proyectos, habilidades,
                decisiones, propuestas, entregas, liberaciones, enRiesgo, cursos, opciones);
    }

    // ───────────────────────── Hallazgos ─────────────────────────

    private List<Hallazgo> hallazgos(Kpis k, List<Persona> personas, List<ProyectoFila> proyectos, List<CoberturaHabilidadFila> cobertura,
                                     Decisiones d, List<Liberacion> liberaciones, List<Riesgo> riesgo, PanelRm panel, Entregas e) {
        List<Hallazgo> h = new ArrayList<>();
        List<String> sobre = personas.stream().filter(x -> "sobre".equals(x.estadoClave())).map(Persona::nombre).toList();
        if (!sobre.isEmpty()) h.add(new Hallazgo("red", "icon-alert-triangle", sobre.size() + " persona(s) sobre el límite de carga",
                lista(sobre) + ". Conviene redistribuir su dedicación antes de asignarles algo nuevo."));
        List<CoberturaHabilidadFila> sin = cobertura.stream().filter(c -> CoberturaHabilidadFila.SIN_COBERTURA.equals(c.estado())).toList();
        if (!sin.isEmpty()) {
            Set<String> afectados = sin.stream().flatMap(c -> c.proyectosNombres().stream()).collect(Collectors.toCollection(TreeSet::new));
            h.add(new Hallazgo("red", "icon-target", sin.size() + " habilidad(es) que nadie del equipo tiene al nivel pedido",
                    lista(sin.stream().map(CoberturaHabilidadFila::nombre).toList()) + ". Afectan a " + lista(new ArrayList<>(afectados))
                            + ": capacitación (Cursos) o contratación."));
        }
        List<ProyectoFila> altos = proyectos.stream().filter(p -> "Alto".equals(p.riesgo())).toList();
        if (!altos.isEmpty()) h.add(new Hallazgo("amber", "icon-folder", altos.size() + " proyecto(s) en riesgo alto",
                altos.stream().map(p -> p.nombre() + " (" + String.join(", ", p.motivos()).toLowerCase() + ")").collect(Collectors.joining("; ")) + "."));
        if (k.vacantes() > 0) {
            double fte = k.capacidadLibre() / 100.0;
            h.add(new Hallazgo(fte >= k.vacantes() * 0.5 ? "blue" : "amber", "icon-users",
                    k.vacantes() + " vacante(s) abiertas y " + k.capacidadLibreFte() + " persona(s) a tiempo completo de capacidad libre",
                    fte >= k.vacantes() * 0.5 ? "Hay capacidad para cubrir buena parte de la demanda con el equipo actual: revisa AI Talent Matching."
                            : "La capacidad libre no alcanza para las vacantes: prioriza proyectos o planifica contrataciones."));
        }
        panel.disponibles().stream().filter(x -> x.diasEnBanca() != null && x.diasEnBanca() > 21)
                .filter(x -> personas.stream().anyMatch(p -> p.perfilId().equals(x.perfilId())))
                .max(Comparator.comparingInt(PanelRm.PersonaDisponible::diasEnBanca))
                .ifPresent(x -> h.add(new Hallazgo("amber", "icon-clock", x.nombre() + " lleva " + x.diasEnBanca() + " días sin proyecto",
                        x.sugerenciaProyecto() != null ? "Encaja en " + x.sugerenciaProyecto() + " por " + lista(x.sugerenciaHabilidades()) + "."
                                : "Ningún proyecto con vacantes pide sus habilidades: buen momento para capacitación.")));
        if (!riesgo.isEmpty()) h.add(new Hallazgo("red", "icon-trending-up", riesgo.size() + " persona(s) superarían el límite en " + panel.prediccion().dias() + " días",
                riesgo.stream().map(r -> r.nombre() + " (" + r.cargaFutura() + "%)").collect(Collectors.joining(", ")) + ", según las asignaciones ya programadas."));
        if (!liberaciones.isEmpty()) h.add(new Hallazgo("blue", "icon-calendar", liberaciones.size() + " liberación(es) en los próximos 30 días",
                liberaciones.stream().limit(3).map(l -> l.nombre() + " deja " + l.proyecto() + " el " + l.fecha()).collect(Collectors.joining("; ")) + "."));
        if (d.pendientes() > 0) h.add(new Hallazgo("violet", "icon-sparkles", d.pendientes() + " propuesta(s) de asignación esperan tu revisión",
                "Tasa de aprobación histórica: " + d.tasaAprobacion() + "%" + (d.tiempoMedioResolucion() != null ? " · se resuelven en " + d.tiempoMedioResolucion() + " en promedio." : ".")));
        if (d.iaRecomendaciones() > 0) h.add(new Hallazgo("violet", "icon-sparkles", "La IA acertó en " + d.iaAciertoPct() + "% de sus recomendaciones",
                d.iaAciertos() + " de " + d.iaRecomendaciones() + " personas recomendadas por AI Talent Matching terminaron asignadas."));
        if (e.entregas() > 0) h.add(new Hallazgo(e.aTiempoPct() >= 80 ? "green" : "amber", "icon-check-circle", e.aTiempoPct() + "% de las entregas llegaron a tiempo",
                e.entregas() + " entregas registradas" + (e.calificacionPromedioPct() > 0 ? ", con calificación promedio de " + e.calificacionPromedioPct() + "%" : "")
                        + (e.vencidosSinEntrega() > 0 ? "; " + e.vencidosSinEntrega() + " entregable(s) siguen abiertos con la fecha vencida." : ".")));
        if (h.isEmpty()) h.add(new Hallazgo("green", "icon-check-circle", "Sin alertas", "El equipo está dentro del límite y las habilidades pedidas están cubiertas."));
        return h.size() > 6 ? h.subList(0, 6) : h;
    }

    // ───────────────────────── utilidades ─────────────────────────

    private static String describir(Filtros f, Opciones o) {
        if (f.vacios()) return "Sin filtros: todo el equipo activo, todos los proyectos vigentes y todas las habilidades pedidas";
        List<String> partes = new ArrayList<>();
        if (!f.q().isEmpty()) partes.add("búsqueda \"" + f.q() + "\"");
        if (!f.estados().isEmpty()) partes.add("estado de carga: " + f.estados().stream().map(RmReporteService::etiquetaEstado).collect(Collectors.joining(", ")));
        if (f.cargo() != null) partes.add("cargo: " + f.cargo());
        if (f.proyectoId() != null) partes.add("proyecto: " + o.proyectos().stream().filter(p -> p.valor().equals(String.valueOf(f.proyectoId()))).map(Opcion::etiqueta).findFirst().orElse("#" + f.proyectoId()));
        if (f.categoriaId() != null) partes.add("categoría: " + o.categorias().stream().filter(c -> c.valor().equals(String.valueOf(f.categoriaId()))).map(Opcion::etiqueta).findFirst().orElse("#" + f.categoriaId()));
        if (f.minimo() > 0) partes.add("ocupación desde " + f.minimo() + "%");
        if (f.soloBrecha()) partes.add("solo habilidades con brecha");
        return "Filtros: " + String.join("; ", partes);
    }

    public static String etiquetaEstado(String clave) {
        return ESTADOS_CARGA.stream().filter(e -> e[0].equals(clave)).map(e -> e[1]).findFirst().orElse(clave);
    }

    private static String clave(String etiqueta) {
        return ESTADOS_CARGA.stream().filter(e -> e[1].equals(etiqueta)).map(e -> e[0]).findFirst().orElse("sub");
    }

    private static String fte(int porcentaje) {
        double v = Math.round(porcentaje / 10.0) / 10.0;
        return (v == Math.floor(v) ? String.valueOf((long) v) : String.valueOf(v)).replace('.', ',');
    }

    private static String duracion(Long minutos) {
        if (minutos == null) return null;
        if (minutos < 60) return Math.max(1, minutos) + " min";
        if (minutos < 48 * 60) return Math.round(minutos / 60.0) + " h";
        return Math.round(minutos / 1440.0) + " días";
    }

    private static String tipoCurso(String t) {
        if (t == null) return "Curso";
        return switch (t) { case "VIDEO" -> "Video"; case "RUTA" -> "Ruta"; case "ESPECIALIZACION" -> "Especialización"; default -> "Curso"; };
    }

    private static String origenLabel(String o) {
        if ("manual".equals(o)) return "Manual";
        if ("reincorporacion".equals(o)) return "Reincorporación";
        return "AI Talent Matching";
    }

    private static String estadoPropuesta(String e) {
        return switch (e) { case "aprobada" -> "Aprobada"; case "rechazada" -> "Rechazada"; default -> "Pendiente"; };
    }

    private static String nivelCorto(Integer n) {
        if (n == null) return "";
        return switch (n) { case 1, 2 -> "básico"; case 3 -> "intermedio"; case 4 -> "avanzado"; case 5 -> "experto"; default -> ""; };
    }

    private static String lista(List<String> xs) {
        if (xs.isEmpty()) return "";
        if (xs.size() == 1) return xs.get(0);
        List<String> l = xs.size() > 4 ? new ArrayList<>(xs.subList(0, 4)) : new ArrayList<>(xs);
        String fin = xs.size() > 4 ? " y " + (xs.size() - 4) + " más" : " y " + l.remove(l.size() - 1);
        return String.join(", ", l) + fin;
    }

    static String normalizar(String v) {
        return Normalizer.normalize(v == null ? "" : v, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase().trim();
    }
}
