package com.skillbridge.ai.web;

import com.skillbridge.ai.dto.MiProyectoFila;
import com.skillbridge.ai.dto.PerfilOpcion;
import com.skillbridge.ai.dto.ProyectoDetalle;
import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.service.ProyectoService;
import com.skillbridge.ai.service.ShellModelBuilder;
import com.skillbridge.ai.util.Roles;
import com.skillbridge.ai.util.SesionKeys;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Vista de Asignaciones del Project Manager (project-manager/asignaciones.html):
 * los equipos de todos sus proyectos en un solo lugar. Las acciones de asignar
 * y finalizar reutilizan los endpoints de ProyectosPmController (POST
 * /project-manager/proyectos/{id}/asignar y
 * /project-manager/asignaciones/{id}/finalizar), enviando volver=asignaciones
 * para regresar a esta pagina.
 */
@Controller
@RequestMapping("/project-manager")
public class AsignacionesPmController {

    private final ProyectoService proyectoService;
    private final ShellModelBuilder shellModelBuilder;

    public AsignacionesPmController(ProyectoService proyectoService, ShellModelBuilder shellModelBuilder) {
        this.proyectoService = proyectoService;
        this.shellModelBuilder = shellModelBuilder;
    }

    @GetMapping("/asignaciones.html")
    public String asignaciones(HttpSession session, Model model) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        List<ProyectoDetalle> proyectos = proyectoService.misProyectosActivos(sesion.getPerfilId()).stream()
                .filter(m -> Roles.PROJECT_MANAGER.equals(m.getRolEnProyectoCrudo()))
                .map(MiProyectoFila::getProyectoId)
                .map(proyectoService::obtenerDetalle)
                .collect(Collectors.toList());
        List<PerfilOpcion> perfiles = proyectoService.listarPerfilesParaAsignar();
        long totalMiembros = proyectos.stream().mapToLong(p -> p.getEquipo().size()).sum();

        shellModelBuilder.aplicar(model, sesion, "asignaciones.html", "Asignaciones",
                totalMiembros + " asignaciones activas en tus " + proyectos.size() + " proyectos");
        model.addAttribute("proyectos", proyectos);
        model.addAttribute("perfiles", perfiles);
        return "project-manager/asignaciones";
    }
}
