package com.skillbridge.ai.service;

import com.skillbridge.ai.model.Proyecto;
import com.skillbridge.ai.repository.AsignacionRepository;
import com.skillbridge.ai.repository.ProyectoRepository;
import com.skillbridge.ai.util.OperacionInvalidaException;
import com.skillbridge.ai.util.Roles;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Asignación en masa: asigna varios colaboradores a un proyecto en un solo
 * envío (checkboxes del modal "Asignar colaboradores").
 *
 * No duplica ninguna regla de negocio: por cada persona llama a
 * ProyectoService.asignarColaborador(...) A TRAVÉS DEL PROXY de Spring, así
 * cada asignación corre en su propia transacción con las mismas
 * validaciones de siempre (vacantes, límite de carga, máximo de proyectos
 * simultáneos, no duplicar, no asignar Admin/RM). Si una persona no cumple,
 * se informa su error y las demás sí se asignan.
 */
@Service
public class AsignacionMasivaService {

    /** Resultado para armar los mensajes flash de éxito / error. */
    public record Resultado(List<String> asignados, List<String> errores) {
        public boolean huboAsignados() { return !asignados.isEmpty(); }
        public boolean huboErrores() { return !errores.isEmpty(); }
    }

    private final ProyectoService proyectoService;
    private final ProyectoRepository proyectoRepository;
    private final AsignacionRepository asignacionRepository;

    public AsignacionMasivaService(ProyectoService proyectoService, ProyectoRepository proyectoRepository,
                                   AsignacionRepository asignacionRepository) {
        this.proyectoService = proyectoService;
        this.proyectoRepository = proyectoRepository;
        this.asignacionRepository = asignacionRepository;
    }

    public Resultado asignarVarios(Long proyectoId, List<Long> perfilIds, int cargaPorcentaje,
                                   LocalDate fechaInicio, Long actorUsuarioId) {
        // LinkedHashSet: quita IDs repetidos y respeta el orden de selección.
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        if (perfilIds != null) {
            for (Long id : perfilIds) if (id != null) ids.add(id);
        }
        if (ids.isEmpty()) {
            throw new OperacionInvalidaException("Selecciona al menos un colaborador.");
        }
        Proyecto p = proyectoRepository.findById(proyectoId)
                .orElseThrow(() -> new OperacionInvalidaException("El proyecto ya no existe."));
        long activos = asignacionRepository.countByProyectoIdAndEstadoAndRolEnProyecto(
                proyectoId, "activa", Roles.COLABORADOR);
        long vacantes = Math.max(0, p.getColaboradoresRequeridos() - activos);
        if (ids.size() > vacantes) {
            // Se rechaza todo el lote en vez de asignar "a los primeros N":
            // así quien asigna decide a quién deja fuera.
            throw new OperacionInvalidaException("Seleccionaste " + ids.size() + " colaborador(es), pero el proyecto solo tiene "
                    + vacantes + " vacante(s) disponible(s).");
        }

        List<String> asignados = new ArrayList<>();
        List<String> errores = new ArrayList<>();
        for (Long perfilId : ids) {
            try {
                proyectoService.asignarColaborador(proyectoId, perfilId, Roles.COLABORADOR, cargaPorcentaje,
                        fechaInicio, actorUsuarioId);
                asignados.add(proyectoService.nombreDePerfil(perfilId));
            } catch (OperacionInvalidaException ex) {
                errores.add(ex.getMessage());
            }
        }
        return new Resultado(asignados, errores);
    }

    /** Arma los textos de los flash a partir del resultado (lo usan PM y RM). */
    public static String mensajeExito(Resultado r) {
        if (!r.huboAsignados()) return null;
        return r.asignados().size() == 1
                ? r.asignados().get(0) + " fue asignado(a) al proyecto."
                : r.asignados().size() + " colaboradores asignados al proyecto: " + String.join(", ", r.asignados()) + ".";
    }

    public static String mensajeError(Resultado r) {
        if (!r.huboErrores()) return null;
        return (r.huboAsignados() ? "No se pudo asignar a todos. " : "") + String.join(" ", r.errores());
    }
}
