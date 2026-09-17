package com.skillbridge.ai.web;

import com.skillbridge.ai.dto.HiloFila;
import com.skillbridge.ai.dto.MiProyectoFila;
import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.repository.PerfilHabilidadRepository;
import com.skillbridge.ai.service.ForoService;
import com.skillbridge.ai.service.ProyectoService;
import com.skillbridge.ai.service.ShellModelBuilder;
import com.skillbridge.ai.util.SesionKeys;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

/**
 * Inicio de Colaborador (colaborador/inicio.html).
 *
 * El mockup mostraba "Mis asignaciones de la semana" con tareas inventadas
 * (el esquema v4 no modela tareas/tickets, solo proyectos y asignaciones a
 * nivel de proyecto completo) y un mini-panel del Asistente IA; ninguno de
 * los dos se reconstruye aquí - ver README. Se reemplazan por un desglose
 * real de la carga por proyecto.
 */
@Controller
@RequestMapping("/colaborador")
public class ColaboradorInicioController {

    private final ProyectoService proyectoService;
    private final ForoService foroService;
    private final PerfilHabilidadRepository perfilHabilidadRepository;
    private final ShellModelBuilder shellModelBuilder;

    public ColaboradorInicioController(ProyectoService proyectoService, ForoService foroService,
                                        PerfilHabilidadRepository perfilHabilidadRepository,
                                        ShellModelBuilder shellModelBuilder) {
        this.proyectoService = proyectoService;
        this.foroService = foroService;
        this.perfilHabilidadRepository = perfilHabilidadRepository;
        this.shellModelBuilder = shellModelBuilder;
    }

    @GetMapping("/inicio.html")
    public String inicio(HttpSession session, Model model) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        Long perfilId = sesion.getPerfilId();

        List<MiProyectoFila> proyectosActivos = proyectoService.misProyectosActivos(perfilId);
        List<HiloFila> hilosRecientes = foroService.listarHilosDeMisProyectos(perfilId);
        long habilidadesDeclaradas = perfilHabilidadRepository.countById_PerfilId(perfilId);
        long habilidadesValidadas = perfilHabilidadRepository.countById_PerfilIdAndValidadoPorIdIsNotNull(perfilId);

        shellModelBuilder.aplicar(model, sesion, "inicio.html", "Hola, " + primerNombre(sesion.getNombreCompleto()),
                "NexaCorp · SkillBridge AI");

        model.addAttribute("proyectosActivos", proyectosActivos);
        model.addAttribute("cargaActiva", proyectoService.cargaActivaDe(perfilId));
        model.addAttribute("habilidadesDeclaradas", habilidadesDeclaradas);
        model.addAttribute("habilidadesValidadas", habilidadesValidadas);
        model.addAttribute("hilosRecientes", hilosRecientes.size() > 4 ? hilosRecientes.subList(0, 4) : hilosRecientes);
        return "colaborador/inicio";
    }

    private String primerNombre(String nombreCompleto) {
        if (nombreCompleto == null || nombreCompleto.isBlank()) return "";
        return nombreCompleto.trim().split("\\s+")[0];
    }
}
