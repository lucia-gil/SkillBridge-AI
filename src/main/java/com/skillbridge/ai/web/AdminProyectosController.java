package com.skillbridge.ai.web;

import com.skillbridge.ai.dto.PerfilOpcion;
import com.skillbridge.ai.dto.ProyectoDetalle;
import com.skillbridge.ai.dto.ProyectoFila;
import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.service.ProyectoService;
import com.skillbridge.ai.service.TecnologiaCatalogoService;
import com.skillbridge.ai.service.ShellModelBuilder;
import com.skillbridge.ai.util.OperacionInvalidaException;
import com.skillbridge.ai.util.SesionKeys;
import jakarta.servlet.http.HttpSession;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * CRUD real de Proyectos + asignación de colaboradores (administrador/proyectos.html).
 *
 * "Ver detalle" se resuelve con una plantilla oculta por fila (renderizada
 * por Thymeleaf en el propio HTML de la tabla) en vez de una llamada AJAX:
 * el volumen esperado de proyectos en esta plataforma es pequeño, así que
 * precalcular el detalle de cada fila es más simple que montar un endpoint
 * JSON aparte, y conserva el modal del mockup original tal cual.
 */
@Controller
@RequestMapping("/administrador")
public class AdminProyectosController {

    private final ProyectoService proyectoService;
    private final ShellModelBuilder shellModelBuilder;
    private final TecnologiaCatalogoService tecnologiaCatalogoService;

    public AdminProyectosController(ProyectoService proyectoService, ShellModelBuilder shellModelBuilder, TecnologiaCatalogoService tecnologiaCatalogoService) {
        this.proyectoService = proyectoService;
        this.shellModelBuilder = shellModelBuilder;
        this.tecnologiaCatalogoService = tecnologiaCatalogoService;
    }

    @GetMapping("/proyectos.html")
    public String proyectos(HttpSession session, Model model) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);

        List<ProyectoFila> proyectos = proyectoService.listar();
        List<ProyectoDetalle> detalles = proyectos.stream()
                .map(p -> proyectoService.obtenerDetalle(p.getId()))
                .collect(Collectors.toList());
        List<PerfilOpcion> perfiles = proyectoService.listarPerfilesParaAsignar();

        shellModelBuilder.aplicar(model, sesion, "proyectos.html", "Proyectos",
                "Vista global de los " + proyectos.size() + " proyectos de la organización");

        model.addAttribute("proyectos", proyectos);
        model.addAttribute("detalles", detalles);
        model.addAttribute("perfiles", perfiles);
        model.addAttribute("catalogoTecnologias", tecnologiaCatalogoService.catalogo());
        model.addAttribute("maxTecnologias", TecnologiaCatalogoService.MAX_TECNOLOGIAS);
        return "administrador/proyectos";
    }

    @PostMapping("/proyectos")
    public String crear(@RequestParam String nombre,
                         @RequestParam(required = false) String descripcion,
                         @RequestParam(required = false) String tecnologias,
                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFinEstimada,
                         @RequestParam(defaultValue = "0") int colaboradoresRequeridos,
                         @RequestParam Long pmPerfilId,
                         HttpSession session, RedirectAttributes redirectAttributes) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        try {
            List<String> lista = tecnologiaCatalogoService.normalizar(ProyectosPmController.separarTecnologias(tecnologias), List.of());
            proyectoService.crear(nombre, descripcion, lista, fechaInicio, fechaFinEstimada,
                    colaboradoresRequeridos, pmPerfilId, sesion.getUsuarioId());
            redirectAttributes.addFlashAttribute("exito", "Proyecto \"" + nombre.trim() + "\" creado.");
        } catch (OperacionInvalidaException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/administrador/proyectos.html";
    }

    @PostMapping("/proyectos/{id}/estado")
    public String cambiarEstado(@PathVariable Long id, @RequestParam String estado,
                                 @RequestParam(required = false) String razonCancelacion,
                                 HttpSession session, RedirectAttributes redirectAttributes) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        try {
            proyectoService.cambiarEstado(id, estado, razonCancelacion, sesion.getUsuarioId());
            redirectAttributes.addFlashAttribute("exito", "Estado del proyecto actualizado.");
        } catch (OperacionInvalidaException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/administrador/proyectos.html";
    }

    @PostMapping("/proyectos/{id}/asignar")
    public String asignar(@PathVariable Long id, @RequestParam Long perfilId, @RequestParam String rolEnProyecto,
                           @RequestParam int cargaPorcentaje,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
                           HttpSession session, RedirectAttributes redirectAttributes) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        try {
            proyectoService.asignarColaborador(id, perfilId, rolEnProyecto, cargaPorcentaje, fechaInicio, sesion.getUsuarioId());
            redirectAttributes.addFlashAttribute("exito", "Colaborador asignado al proyecto.");
        } catch (OperacionInvalidaException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/administrador/proyectos.html";
    }

    @PostMapping("/asignaciones/{asignacionId}/finalizar")
    public String finalizar(@PathVariable Long asignacionId, HttpSession session, RedirectAttributes redirectAttributes) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        try {
            proyectoService.finalizarAsignacion(asignacionId, sesion.getUsuarioId());
            redirectAttributes.addFlashAttribute("exito", "Asignación finalizada.");
        } catch (OperacionInvalidaException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/administrador/proyectos.html";
    }
}
