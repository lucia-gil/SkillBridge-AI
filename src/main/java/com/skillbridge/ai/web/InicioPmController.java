package com.skillbridge.ai.web;

import com.skillbridge.ai.dto.HiloFila;
import com.skillbridge.ai.dto.MiProyectoFila;
import com.skillbridge.ai.dto.ProyectoDetalle;
import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.service.ForoService;
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
 * Inicio real del Project Manager (project-manager/inicio.html): un pequeno
 * dashboard servido desde el backend (antes era la demo mock que salia en
 * blanco). Resume los proyectos que gestiona, cuantos colaboradores tiene
 * asignados y la actividad reciente en sus foros.
 */
@Controller
@RequestMapping("/project-manager")
public class InicioPmController {

    private final ProyectoService proyectoService;
    private final ForoService foroService;
    private final ShellModelBuilder shellModelBuilder;

    public InicioPmController(ProyectoService proyectoService, ForoService foroService,
                             ShellModelBuilder shellModelBuilder) {
        this.proyectoService = proyectoService;
        this.foroService = foroService;
        this.shellModelBuilder = shellModelBuilder;
    }

    @GetMapping("/inicio.html")
    public String inicio(HttpSession session, Model model) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        Long perfilId = sesion.getPerfilId();

        List<ProyectoDetalle> proyectos = proyectoService.misProyectosActivos(perfilId).stream()
                .filter(m -> Roles.PROJECT_MANAGER.equals(m.getRolEnProyectoCrudo()))
                .map(MiProyectoFila::getProyectoId)
                .map(proyectoService::obtenerDetalle)
                .collect(Collectors.toList());

        long totalMiembros = proyectos.stream().mapToLong(p -> p.getEquipo().size()).sum();
        long activos = proyectos.stream().filter(p -> "activo".equals(p.getEstadoCrudo())).count();
        int avancePromedio = proyectos.isEmpty() ? 0
                : (int) Math.round(proyectos.stream().mapToInt(ProyectoDetalle::getAvance).average().orElse(0));
        long enRiesgo = proyectos.stream().filter(p -> "en_riesgo".equals(p.getEstadoCrudo())).count();

        List<HiloFila> hilos = foroService.listarHilosDeMisProyectos(perfilId);

        String subtitle = activos + " proyectos activos" + (enRiesgo > 0 ? " · " + enRiesgo + " alertas de riesgo" : "");
        shellModelBuilder.aplicar(model, sesion, "inicio.html", "Panel de Project Manager", subtitle);

        model.addAttribute("proyectos", proyectos);
        model.addAttribute("totalMiembros", totalMiembros);
        model.addAttribute("proyectosActivos", activos);
        model.addAttribute("avancePromedio", avancePromedio);
        model.addAttribute("totalHilos", hilos.size());
        model.addAttribute("hilosRecientes", hilos.size() > 4 ? hilos.subList(0, 4) : hilos);
        return "project-manager/inicio";
    }


    private String primerNombre(String nombreCompleto) {
        if (nombreCompleto == null || nombreCompleto.isBlank()) return "";
        return nombreCompleto.trim().split("\\s+")[0];
    }
}
