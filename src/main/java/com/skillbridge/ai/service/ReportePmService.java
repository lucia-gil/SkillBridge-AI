package com.skillbridge.ai.service;

import com.skillbridge.ai.dto.EstadoProyectoUtil;
import com.skillbridge.ai.dto.ReportePmDatos;
import com.skillbridge.ai.dto.ReportePmFila;
import com.skillbridge.ai.model.Asignacion;
import com.skillbridge.ai.model.Proyecto;
import com.skillbridge.ai.repository.AsignacionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
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

    public ReportePmService(AsignacionRepository asignacionRepository) {
        this.asignacionRepository = asignacionRepository;
    }

    @Transactional(readOnly = true)
    public List<Proyecto> proyectosGestionados(Long perfilId) {
        return asignacionRepository.listarProyectosGestionados(perfilId);
    }

    @Transactional(readOnly = true)
    public ReportePmDatos generar(Long perfilId, List<Long> proyectoIds, String estado,
                                  LocalDate desde, LocalDate hasta) {
        // Filtro de proyectos de selección múltiple (RG-217): lista vacía o
        // null = todos los proyectos que gestiona el PM.
        Set<Long> seleccion = new HashSet<>();
        if (proyectoIds != null) for (Long id : proyectoIds) if (id != null) seleccion.add(id);
        List<Proyecto> disponibles = proyectosGestionados(perfilId);
        // List.of(...).contains(null) lanza NullPointerException. La vista
        // entra sin el parámetro "estado" la primera vez, por lo que hay que
        // validar el null antes de consultar la lista de valores permitidos.
        String estadoValido = estado != null && ESTADOS.contains(estado) ? estado : null;
        String proyectoFiltro = "Todos";
        if (!seleccion.isEmpty()) {
            List<String> nombres = disponibles.stream().filter(p -> seleccion.contains(p.getId()))
                    .map(Proyecto::getNombre).collect(Collectors.toList());
            proyectoFiltro = nombres.isEmpty() ? "Sin coincidencias" : String.join(", ", nombres);
        }

        List<ReportePmFila> filas = new ArrayList<>();
        for (Proyecto p : disponibles) {
            if (!seleccion.isEmpty() && !seleccion.contains(p.getId())) continue;
            if (estadoValido != null && !estadoValido.equals(p.getEstado())) continue;
            if (desde != null && (p.getFechaInicio() == null || p.getFechaInicio().isBefore(desde))) continue;
            if (hasta != null && (p.getFechaInicio() == null || p.getFechaInicio().isAfter(hasta))) continue;

            List<Asignacion> equipo = asignacionRepository.listarEquipoDeProyecto(p.getId(), "activa");
            int carga = equipo.stream().mapToInt(a -> a.getCargaPorcentaje() != null ? a.getCargaPorcentaje() : 0).sum();
            filas.add(new ReportePmFila(p.getId(), p.getNombre(), p.getEstado(), p.getFechaInicio(),
                    p.getFechaFinEstimada(), ProyectoService.avanceHeuristico(
                            p.getFechaInicio(), p.getFechaFinEstimada(), p.getEstado()), equipo.size(), carga));
        }

        Map<String, Long> porEstado = new LinkedHashMap<>();
        for (String valor : ESTADOS) {
            long cantidad = filas.stream().filter(f -> valor.equals(f.getEstadoCrudo())).count();
            if (cantidad > 0) porEstado.put(EstadoProyectoUtil.label(valor), cantidad);
        }
        return new ReportePmDatos(filas, porEstado, proyectoFiltro,
                estadoValido == null ? "Todos" : EstadoProyectoUtil.label(estadoValido), desde, hasta);
    }
}
