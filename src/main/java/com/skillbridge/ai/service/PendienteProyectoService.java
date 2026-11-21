package com.skillbridge.ai.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Service
public class PendienteProyectoService {
    private static final int TAMANO_PAGINA = 12;
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private static final String SIN_LIDER = """
            SELECT p.id, p.id proyecto_id, p.nombre titulo, p.nombre proyecto, p.fecha_inicio fecha, 0 cantidad
            FROM proyectos p
            WHERE p.estado IN ('planificacion','activo','en_pausa') AND NOT EXISTS (
                SELECT 1 FROM asignaciones a JOIN perfiles pf ON pf.id=a.perfil_id
                    JOIN usuarios u ON u.id=pf.usuario_id
                WHERE a.proyecto_id=p.id AND a.estado='activa' AND a.rol_en_proyecto='project_manager'
                    AND u.estado='activo')
            """;

    private static final String ENTREGABLES_VENCIDOS = """
            SELECT e.id, p.id proyecto_id, e.titulo, p.nombre proyecto, e.fecha_cierre fecha, COUNT(*) cantidad
            FROM entregables e JOIN proyectos p ON p.id=e.proyecto_id
                JOIN asignaciones a ON a.proyecto_id=p.id AND a.estado='activa' AND a.rol_en_proyecto='colaborador'
                JOIN perfiles pf ON pf.id=a.perfil_id JOIN usuarios u ON u.id=pf.usuario_id AND u.estado='activo'
            WHERE p.estado='activo' AND e.estado='activo' AND e.fecha_cierre < ?
                AND a.fecha_inicio <= CAST(e.fecha_cierre AS DATE)
                AND (a.fecha_fin IS NULL OR a.fecha_fin >= CAST(e.fecha_cierre AS DATE))
                AND NOT EXISTS (SELECT 1 FROM entregas t WHERE t.entregable_id=e.id AND t.perfil_id=a.perfil_id)
            GROUP BY e.id, p.id, e.titulo, p.nombre, e.fecha_cierre
            """;

    private static final String ASIGNACIONES_POR_TERMINAR = """
            SELECT a.id, p.id proyecto_id, u.nombre_completo titulo, p.nombre proyecto, a.fecha_fin fecha, 0 cantidad
            FROM asignaciones a JOIN proyectos p ON p.id=a.proyecto_id
                JOIN perfiles pf ON pf.id=a.perfil_id JOIN usuarios u ON u.id=pf.usuario_id
            WHERE p.estado IN ('planificacion','activo') AND a.estado='activa' AND u.estado='activo'
                AND a.fecha_fin BETWEEN ? AND ?
            """;

    private final JdbcTemplate jdbc;

    public PendienteProyectoService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public enum Tipo {
        SIN_LIDER("sin-lider", "Proyectos sin líder", "Proyectos vigentes sin un Project Manager activo.", "icon-users"),
        ENTREGABLES("entregables", "Entregables vencidos", "Entregables cerrados con envíos pendientes del equipo activo.", "icon-file-text"),
        ASIGNACIONES("asignaciones", "Asignaciones por terminar", "Asignaciones activas que terminan hoy o durante los próximos siete días.", "icon-calendar");

        private final String slug;
        private final String titulo;
        private final String descripcion;
        private final String icono;

        Tipo(String slug, String titulo, String descripcion, String icono) {
            this.slug = slug;
            this.titulo = titulo;
            this.descripcion = descripcion;
            this.icono = icono;
        }

        public String slug() { return slug; }
        public String titulo() { return titulo; }
        public String descripcion() { return descripcion; }
        public String icono() { return icono; }

        public static Tipo elegir(String slug) {
            return Arrays.stream(values()).filter(tipo -> tipo.slug.equals(slug)).findFirst().orElse(SIN_LIDER);
        }
    }

    public record Resumen(Tipo tipo, long total) {}
    public record Fila(long id, long proyectoId, String titulo, String proyecto, LocalDateTime fecha, int cantidad) {
        public String fechaLabel(Tipo tipo) {
            if (fecha == null) return "—";
            String formato = tipo == Tipo.ENTREGABLES ? "dd MMM yyyy · HH:mm" : "dd MMM yyyy";
            return fecha.format(DateTimeFormatter.ofPattern(formato, Locale.forLanguageTag("es-PE")));
        }
    }
    public record Bandeja(Tipo tipo, List<Resumen> resumen, List<Fila> filas, int pagina, int paginas) {}
    private record Consulta(String sql, String orden, Object[] parametros) {}

    @Transactional(readOnly = true)
    public Bandeja buscar(Tipo tipo, int paginaSolicitada) {
        LocalDateTime ahora = LocalDateTime.now(LIMA);
        LocalDate hoy = ahora.toLocalDate();
        List<Resumen> resumen = Arrays.stream(Tipo.values()).map(item -> {
            Consulta consulta = consulta(item, ahora, hoy);
            long total = jdbc.queryForObject("SELECT COUNT(*) FROM (" + consulta.sql() + ") pendientes",
                    Long.class, consulta.parametros());
            return new Resumen(item, total);
        }).toList();
        long total = resumen.get(tipo.ordinal()).total();
        int paginas = Math.max(1, (int) ((total + TAMANO_PAGINA - 1) / TAMANO_PAGINA));
        int pagina = Math.min(Math.max(0, paginaSolicitada), paginas - 1);
        return new Bandeja(tipo, resumen, total == 0 ? List.of() : listar(tipo, ahora, hoy, pagina), pagina, paginas);
    }

    private Consulta consulta(Tipo tipo, LocalDateTime ahora, LocalDate hoy) {
        return switch (tipo) {
            case SIN_LIDER -> new Consulta(SIN_LIDER, "fecha ASC,id ASC", new Object[0]);
            case ENTREGABLES -> new Consulta(ENTREGABLES_VENCIDOS, "fecha DESC,id DESC", new Object[]{ahora});
            case ASIGNACIONES -> new Consulta(ASIGNACIONES_POR_TERMINAR, "fecha ASC,id ASC", new Object[]{hoy, hoy.plusDays(7)});
        };
    }

    private List<Fila> listar(Tipo tipo, LocalDateTime ahora, LocalDate hoy, int pagina) {
        Consulta consulta = consulta(tipo, ahora, hoy);
        Object[] parametros = Arrays.copyOf(consulta.parametros(), consulta.parametros().length + 2);
        parametros[parametros.length - 2] = TAMANO_PAGINA;
        parametros[parametros.length - 1] = (long) pagina * TAMANO_PAGINA;
        RowMapper<Fila> fila = (rs, n) -> new Fila(rs.getLong("id"), rs.getLong("proyecto_id"),
                rs.getString("titulo"), rs.getString("proyecto"), rs.getTimestamp("fecha").toLocalDateTime(),
                rs.getInt("cantidad"));
        return jdbc.query(consulta.sql() + " ORDER BY " + consulta.orden() + " LIMIT ? OFFSET ?", fila, parametros);
    }
}
