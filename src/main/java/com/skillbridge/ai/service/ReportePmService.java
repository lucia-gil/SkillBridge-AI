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
    public ReportePmDatos generar(Long perfilId, Long proyectoId, String estado,
                                  LocalDate desde, LocalDate hasta) {
        List<Proyecto> disponibles = proyectosGestionados(perfilId);
        // List.of(...).contains(null) lanza NullPointerException. La vista
        // entra sin el parámetro "estado" la primera vez, por lo que hay que
        // validar el null antes de consultar la lista de valores permitidos.
        String estadoValido = estado != null && ESTADOS.contains(estado) ? estado : null;
        String proyectoFiltro = "Todos";
        if (proyectoId != null) {
            proyectoFiltro = disponibles.stream().filter(p -> p.getId().equals(proyectoId))
                    .map(Proyecto::getNombre).findFirst().orElse("Sin coincidencias");
        }

        List<ReportePmFila> filas = new ArrayList<>();
        for (Proyecto p : disponibles) {
            if (proyectoId != null && !proyectoId.equals(p.getId())) continue;
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
