package com.skillbridge.ai.service;

import com.skillbridge.ai.dto.EstadoProyectoUtil;
import com.skillbridge.ai.dto.ReportePmDatos;
import com.skillbridge.ai.dto.ReportePmFila;
import com.skillbridge.ai.model.Asignacion;
import com.skillbridge.ai.model.Proyecto;
import com.skillbridge.ai.repository.AsignacionRepository;
import com.skillbridge.ai.repository.EntregableRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.stream.Collectors;
import java.util.Set;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ReportePmService {

    private static final List<String> ESTADOS = List.of("planificacion", "activo", "en_pausa", "completado", "cancelado");

    private final AsignacionRepository asignacionRepository;
    private final EntregableRepository entregableRepository;

    public ReportePmService(AsignacionRepository asignacionRepository, EntregableRepository entregableRepository) {
        this.asignacionRepository = asignacionRepository;
        this.entregableRepository = entregableRepository;
    }

    @Transactional(readOnly = true)
    public List<Proyecto> proyectosGestionados(Long perfilId) {
        return asignacionRepository.listarProyectosGestionados(perfilId);
    }

    /**
     * Filtros del reporte del PM. periodo: "6m", "1a", "3a" o vacío (todo);
     * ultimos: 5, 10 o 20 proyectos más recientes (null = todos). El rango de
     * fechas toma los proyectos ACTIVOS en algún momento del rango (no solo
     * los que empezaron dentro), para que un proyecto largo no quede fuera.
     */
    public record FiltrosPm(List<Long> proyectoIds, String estado, LocalDate desde, LocalDate hasta,
                            boolean soloRiesgo, String periodo, Integer ultimos) {
        public FiltrosPm {
            periodo = periodo != null && List.of("6m", "1a", "3a").contains(periodo) ? periodo : "";
            ultimos = ultimos != null && List.of(5, 10, 20).contains(ultimos) ? ultimos : null;
        }

        /** Inicio efectivo del rango: el elegido a mano o el del periodo rápido. */
        public LocalDate desdeEfectivo() {
            if (desde != null || periodo.isEmpty()) return desde;
            LocalDate hoy = LocalDate.now(java.time.ZoneId.of("America/Lima"));
            return switch (periodo) { case "6m" -> hoy.minusMonths(6); case "1a" -> hoy.minusYears(1); default -> hoy.minusYears(3); };
        }
    }

    public ReportePmDatos generar(Long perfilId, List<Long> proyectoIds, String estado,
                                  LocalDate desde, LocalDate hasta) {
        return generar(perfilId, proyectoIds, estado, desde, hasta, false);
    }

    public ReportePmDatos generar(Long perfilId, List<Long> proyectoIds, String estado,
                                  LocalDate desde, LocalDate hasta, boolean soloRiesgo) {
        return generar(perfilId, new FiltrosPm(proyectoIds, estado, desde, hasta, soloRiesgo, null, null));
    }

    /** Proyectos que quedan tras los filtros, del más reciente al más antiguo (para fichas y exportes). */
    @Transactional(readOnly = true)
    public List<Proyecto> proyectosFiltrados(Long perfilId, FiltrosPm f) {
        Set<Long> seleccion = new HashSet<>();
        if (f.proyectoIds() != null) for (Long id : f.proyectoIds()) if (id != null) seleccion.add(id);
        String estadoValido = f.estado() != null && ESTADOS.contains(f.estado()) ? f.estado() : null;
        LocalDate desde = f.desdeEfectivo(), hasta = f.hasta();
        List<Proyecto> lista = proyectosGestionados(perfilId).stream()
                .filter(p -> seleccion.isEmpty() || seleccion.contains(p.getId()))
                .filter(p -> estadoValido == null || estadoValido.equals(p.getEstado()))
                // Activo en el rango: empezó antes del fin del rango y no terminó antes de su inicio.
                .filter(p -> hasta == null || (p.getFechaInicio() != null && !p.getFechaInicio().isAfter(hasta)))
                .filter(p -> desde == null || p.getFechaFinEstimada() == null || !p.getFechaFinEstimada().isBefore(desde))
                .sorted(java.util.Comparator.comparing(Proyecto::getFechaInicio, java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder()))
                        .thenComparing(Proyecto::getNombre))
                .collect(Collectors.toCollection(ArrayList::new));
        if (f.ultimos() != null && lista.size() > f.ultimos()) lista = new ArrayList<>(lista.subList(0, f.ultimos()));
        return lista;
    }

    @Transactional(readOnly = true)
    public ReportePmDatos generar(Long perfilId, FiltrosPm f) {
        Set<Long> seleccion = new HashSet<>();
        if (f.proyectoIds() != null) for (Long id : f.proyectoIds()) if (id != null) seleccion.add(id);
        List<Proyecto> disponibles = proyectosGestionados(perfilId);
        String estadoValido = f.estado() != null && ESTADOS.contains(f.estado()) ? f.estado() : null;
        String proyectoFiltro = "Todos";
        if (!seleccion.isEmpty()) {
            List<String> nombres = disponibles.stream().filter(p -> seleccion.contains(p.getId()))
                    .map(Proyecto::getNombre).collect(Collectors.toList());
            proyectoFiltro = nombres.isEmpty() ? "Sin coincidencias" : String.join(", ", nombres);
        }

        List<ReportePmFila> filas = new ArrayList<>();
        for (Proyecto p : proyectosFiltrados(perfilId, f)) {
            List<Asignacion> equipo = asignacionRepository.listarEquipoDeProyecto(p.getId(), "activa");
            int carga = equipo.stream().mapToInt(a -> a.getCargaPorcentaje() != null ? a.getCargaPorcentaje() : 0).sum();
            filas.add(new ReportePmFila(p.getId(), p.getNombre(), p.getEstado(), p.getFechaInicio(),
                    p.getFechaFinEstimada(), ProyectoService.avanceHeuristico(
                            p.getFechaInicio(), p.getFechaFinEstimada(), p.getEstado()), equipo.size(), carga));
        }

        // Entregables vencidos (RF nuevo del hero): se cuenta sobre el mismo
        // conjunto de proyectos ya visible tras los filtros, antes de aplicar
        // el toggle "Solo en riesgo" (es un dato del portafolio filtrado, no
        // de la sub-vista de riesgo).
        List<Long> idsVisibles = filas.stream().map(ReportePmFila::getProyectoId).collect(Collectors.toList());
        long entregablesVencidos = idsVisibles.isEmpty() ? 0
                : entregableRepository.countVencidos(idsVisibles, LocalDateTime.now());

        if (f.soloRiesgo()) {
            filas = filas.stream().filter(ReportePmFila::isEnRiesgo).collect(Collectors.toCollection(ArrayList::new));
        }

        Map<String, Long> porEstado = new LinkedHashMap<>();
        for (String valor : ESTADOS) {
            long cantidad = filas.stream().filter(x -> valor.equals(x.getEstadoCrudo())).count();
            if (cantidad > 0) porEstado.put(EstadoProyectoUtil.label(valor), cantidad);
        }
        return new ReportePmDatos(filas, porEstado, proyectoFiltro,
                estadoValido == null ? "Todos" : EstadoProyectoUtil.label(estadoValido), f.desdeEfectivo(), f.hasta(),
                entregablesVencidos, f.soloRiesgo(), f.ultimos());
    }
}
