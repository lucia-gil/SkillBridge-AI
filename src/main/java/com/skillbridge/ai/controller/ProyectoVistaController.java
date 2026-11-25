package com.skillbridge.ai.controller;

import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.service.ProyectoVistaService;
import com.skillbridge.ai.service.IdentidadDniService;
import com.skillbridge.ai.service.ShellModelBuilder;
import com.skillbridge.ai.util.OperacionInvalidaException;
import com.skillbridge.ai.util.SesionKeys;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.server.ResponseStatusException;

/**
 * Vista de detalle de un proyecto (compartido/proyecto-detalle.html):
 *  - Project Manager: solo los proyectos que lidera; propone candidatos al RM.
 *  - Resource Manager: cualquier proyecto; asigna candidatos directamente.
 */
@Controller
public class ProyectoVistaController {

    private final ShellModelBuilder shell;
    private final ProyectoVistaService vistaService;
    private final IdentidadDniService identidadDni;

    public ProyectoVistaController(ShellModelBuilder shell, ProyectoVistaService vistaService, IdentidadDniService identidadDni) {
        this.shell = shell;
        this.vistaService = vistaService;
        this.identidadDni = identidadDni;
    }

    @GetMapping("/project-manager/proyectos/{id}.html")
    public String pm(@PathVariable Long id, HttpSession session, Model model, RedirectAttributes ra) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        if (!vistaService.esPmDe(sesion.getPerfilId(), id)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return pintar(id, sesion, model, ra, "proyectos.html", "project-manager", "/project-manager/proyectos.html");
    }

    @GetMapping("/resource-manager/proyectos/{id}.html")
    public String rm(@PathVariable Long id, HttpSession session, Model model, RedirectAttributes ra) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        return pintar(id, sesion, model, ra, "asignaciones.html", "resource-manager", "/resource-manager/asignaciones.html");
    }

    private String pintar(Long id, UsuarioSesion sesion, Model model, RedirectAttributes ra,
                          String menuActivo, String area, String volver) {
        try {
            ProyectoVistaService.Vista v = vistaService.vista(id);
            shell.aplicar(model, sesion, menuActivo, v.detalle().getNombre(), "Detalle del proyecto");
            model.addAttribute("v", v);
            model.addAttribute("perfilesVerificados", identidadDni.perfilesVerificados(v.equipo().stream()
                    .map(ProyectoVistaService.Miembro::perfilId).toList()));
            model.addAttribute("area", area);
            model.addAttribute("esPm", "project-manager".equals(area));
            model.addAttribute("volverUrl", volver);
            return "compartido/proyecto-detalle";
        } catch (OperacionInvalidaException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:" + volver;
        }
    }
}
