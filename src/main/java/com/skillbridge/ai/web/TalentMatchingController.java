package com.skillbridge.ai.web;

import com.skillbridge.ai.dto.MatchingCandidato;
import com.skillbridge.ai.dto.MatchingProyectoOpcion;
import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.service.ShellModelBuilder;
import com.skillbridge.ai.service.TalentMatchingService;
import com.skillbridge.ai.util.OperacionInvalidaException;
import com.skillbridge.ai.util.SesionKeys;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Controller
public class TalentMatchingController {
    private final TalentMatchingService matchingService;
    private final ShellModelBuilder shellModelBuilder;

    public TalentMatchingController(TalentMatchingService matchingService, ShellModelBuilder shellModelBuilder) {
        this.matchingService = matchingService;
        this.shellModelBuilder = shellModelBuilder;
    }

    @GetMapping("/project-manager/ai-talent-matching.html")
    public String projectManager(@RequestParam(required = false) Long proyectoId,
            @RequestParam(defaultValue = "50") int dedicacion,
            @RequestParam(required = false) Integer pesoHabilidades,
            @RequestParam(required = false) Integer pesoExperiencia,
            @RequestParam(required = false) Integer pesoDisponibilidad,
            HttpSession session, Model model) {
        UsuarioSesion sesion = sesion(session);
        List<MatchingProyectoOpcion> proyectos = matchingService.proyectosDelPm(sesion.getPerfilId());
        int[] cfg = matchingService.pesosConfigurados();
        int ph = pesoHabilidades != null ? pesoHabilidades : cfg[0];
        int pe = pesoExperiencia != null ? pesoExperiencia : cfg[1];
        int pd = pesoDisponibilidad != null ? pesoDisponibilidad : cfg[2];
        Long seleccionado = proyectoId != null ? proyectoId : (proyectos.isEmpty() ? null : proyectos.get(0).id());
        List<MatchingCandidato> candidatos = Collections.emptyList();
        if (seleccionado != null) {
            try {
                candidatos = matchingService.calcular(sesion.getPerfilId(), seleccionado, dedicacion, ph, pe, pd);
                // Solo se muestran los 5 mejores candidatos en la vista, para que
                // la lista no quede mas larga que el panel de detalle al costado.
                if (candidatos.size() > 5) candidatos = candidatos.subList(0, 5);
            }
            catch (OperacionInvalidaException ex) { model.addAttribute("errorVista", ex.getMessage()); }
        }
        String nombreProyecto = proyectos.stream().filter(p -> p.id().equals(seleccionado)).map(MatchingProyectoOpcion::nombre).findFirst().orElse("Selecciona un proyecto");
        shellModelBuilder.aplicar(model, sesion, "ai-talent-matching.html", "AI Talent Matching",
                seleccionado != null ? nombreProyecto + " · candidatos recomendados" : "No hay vacantes abiertas");
        model.addAttribute("proyectos", proyectos); model.addAttribute("proyectoId", seleccionado);
        model.addAttribute("proyectoNombre", nombreProyecto); model.addAttribute("dedicacion", dedicacion);
        model.addAttribute("pesoHabilidades", ph); model.addAttribute("pesoExperiencia", pe); model.addAttribute("pesoDisponibilidad", pd);
        model.addAttribute("candidatos", candidatos);
        return "project-manager/ai-talent-matching";
    }

    @PostMapping("/project-manager/ai-talent-matching/proponer")
    public String proponer(@RequestParam Long proyectoId, @RequestParam Long candidatoId,
            @RequestParam int dedicacion, @RequestParam int pesoHabilidades,
            @RequestParam int pesoExperiencia, @RequestParam int pesoDisponibilidad,
            HttpSession session, RedirectAttributes ra) {
        UsuarioSesion sesion = sesion(session);
        try {
            matchingService.proponer(sesion.getPerfilId(), sesion.getUsuarioId(), proyectoId, candidatoId,
                    dedicacion, pesoHabilidades, pesoExperiencia, pesoDisponibilidad);
            ra.addFlashAttribute("exito", "Propuesta enviada al Resource Manager.");
        } catch (OperacionInvalidaException ex) { ra.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/project-manager/ai-talent-matching.html?proyectoId=" + proyectoId + "&dedicacion=" + dedicacion;
    }

    @GetMapping("/resource-manager/ai-talent-matching.html")
    public String resourceManager(HttpSession session, Model model) {
        UsuarioSesion sesion = sesion(session);
        var pendientes = matchingService.pendientes();
        var resueltas = matchingService.resueltas();
        long compatibles = pendientes.stream().filter(p -> p.cargaCompatible()).count();
        shellModelBuilder.aplicar(model, sesion, "ai-talent-matching.html", "AI Talent Matching",
                pendientes.size() + " propuesta(s) pendiente(s)", Map.of("ai-talent-matching.html", pendientes.size()));
        model.addAttribute("pendientes", pendientes); model.addAttribute("resueltas", resueltas);
        model.addAttribute("compatibles", compatibles); model.addAttribute("conSobrecarga", pendientes.size() - compatibles);
        return "resource-manager/ai-talent-matching";
    }

    @PostMapping("/resource-manager/ai-talent-matching/{id}/resolver")
    public String resolver(@PathVariable Long id, @RequestParam String decision,
            @RequestParam(required = false) String motivo, HttpSession session, RedirectAttributes ra) {
        UsuarioSesion sesion = sesion(session);
        try {
            boolean aprobar = "aprobar".equals(decision);
            if (!aprobar && !"rechazar".equals(decision)) throw new OperacionInvalidaException("Decisión no reconocida.");
            matchingService.resolver(id, aprobar, motivo, sesion.getPerfilId(), sesion.getUsuarioId());
            ra.addFlashAttribute("exito", aprobar ? "Propuesta aprobada y colaborador asignado." : "Propuesta rechazada.");
        } catch (OperacionInvalidaException ex) { ra.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/resource-manager/ai-talent-matching.html";
    }

    private UsuarioSesion sesion(HttpSession session) { return (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO); }
}
