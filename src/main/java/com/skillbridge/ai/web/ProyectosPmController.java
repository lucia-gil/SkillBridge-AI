package com.skillbridge.ai.web;

import com.skillbridge.ai.dto.MiProyectoFila;
import com.skillbridge.ai.dto.PerfilOpcion;
import com.skillbridge.ai.dto.ProyectoDetalle;
import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.model.Asignacion;
import com.skillbridge.ai.repository.AsignacionRepository;
import com.skillbridge.ai.service.ProyectoService;
import com.skillbridge.ai.service.ShellModelBuilder;
import com.skillbridge.ai.util.OperacionInvalidaException;
import com.skillbridge.ai.util.Roles;
import com.skillbridge.ai.util.SesionKeys;
import jakarta.servlet.http.HttpSession;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * CRUD de Proyectos del Project Manager, restringido a SUS proyectos (aquellos
 * donde tiene una asignacion activa como project_manager). Reutiliza
 * ProyectoService: mismas reglas de negocio y validaciones que el panel del
 * administrador. La diferencia es el alcance (solo sus proyectos) y que al
 * crear un proyecto el propio PM queda como responsable automaticamente.
 */
@Controller
@RequestMapping("/project-manager")
public class ProyectosPmController {

    private final ProyectoService proyectoService;
    private final AsignacionRepository asignacionRepository;
    private final ShellModelBuilder shellModelBuilder;

    public ProyectosPmController(ProyectoService proyectoService, AsignacionRepository asignacionRepository,
                                 ShellModelBuilder shellModelBuilder) {
        this.proyectoService = proyectoService;
        this.asignacionRepository = asignacionRepository;
        this.shellModelBuilder = shellModelBuilder;
    }

    /** IDs de los proyectos donde este perfil es Project Manager activo. */
    private List<Long> misProyectoIds(Long perfilId) {
        return proyectoService.misProyectosActivos(perfilId).stream()
                .filter(m -> Roles.PROJECT_MANAGER.equals(m.getRolEnProyectoCrudo()))
                .map(MiProyectoFila::getProyectoId)
                .collect(Collectors.toList());
    }

    private void exigirPropiedad(Long perfilId, Long proyectoId) {
        if (!misProyectoIds(perfilId).contains(proyectoId)) {
            throw new OperacionInvalidaException("Ese proyecto no esta bajo tu gestion.");
        }
    }

    @GetMapping("/proyectos.html")
    public String proyectos(HttpSession session, Model model) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        List<ProyectoDetalle> proyectos = misProyectoIds(sesion.getPerfilId()).stream()
                .map(proyectoService::obtenerDetalle)
                .collect(Collectors.toList());
        List<PerfilOpcion> perfiles = proyectoService.listarPerfilesParaAsignar();

        shellModelBuilder.aplicar(model, sesion, "proyectos.html", "Proyectos",
                "Tus " + proyectos.size() + " proyectos como Project Manager");
        model.addAttribute("proyectos", proyectos);
        model.addAttribute("perfiles", perfiles);
        return "project-manager/proyectos";
    }

    @PostMapping("/proyectos")
    public String crear(@RequestParam String nombre,
                        @RequestParam(required = false) String descripcion,
                        @RequestParam(required = false) String tecnologias,
                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFinEstimada,
                        @RequestParam(defaultValue = "0") int colaboradoresRequeridos,
                        HttpSession session, RedirectAttributes redirectAttributes) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        try {
            List<String> lista = tecnologias == null ? List.of() : Arrays.stream(tecnologias.split(","))
                    .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
            // El Project Manager del proyecto es el propio usuario que lo crea.
            proyectoService.crear(nombre, descripcion, lista, fechaInicio, fechaFinEstimada,
                    colaboradoresRequeridos, sesion.getPerfilId(), sesion.getUsuarioId());
            redirectAttributes.addFlashAttribute("exito", "Proyecto \"" + nombre.trim() + "\" creado.");
        } catch (OperacionInvalidaException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/project-manager/proyectos.html";
    }

    @PostMapping("/proyectos/{id}/estado")
    public String cambiarEstado(@PathVariable Long id, @RequestParam String estado,
                                HttpSession session, RedirectAttributes redirectAttributes) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        try {
            exigirPropiedad(sesion.getPerfilId(), id);
            proyectoService.cambiarEstado(id, estado, sesion.getUsuarioId());
            redirectAttributes.addFlashAttribute("exito", "Estado del proyecto actualizado.");
        } catch (OperacionInvalidaException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/project-manager/proyectos.html";
    }

    @PostMapping("/proyectos/{id}/asignar")
    public String asignar(@PathVariable Long id, @RequestParam Long perfilId, @RequestParam String rolEnProyecto,
                          @RequestParam int cargaPorcentaje,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
                          @RequestParam(required = false) String volver,
                          HttpSession session, RedirectAttributes redirectAttributes) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        try {
            exigirPropiedad(sesion.getPerfilId(), id);
            proyectoService.asignarColaborador(id, perfilId, rolEnProyecto, cargaPorcentaje, fechaInicio, sesion.getUsuarioId());
            redirectAttributes.addFlashAttribute("exito", "Colaborador asignado al proyecto.");
        } catch (OperacionInvalidaException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return destino(volver);
    }

    @PostMapping("/asignaciones/{asignacionId}/finalizar")
    public String finalizar(@PathVariable Long asignacionId, @RequestParam(required = false) String volver,
                            HttpSession session, RedirectAttributes redirectAttributes) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        try {
            Asignacion a = asignacionRepository.findById(asignacionId)
                    .orElseThrow(() -> new OperacionInvalidaException("La asignacion ya no existe."));
            exigirPropiedad(sesion.getPerfilId(), a.getProyectoId());
            proyectoService.finalizarAsignacion(asignacionId, sesion.getUsuarioId());
            redirectAttributes.addFlashAttribute("exito", "Asignacion finalizada.");
        } catch (OperacionInvalidaException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return destino(volver);
    }

    private String destino(String volver) {
        return "redirect:" + ("asignaciones".equals(volver)
                ? "/project-manager/asignaciones.html"
                : "/project-manager/proyectos.html");
    }
}
