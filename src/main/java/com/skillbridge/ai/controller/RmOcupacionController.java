package com.skillbridge.ai.controller;

import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.service.ProyectoService;
import com.skillbridge.ai.util.OperacionInvalidaException;
import com.skillbridge.ai.util.SesionKeys;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Carga del equipo del Resource Manager. La antigua pantalla "Ocupación del
 * equipo" se unió a Colaboradores (RmColaboradoresController): allí están
 * las tarjetas de capacidad y el modal "Ajustar carga" (RG-214), que guarda
 * la dedicación con el POST de esta clase. Finalizar una asignación desde el
 * modal reutiliza RmAsignacionesController con volver=colaboradores.
 */
@Controller
@RequestMapping("/resource-manager")
public class RmOcupacionController {

    private final ProyectoService proyectoService;

    public RmOcupacionController(ProyectoService proyectoService) {
        this.proyectoService = proyectoService;
    }

    /**
     * "Ocupación del equipo" se unió a Colaboradores (capacidad, carga y
     * perfiles en una sola pantalla). Se conserva la dirección para que los
     * enlaces antiguos sigan funcionando.
     */
    @GetMapping("/ocupacion.html")
    public String ocupacion() {
        return "redirect:/resource-manager/colaboradores.html";
    }

    /** "Rebalancear" -> cambiar la dedicacion (%) de una asignacion activa. */
    @PostMapping("/asignaciones/{asignacionId}/dedicacion")
    public String ajustarDedicacion(@PathVariable Long asignacionId,
                                    @RequestParam int cargaPorcentaje,
                                    @RequestParam(required = false) String motivo,
                                    HttpSession session, RedirectAttributes redirectAttributes) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        try {
            proyectoService.ajustarDedicacion(asignacionId, cargaPorcentaje, motivo, sesion.getUsuarioId());
            redirectAttributes.addFlashAttribute("exito", "Dedicación actualizada a " + cargaPorcentaje + "%.");
        } catch (OperacionInvalidaException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/resource-manager/colaboradores.html";
    }
}