package com.skillbridge.ai.service;

import com.skillbridge.ai.dto.PanelRm;
import com.skillbridge.ai.dto.PanelRm.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Datos del Inicio del Resource Manager: disponibilidad ("la banca"),
 * tendencia de ocupación, predicción de sobrecarga, balance de carga,
 * liberaciones próximas, solicitudes de PMs, proyectos por iniciar, línea de
 * tiempo y demanda vs. oferta de habilidades.
 *
 * <p>Solo LECTURA: 6 consultas SQL parametrizadas (JdbcTemplate, sin
 * concatenar datos del usuario) y el resto se calcula en memoria. Usa el mismo
 * criterio de "carga efectiva" del modelo v7 que AsignacionRepository:
 * asignaciones activas en proyectos planificacion / activo / en_pausa.
 *
 * <p>Cada bloque se calcula por separado: si uno falla (p.ej. una tabla que
 * aún no existe en otra BD), ese bloque sale vacío y el resto de la página
 * se muestra igual.
 */
@Service
public class RmPanelService {

    private static final Logger log = LoggerFactory.getLogger(RmPanelService.class);

    /** Menos de este % de carga = "disponible" (capacidad libre). */
    public static final int UMBRAL_DISPONIBLE = 30;
    /** Banda de carga saludable. */
    public static final int OPTIMO_MIN = 70, OPTIMO_MAX = 85;
    private static final int DIAS_PREDICCION = 14;
    private static final int DIAS_LIBERACION = 30;
    private static final int SEMANAS_TENDENCIA = 6;
    private static final int GANTT_DIAS_ANTES = 21, GANTT_DIAS_TOTAL = 90;

    private static final Set<String> VIGENTES = Set.of("planificacion", "activo", "en_pausa");
    private static final Set<String> ABIERTOS = Set.of("planificacion", "activo");
    private static final Locale ES = Locale.forLanguageTag("es");
    private static final DateTimeFormatter DIA_MES = DateTimeFormatter.ofPattern("d MMM", ES);

    private final JdbcTemplate jdbc;
    private final ProyectoService proyectoService;
    private final ReporteService reporteService;

    public RmPanelService(JdbcTemplate jdbc, ProyectoService proyectoService, ReporteService reporteService) {
        this.jdbc = jdbc;
        this.proyectoService = proyectoService;
        this.reporteService = reporteService;
    }

    // ───────────────────────── Datos base ─────────────────────────

    private record Miembro(Long id, String nombre, String cargo, LocalDate creado) { }

    private record Asig(Long perfilId, Long proyectoId, String rol, int carga, String estado, LocalDate inicio,
                        LocalDate fin, String proyectoEstado, LocalDate proyectoFin) {
        boolean cuentaHoy() { return "activa".equals(estado) && VIGENTES.contains(proyectoEstado); }
    }

    private record Proy(Long id, String nombre, String estado, int requeridos, LocalDate inicio, LocalDate finEstimado) { }

    private record HabPerfil(Long perfilId, Long habilidadId, String nombre, int nivel) { }

    private record HabReq(Long proyectoId, Long habilidadId, String nombre, int nivel) { }

    @Transactional(readOnly = true)
    public PanelRm construir() {
        LocalDate hoy = LocalDate.now();
        int limite = limiteSeguro();

        List<Miembro> miembros = cargar("miembros", () -> jdbc.query(
                "SELECT p.id, u.nombre_completo, p.cargo, p.fecha_creacion FROM perfiles p " +
                "JOIN usuarios u ON u.id = p.usuario_id " +
                "WHERE u.rol_organizacional IS NULL AND u.estado = 'activo' AND p.estado = 'activo' " +
                "ORDER BY u.nombre_completo",
                (rs, i) -> new Miembro(rs.getLong(1), rs.getString(2), rs.getString(3),
                        fecha(rs.getObject(4, LocalDateTime.class)))));

        List<Asig> asignaciones = cargar("asignaciones", () -> jdbc.query(
                "SELECT a.perfil_id, a.proyecto_id, a.rol_en_proyecto, a.carga_porcentaje, a.estado, " +
                "a.fecha_inicio, a.fecha_fin, pr.estado, pr.fecha_fin_estimada " +
                "FROM asignaciones a JOIN proyectos pr ON pr.id = a.proyecto_id WHERE a.estado <> ?",
                (rs, i) -> new Asig(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getInt(4), rs.getString(5),
                        rs.getObject(6, LocalDate.class), rs.getObject(7, LocalDate.class), rs.getString(8),
                        rs.getObject(9, LocalDate.class)),
                "cancelada"));

        List<Proy> proyectos = cargar("proyectos", () -> jdbc.query(
                "SELECT id, nombre, estado, colaboradores_requeridos, fecha_inicio, fecha_fin_estimada " +
                "FROM proyectos WHERE estado IN ('planificacion', 'activo', 'en_pausa')",
                (rs, i) -> new Proy(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getInt(4),
                        rs.getObject(5, LocalDate.class), rs.getObject(6, LocalDate.class))));

        List<HabPerfil> habPerfil = cargar("habilidades de perfil", () -> jdbc.query(
                "SELECT ph.perfil_id, h.id, h.nombre, ph.nivel FROM perfil_habilidad ph " +
                "JOIN habilidades h ON h.id = ph.habilidad_id",
                (rs, i) -> new HabPerfil(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getInt(4))));

        List<HabReq> habReq = cargar("habilidades requeridas", () -> jdbc.query(
                "SELECT r.proyecto_id, h.id, h.nombre, r.nivel_requerido FROM proyecto_habilidad_requerida r " +
                "JOIN habilidades h ON h.id = r.habilidad_id JOIN proyectos p ON p.id = r.proyecto_id " +
                "WHERE p.estado IN ('planificacion', 'activo')",
                (rs, i) -> new HabReq(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getInt(4))));

        // Carga efectiva actual por perfil (mismo criterio que AsignacionRepository)
        Map<Long, Integer> carga = new HashMap<>();
        for (Asig a : asignaciones) {
            if (a.cuentaHoy()) carga.merge(a.perfilId(), a.carga(), Integer::sum);
        }
        // Colaboradores activos por proyecto -> vacantes
        Map<Long, Integer> colaboradoresPorProyecto = new HashMap<>();
        for (Asig a : asignaciones) {
            if ("activa".equals(a.estado()) && "colaborador".equals(a.rol())) {
                colaboradoresPorProyecto.merge(a.proyectoId(), 1, Integer::sum);
            }
        }
        Map<Long, List<HabPerfil>> habilidadesDe = habPerfil.stream().collect(Collectors.groupingBy(HabPerfil::perfilId));
        Map<Long, List<HabReq>> requeridasDe = habReq.stream().collect(Collectors.groupingBy(HabReq::proyectoId));

        Tendencia tendencia = seguro("tendencia", () -> tendencia(hoy, asignaciones, miembros),
                new Tendencia(List.of(), 0, 0, "", ""));
        Prediccion prediccion = seguro("prediccion", () -> prediccion(hoy, limite, asignaciones, miembros),
                new Prediccion(DIAS_PREDICCION, List.of()));
        Balance balance = seguro("balance", () -> balance(miembros, carga), new Balance(0, 0, 0, 0, 0, 0, 0));

        List<Miembro> disponiblesTodos = miembros.stream()
                .filter(m -> carga.getOrDefault(m.id(), 0) < UMBRAL_DISPONIBLE).toList();
        List<PersonaDisponible> disponibles = seguro("disponibles", () -> disponibles(hoy, limite, disponiblesTodos,
                carga, asignaciones, proyectos, colaboradoresPorProyecto, habilidadesDe, requeridasDe), List.of());
        long enBanca = disponiblesTodos.stream().filter(m -> carga.getOrDefault(m.id(), 0) == 0).count();
        int capacidadLibre = disponiblesTodos.stream().mapToInt(m -> Math.max(0, limite - carga.getOrDefault(m.id(), 0))).sum();

        List<Liberacion> liberados = seguro("liberados", () -> liberados(hoy, miembros, carga, asignaciones, proyectos), List.of());
        List<Solicitud> solicitudesTodas = seguro("solicitudes", () -> solicitudes(limite, carga), List.of());
        List<ProyectoProximo> porIniciar = seguro("por iniciar", () -> porIniciar(hoy, proyectos, colaboradoresPorProyecto, requeridasDe), List.of());
        Gantt gantt = seguro("linea de tiempo", () -> gantt(hoy, proyectos, asignaciones, colaboradoresPorProyecto),
                new Gantt(List.of(), List.of(), 0, ""));
        List<HabilidadDemanda> habilidades = seguro("habilidades", () -> habilidades(limite, miembros, carga, habPerfil,
                habReq, proyectos, colaboradoresPorProyecto), List.of());

        return new PanelRm(tendencia, prediccion, balance, disponibles, enBanca, capacidadLibre, liberados,
                solicitudesTodas.stream().limit(5).toList(), solicitudesTodas.size(), porIniciar, gantt, habilidades, limite);
    }

    // ───────────────────────── KPIs ─────────────────────────

    /**
     * Ocupación promedio semana a semana, reconstruida con las fechas de inicio
     * y fin de cada asignación (no hay una tabla de histórico). El último punto
     * es exactamente el KPI actual (ReporteService.ocupacionPromedio).
     */
    private Tendencia tendencia(LocalDate hoy, List<Asig> asignaciones, List<Miembro> miembros) {
        Set<Long> ids = miembros.stream().map(Miembro::id).collect(Collectors.toSet());
        List<Integer> semanas = new ArrayList<>();
        for (int k = SEMANAS_TENDENCIA - 1; k >= 1; k--) {
            LocalDate dia = hoy.minusWeeks(k);
            Map<Long, Integer> cargaDia = new HashMap<>();
            for (Asig a : asignaciones) {
                if (ids.contains(a.perfilId()) && vigenteEn(a, dia)) cargaDia.merge(a.perfilId(), a.carga(), Integer::sum);
            }
            semanas.add((int) Math.round(cargaDia.values().stream().mapToInt(Integer::intValue).average().orElse(0)));
        }
        int actual = reporteService.ocupacionPromedio();
        semanas.add(actual);
        int anterior = semanas.size() >= 2 ? semanas.get(semanas.size() - 2) : actual;

        int max = Math.max(100, semanas.stream().mapToInt(Integer::intValue).max().orElse(100));
        StringBuilder puntos = new StringBuilder();
        for (int i = 0; i < semanas.size(); i++) {
            double x = semanas.size() == 1 ? 0 : i * 100.0 / (semanas.size() - 1);
            double y = 30 - (semanas.get(i) * 26.0 / max);
            if (i > 0) puntos.append(' ');
            puntos.append(String.format(Locale.ROOT, "%.1f,%.1f", x, y));
        }
        String area = puntos + " 100,32 0,32";
        return new Tendencia(semanas, actual, actual - anterior, puntos.toString(), area);
    }

    /** ¿La asignación ocupaba capacidad ese día? */
    private static boolean vigenteEn(Asig a, LocalDate dia) {
        if (a.inicio() == null || a.inicio().isAfter(dia)) return false;
        LocalDate fin = a.fin();
        if (fin == null && !VIGENTES.contains(a.proyectoEstado())) fin = a.proyectoFin();   // proyecto ya cerrado
        if (fin == null && !VIGENTES.contains(a.proyectoEstado())) return false;
        return fin == null || !fin.isBefore(dia);
    }

    /** Carga dentro de N días con las asignaciones ya programadas (inicios futuros y fines previstos). */
    private Prediccion prediccion(LocalDate hoy, int limite, List<Asig> asignaciones, List<Miembro> miembros) {
        LocalDate dia = hoy.plusDays(DIAS_PREDICCION);
        Map<Long, Integer> cargaFutura = new HashMap<>();
        for (Asig a : asignaciones) {
            if (!a.cuentaHoy() || a.inicio() == null || a.inicio().isAfter(dia)) continue;
            if (a.fin() != null && a.fin().isBefore(dia)) continue;
            if (a.proyectoFin() != null && a.proyectoFin().isBefore(dia)) continue;
            cargaFutura.merge(a.perfilId(), a.carga(), Integer::sum);
        }
        List<PersonaRiesgo> riesgo = miembros.stream()
                .filter(m -> cargaFutura.getOrDefault(m.id(), 0) > limite)
                .map(m -> new PersonaRiesgo(m.id(), m.nombre(), cargaFutura.get(m.id())))
                .sorted(Comparator.comparingInt(PersonaRiesgo::cargaFutura).reversed())
                .toList();
        return new Prediccion(DIAS_PREDICCION, riesgo);
    }

    private Balance balance(List<Miembro> miembros, Map<Long, Integer> carga) {
        int sin = 0, baja = 0, media = 0, optimo = 0, alta = 0, sobre = 0;
        for (Miembro m : miembros) {
            int c = carga.getOrDefault(m.id(), 0);
            if (c == 0) sin++;
            else if (c < UMBRAL_DISPONIBLE) baja++;
            else if (c < OPTIMO_MIN) media++;
            else if (c <= OPTIMO_MAX) optimo++;
            else if (c <= 100) alta++;
            else sobre++;
        }
        return new Balance(miembros.size(), sin, baja, media, optimo, alta, sobre);
    }

    // ───────────────────────── La banca ─────────────────────────

    private List<PersonaDisponible> disponibles(LocalDate hoy, int limite, List<Miembro> candidatos, Map<Long, Integer> carga,
                                                List<Asig> asignaciones, List<Proy> proyectos,
                                                Map<Long, Integer> colaboradoresPorProyecto,
                                                Map<Long, List<HabPerfil>> habilidadesDe,
                                                Map<Long, List<HabReq>> requeridasDe) {
        Map<Long, Set<Long>> proyectosActivosDe = new HashMap<>();
        Map<Long, LocalDate> ultimoFin = new HashMap<>();
        for (Asig a : asignaciones) {
            if ("activa".equals(a.estado())) proyectosActivosDe.computeIfAbsent(a.perfilId(), k -> new HashSet<>()).add(a.proyectoId());
            LocalDate fin = a.fin() != null ? a.fin() : (!VIGENTES.contains(a.proyectoEstado()) ? a.proyectoFin() : null);
            if (fin != null) ultimoFin.merge(a.perfilId(), fin, (x, y) -> x.isAfter(y) ? x : y);
        }
        List<Proy> conVacantes = proyectos.stream()
                .filter(p -> ABIERTOS.contains(p.estado()))
                .filter(p -> p.requeridos() - colaboradoresPorProyecto.getOrDefault(p.id(), 0) > 0)
                .sorted(Comparator.comparing(Proy::inicio, Comparator.nullsLast(Comparator.<LocalDate>naturalOrder())))
                .toList();

        List<PersonaDisponible> lista = new ArrayList<>();
        for (Miembro m : candidatos) {
            int c = carga.getOrDefault(m.id(), 0);
            List<HabPerfil> suyas = habilidadesDe.getOrDefault(m.id(), List.of());
            List<HabilidadNivel> top = suyas.stream()
                    .sorted(Comparator.comparingInt(HabPerfil::nivel).reversed().thenComparing(HabPerfil::nombre))
                    .limit(3).map(h -> new HabilidadNivel(h.nombre(), h.nivel())).toList();

            Integer dias = null;
            if (c == 0) {
                LocalDate desde = ultimoFin.getOrDefault(m.id(), m.creado());
                dias = desde == null ? null : (int) Math.max(0, ChronoUnit.DAYS.between(desde, hoy));
            }

            // Sugerencia: el proyecto con vacantes donde más habilidades requeridas cumple
            String sugerencia = null;
            List<String> coinciden = List.of();
            Map<Long, Integer> nivelDe = suyas.stream().collect(Collectors.toMap(HabPerfil::habilidadId, HabPerfil::nivel, Math::max));
            Set<Long> yaEsta = proyectosActivosDe.getOrDefault(m.id(), Set.of());
            int mejor = 0;
            for (Proy p : conVacantes) {
                if (yaEsta.contains(p.id())) continue;
                List<String> match = requeridasDe.getOrDefault(p.id(), List.of()).stream()
                        .filter(r -> nivelDe.getOrDefault(r.habilidadId(), 0) >= r.nivel())
                        .map(HabReq::nombre).toList();
                if (match.size() > mejor) {
                    mejor = match.size();
                    sugerencia = p.nombre();
                    coinciden = match;
                }
            }
            lista.add(new PersonaDisponible(m.id(), m.nombre(), iniciales(m.nombre()), m.cargo(), c,
                    Math.max(0, limite - c), top, dias, sugerencia, coinciden));
        }
        lista.sort(Comparator.comparingInt(PersonaDisponible::carga)
                .thenComparing(PersonaDisponible::diasEnBanca, Comparator.nullsLast(Comparator.<Integer>reverseOrder()))
                .thenComparing(PersonaDisponible::nombre));
        return lista.size() > 12 ? lista.subList(0, 12) : lista;
    }

    /** Asignaciones vigentes que terminan en los próximos 30 días (fin propio o fin estimado del proyecto). */
    private List<Liberacion> liberados(LocalDate hoy, List<Miembro> miembros, Map<Long, Integer> carga,
                                       List<Asig> asignaciones, List<Proy> proyectos) {
        Map<Long, Miembro> porId = miembros.stream().collect(Collectors.toMap(Miembro::id, m -> m));
        Map<Long, String> nombreProyecto = proyectos.stream().collect(Collectors.toMap(Proy::id, Proy::nombre));
        LocalDate limiteFecha = hoy.plusDays(DIAS_LIBERACION);
        List<Liberacion> lista = new ArrayList<>();
        for (Asig a : asignaciones) {
            if (!a.cuentaHoy() || !porId.containsKey(a.perfilId())) continue;
            LocalDate fin = a.fin() != null ? a.fin() : a.proyectoFin();
            if (fin == null || fin.isBefore(hoy) || fin.isAfter(limiteFecha)) continue;
            Miembro m = porId.get(a.perfilId());
            lista.add(new Liberacion(m.id(), m.nombre(), iniciales(m.nombre()),
                    nombreProyecto.getOrDefault(a.proyectoId(), "Proyecto"), diaMes(fin),
                    ChronoUnit.DAYS.between(hoy, fin), a.carga(), carga.getOrDefault(m.id(), 0)));
        }
        lista.sort(Comparator.comparingLong(Liberacion::diasRestantes).thenComparing(Liberacion::nombre));
        return lista.size() > 8 ? lista.subList(0, 8) : lista;
    }

    // ───────────────────────── Solicitudes y proyectos ─────────────────────────

    private List<Solicitud> solicitudes(int limite, Map<Long, Integer> carga) {
        return jdbc.query(
                "SELECT pa.id, us.nombre_completo, uc.nombre_completo, pa.candidato_perfil_id, pr.nombre, " +
                "pa.dedicacion_porcentaje, pa.score_total, pa.fecha_solicitud, pa.origen " +
                "FROM propuestas_asignacion pa " +
                "JOIN proyectos pr ON pr.id = pa.proyecto_id " +
                "JOIN perfiles pc ON pc.id = pa.candidato_perfil_id JOIN usuarios uc ON uc.id = pc.usuario_id " +
                "LEFT JOIN perfiles ps ON ps.id = pa.solicitado_por_perfil_id " +
                "LEFT JOIN usuarios us ON us.id = ps.usuario_id " +
                "WHERE pa.estado = ? ORDER BY pa.fecha_solicitud ASC",
                (rs, i) -> {
                    long candidato = rs.getLong(4);
                    int dedicacion = rs.getInt(6);
                    Object score = rs.getObject(7);
                    LocalDateTime fecha = rs.getObject(8, LocalDateTime.class);
                    String candidatoNombre = rs.getString(3);
                    return new Solicitud(rs.getLong(1), rs.getString(2) != null ? rs.getString(2) : "Project Manager",
                            candidatoNombre, iniciales(candidatoNombre), rs.getString(5), dedicacion,
                            score instanceof Number n ? n.intValue() : null,
                            fecha != null ? diaMes(fecha.toLocalDate()) : "", rs.getString(9),
                            carga.getOrDefault(candidato, 0) + dedicacion > limite);
                },
                "pendiente");
    }

    private List<ProyectoProximo> porIniciar(LocalDate hoy, List<Proy> proyectos, Map<Long, Integer> colaboradores,
                                             Map<Long, List<HabReq>> requeridasDe) {
        return proyectos.stream()
                .filter(p -> "planificacion".equals(p.estado()) || (p.inicio() != null && p.inicio().isAfter(hoy)))
                .sorted(Comparator.comparing(Proy::inicio, Comparator.nullsLast(Comparator.<LocalDate>naturalOrder())))
                .limit(4)
                .map(p -> {
                    int asignados = colaboradores.getOrDefault(p.id(), 0);
                    Long dias = p.inicio() != null && p.inicio().isAfter(hoy) ? ChronoUnit.DAYS.between(hoy, p.inicio()) : null;
                    List<String> habs = requeridasDe.getOrDefault(p.id(), List.of()).stream()
                            .map(HabReq::nombre).distinct().limit(4).toList();
                    return new ProyectoProximo(p.id(), p.nombre(), p.estado(),
                            p.inicio() != null ? diaMes(p.inicio()) : "Sin fecha", dias,
                            p.requeridos(), asignados, Math.max(0, p.requeridos() - asignados), habs);
                })
                .toList();
    }

    /** Línea de tiempo: 3 semanas atrás y ~10 semanas adelante. */
    private Gantt gantt(LocalDate hoy, List<Proy> proyectos, List<Asig> asignaciones, Map<Long, Integer> colaboradores) {
        LocalDate desde = hoy.minusDays(GANTT_DIAS_ANTES);
        LocalDate hasta = desde.plusDays(GANTT_DIAS_TOTAL);
        Map<Long, Long> equipo = asignaciones.stream().filter(a -> "activa".equals(a.estado()))
                .collect(Collectors.groupingBy(Asig::proyectoId, Collectors.counting()));

        List<GanttFila> filas = proyectos.stream()
                .sorted(Comparator.comparing(Proy::inicio, Comparator.nullsLast(Comparator.<LocalDate>naturalOrder())))
                .filter(p -> p.finEstimado() == null || !p.finEstimado().isBefore(desde))
                .limit(8)
                .map(p -> {
                    LocalDate inicio = p.inicio() != null ? p.inicio() : hoy;
                    boolean abierto = p.finEstimado() == null;
                    LocalDate fin = abierto ? hasta : p.finEstimado();
                    double ini = pct(desde, inicio), finPct = pct(desde, fin);
                    double izquierda = Math.max(0, Math.min(98, ini));
                    double ancho = Math.max(2, Math.min(100, finPct) - izquierda);
                    String fechas = (p.inicio() != null ? diaMes(p.inicio()) : "sin inicio") + " → "
                            + (abierto ? "sin fecha de fin" : diaMes(p.finEstimado()));
                    return new GanttFila(p.id(), p.nombre(), p.estado(), etiquetaEstado(p.estado()), izquierda, ancho,
                            abierto, inicio.isBefore(desde), fechas, equipo.getOrDefault(p.id(), 0L).intValue(),
                            Math.max(0, p.requeridos() - colaboradores.getOrDefault(p.id(), 0)));
                })
                .toList();

        List<GanttMarca> meses = new ArrayList<>();
        LocalDate mes = desde.withDayOfMonth(1).plusMonths(1);
        while (mes.isBefore(hasta)) {
            meses.add(new GanttMarca(mes.format(DateTimeFormatter.ofPattern("MMM", ES)).replace(".", ""), pct(desde, mes)));
            mes = mes.plusMonths(1);
        }
        return new Gantt(filas, meses, pct(desde, hoy), diaMes(desde) + " – " + diaMes(hasta));
    }

    // ───────────────────────── Habilidades ─────────────────────────

    /**
     * Para cada habilidad que piden los proyectos abiertos: cuántas plazas
     * abiertas hay en esos proyectos vs. cuántos Miembros la tienen al nivel
     * pedido y con capacidad. "Faltan" = plazas - personas con capacidad.
     * En riesgo = la dominan 2 personas o menos (monopolio de conocimiento).
     */
    private List<HabilidadDemanda> habilidades(int limite, List<Miembro> miembros, Map<Long, Integer> carga,
                                               List<HabPerfil> habPerfil, List<HabReq> habReq, List<Proy> proyectos,
                                               Map<Long, Integer> colaboradores) {
        Set<Long> ids = miembros.stream().map(Miembro::id).collect(Collectors.toSet());
        Map<Long, Integer> vacantes = proyectos.stream().collect(Collectors.toMap(Proy::id,
                p -> Math.max(0, p.requeridos() - colaboradores.getOrDefault(p.id(), 0))));
        Map<Long, List<HabReq>> porHabilidad = habReq.stream().collect(Collectors.groupingBy(HabReq::habilidadId));

        List<HabilidadDemanda> lista = new ArrayList<>();
        for (Map.Entry<Long, List<HabReq>> e : porHabilidad.entrySet()) {
            List<HabReq> reqs = e.getValue();
            int nivelMin = reqs.stream().mapToInt(HabReq::nivel).min().orElse(1);
            Set<Long> proyectosQueLaPiden = reqs.stream().map(HabReq::proyectoId).collect(Collectors.toSet());
            int plazas = proyectosQueLaPiden.stream().mapToInt(id -> vacantes.getOrDefault(id, 0)).sum();
            List<HabPerfil> quienes = habPerfil.stream()
                    .filter(h -> h.habilidadId().equals(e.getKey()) && ids.contains(h.perfilId())).toList();
            int personas = (int) quienes.stream().map(HabPerfil::perfilId).distinct().count();
            int conCapacidad = (int) quienes.stream()
                    .filter(h -> h.nivel() >= nivelMin && carga.getOrDefault(h.perfilId(), 0) < limite)
                    .map(HabPerfil::perfilId).distinct().count();
            lista.add(new HabilidadDemanda(e.getKey(), reqs.get(0).nombre(), personas, conCapacidad,
                    proyectosQueLaPiden.size(), plazas, Math.max(0, plazas - conCapacidad), 0));
        }
        lista.sort(Comparator.comparingInt(HabilidadDemanda::faltan).reversed()
                .thenComparing(Comparator.comparingInt(HabilidadDemanda::plazasAbiertas).reversed())
                .thenComparingInt(HabilidadDemanda::personas)
                .thenComparing(HabilidadDemanda::nombre));
        List<HabilidadDemanda> top = lista.size() > 6 ? lista.subList(0, 6) : lista;
        int max = Math.max(1, top.stream().mapToInt(h -> Math.max(h.personas(), h.plazasAbiertas())).max().orElse(1));
        return top.stream().map(h -> new HabilidadDemanda(h.id(), h.nombre(), h.personas(), h.conCapacidad(),
                h.proyectos(), h.plazasAbiertas(), h.faltan(), Math.round(h.personas() * 100f / max))).toList();
    }

    // ───────────────────────── Utilidades ─────────────────────────

    private int limiteSeguro() {
        try {
            return proyectoService.limiteCargaConfigurado();
        } catch (RuntimeException ex) {
            return 100;
        }
    }

    private static double pct(LocalDate desde, LocalDate dia) {
        return Math.round(ChronoUnit.DAYS.between(desde, dia) * 1000.0 / GANTT_DIAS_TOTAL) / 10.0;
    }

    private static LocalDate fecha(LocalDateTime t) {
        return t != null ? t.toLocalDate() : null;
    }

    private static String diaMes(LocalDate d) {
        return d.format(DIA_MES).replace(".", "");
    }

    private static String etiquetaEstado(String estado) {
        return switch (estado) {
            case "planificacion" -> "Planificación";
            case "activo" -> "Activo";
            case "en_pausa" -> "En pausa";
            default -> estado;
        };
    }

    static String iniciales(String nombre) {
        if (nombre == null || nombre.isBlank()) return "?";
        String[] partes = nombre.trim().split("\\s+");
        String ini = partes[0].substring(0, 1) + (partes.length > 1 ? partes[1].substring(0, 1) : "");
        return ini.toUpperCase(ES);
    }

    private <T> List<T> cargar(String que, java.util.function.Supplier<List<T>> consulta) {
        try {
            return consulta.get();
        } catch (RuntimeException ex) {
            log.warn("Inicio RM: no se pudo leer {} ({})", que, ex.getMessage());
            return List.of();
        }
    }

    private <T> T seguro(String que, java.util.function.Supplier<T> calculo, T vacio) {
        try {
            return calculo.get();
        } catch (RuntimeException ex) {
            log.warn("Inicio RM: no se pudo calcular {} ({})", que, ex.getMessage());
            return vacio;
        }
    }
}
