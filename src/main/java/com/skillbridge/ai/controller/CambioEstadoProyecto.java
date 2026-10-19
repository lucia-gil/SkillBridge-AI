package com.skillbridge.ai.controller;

import com.skillbridge.ai.service.AsignacionMasivaService;
import com.skillbridge.ai.service.ProyectoService;
import com.skillbridge.ai.service.TalentMatchingService;
import com.skillbridge.ai.util.OperacionInvalidaException;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;

/**
 * "Cambiar estado" de un proyecto, compartido por el Administrador (cualquier
 * proyecto) y el Project Manager (solo los suyos; el controlador verifica la
 * propiedad antes de llamar aquí). Modelo v7, fase 2:
 *
 *  1) ProyectoService.cambiarEstado aplica la transición y sus efectos
 *     (pausar saca al equipo, cerrar finaliza/cancela asignaciones, etc.).
 *  2) Si se reanudó o reabrió y se marcaron personas del equipo anterior,
 *     TalentMatchingService.proponerReincorporaciones crea una propuesta por
 *     persona para el Resource Manager (solo el RM crea asignaciones).
 *
 * Son dos pasos separados a propósito: el cambio de estado queda guardado
 * aunque alguna reincorporación no se pueda proponer (se informa cuál y por qué).
 */
@Component
public class CambioEstadoProyecto {

    private final ProyectoService proyectoService;
    private final TalentMatchingService talentMatchingService;

    public CambioEstadoProyecto(ProyectoService proyectoService, TalentMatchingService talentMatchingService) {
        this.proyectoService = proyectoService;
        this.talentMatchingService = talentMatchingService;
    }

    public void aplicar(Long proyectoId, String estado, String motivo, LocalDate fechaReanudacion,
                        List<Long> reincorporar, Long actorUsuarioId, RedirectAttributes ra) {
        try {
            proyectoService.cambiarEstado(proyectoId, estado, motivo, fechaReanudacion, actorUsuarioId);
        } catch (OperacionInvalidaException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
            return;
        }
        String base = switch (estado) {
            case "en_pausa" -> "Proyecto en pausa. El equipo salió del proyecto y su carga quedó liberada.";
            case "completado" -> "Proyecto completado. Las asignaciones del equipo pasaron al historial.";
            case "cancelado" -> "Proyecto cancelado. Las asignaciones del equipo pasaron al historial.";
            default -> "Estado del proyecto actualizado.";
        };
        if (reincorporar == null || reincorporar.isEmpty()) {
            ra.addFlashAttribute("exito", base);
            return;
        }
        try {
            AsignacionMasivaService.Resultado r = talentMatchingService.proponerReincorporaciones(
                    proyectoId, reincorporar, actorUsuarioId);
            String enviados = r.huboAsignados()
                    ? " Se enviaron " + r.asignados().size() + " propuesta(s) de reincorporación al Resource Manager: "
                      + String.join(", ", r.asignados()) + "."
                    : "";
            ra.addFlashAttribute("exito", base + enviados);
            if (r.huboErrores()) {
                ra.addFlashAttribute("error", "No se pudo proponer la reincorporación de todos. " + String.join(" ", r.errores()));
            }
        } catch (OperacionInvalidaException ex) {
            ra.addFlashAttribute("exito", base);
            ra.addFlashAttribute("error", "Reincorporación no enviada: " + ex.getMessage());
        }
    }
}
