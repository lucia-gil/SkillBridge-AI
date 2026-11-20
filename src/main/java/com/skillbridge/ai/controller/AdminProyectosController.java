package com.skillbridge.ai.controller;

import com.skillbridge.ai.dto.ProyectoDetalle;
import com.skillbridge.ai.dto.ProyectoFila;
import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.service.ExtraccionProyectoIaService;
import com.skillbridge.ai.service.ProyectoService;
import com.skillbridge.ai.service.SelectorPersonasService;
import com.skillbridge.ai.service.TecnologiaCatalogoService;
import com.skillbridge.ai.service.ShellModelBuilder;
import com.skillbridge.ai.util.OperacionInvalidaException;
import com.skillbridge.ai.util.SesionKeys;
import jakarta.servlet.http.HttpSession;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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
    private final CambioEstadoProyecto cambioEstadoProyecto;
    private final SelectorPersonasService selectorPersonasService;
    private final ExtraccionProyectoIaService extraccionProyectoIaService;

    public AdminProyectosController(ProyectoService proyectoService, ShellModelBuilder shellModelBuilder,
                                    TecnologiaCatalogoService tecnologiaCatalogoService,
                                    CambioEstadoProyecto cambioEstadoProyecto,
                                    SelectorPersonasService selectorPersonasService,
                                    ExtraccionProyectoIaService extraccionProyectoIaService) {
        this.proyectoService = proyectoService;
        this.shellModelBuilder = shellModelBuilder;
        this.tecnologiaCatalogoService = tecnologiaCatalogoService;
        this.cambioEstadoProyecto = cambioEstadoProyecto;
        this.selectorPersonasService = selectorPersonasService;
        this.extraccionProyectoIaService = extraccionProyectoIaService;
    }

    @GetMapping("/proyectos.html")
    public String proyectos(HttpSession session, Model model) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);

        List<ProyectoFila> proyectos = proyectoService.listar();
        List<ProyectoDetalle> detalles = proyectos.stream()
                .map(p -> {
                    ProyectoDetalle d = proyectoService.obtenerDetalle(p.getId());
                    // Pestaña "Historial" del modal (modelo v7, fase 5).
                    d.setHistorial(proyectoService.historialEquipo(p.getId()));
                    return d;
                })
                .collect(Collectors.toList());

        shellModelBuilder.aplicar(model, sesion, "proyectos.html", "Proyectos",
                "Vista global de los " + proyectos.size() + " proyectos de la organización");

        model.addAttribute("cargaPm", ProyectoService.CARGA_PM_POR_DEFECTO);
        model.addAttribute("proyectos", proyectos);
        model.addAttribute("detalles", detalles);
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
                         @RequestParam Long pmPerfilId, @RequestParam(required=false) Integer dedicacion,
                         HttpSession session, RedirectAttributes redirectAttributes) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        try {
            List<String> lista = tecnologiaCatalogoService.normalizar(ProyectosPmController.separarTecnologias(tecnologias), List.of());
            proyectoService.crear(nombre, descripcion, lista, fechaInicio, fechaFinEstimada,
                    colaboradoresRequeridos, pmPerfilId, sesion.getUsuarioId(), null,
                    dedicacion == null ? ProyectoService.CARGA_PM_POR_DEFECTO : dedicacion);
            redirectAttributes.addFlashAttribute("exito", "Proyecto \"" + nombre.trim() + "\" creado.");
        } catch (OperacionInvalidaException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/administrador/proyectos.html";
    }

    @PostMapping(value = "/proyectos/extraer-ia", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('administrador')")
    @ResponseBody
    public ResponseEntity<?> extraerIa(@RequestParam("archivo") MultipartFile archivo) {
        try {
            return ResponseEntity.ok(extraccionProyectoIaService.extraer(archivo));
        } catch (OperacionInvalidaException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    /**
     * Cambiar estado (modelo v7, fase 2): pausar exige motivo y fecha
     * estimada de reanudación; al reanudar/reabrir se puede marcar a quién
     * del equipo anterior reincorporar (propuestas que aprueba el RM).
     * razonCancelacion conserva su nombre por compatibilidad: es el motivo
     * de la pausa o la razón de la cancelación.
     */
    @PostMapping("/proyectos/{id}/estado")
    public String cambiarEstado(@PathVariable Long id, @RequestParam String estado,
                                 @RequestParam(required = false) String razonCancelacion,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaReanudacion,
                                 @RequestParam(name = "reincorporar", required = false) List<Long> reincorporar,
                                 HttpSession session, RedirectAttributes redirectAttributes) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        cambioEstadoProyecto.aplicar(id, estado, razonCancelacion, fechaReanudacion, reincorporar,
                sesion.getUsuarioId(), redirectAttributes);
        return "redirect:/administrador/proyectos.html";
    }

    /**
     * Candidatos a Project Manager para "Nuevo proyecto" (modelo v7, fase 5):
     * Miembros activos con capacidad para el 20%, buscados en el servidor
     * (selector-personas.js en modo "pm").
     */
    @GetMapping("/candidatos-pm")
    @ResponseBody
    public ResponseEntity<?> candidatosPm(
                                              @RequestParam(required = false) String q,
                                              @RequestParam(required=false) Integer dedicacion,
                                              @RequestParam(defaultValue = "match") String orden,
                                              @RequestParam(defaultValue = "false") boolean soloConCapacidad,
                                              @RequestParam(defaultValue = "1") int pagina) {
        return ResponseEntity.ok(selectorPersonasService.buscarParaPm(null, new SelectorPersonasService.Filtros(q, null, null, null, null,
                    soloConCapacidad, dedicacion == null ? ProyectoService.CARGA_PM_POR_DEFECTO : dedicacion, orden, pagina)));
    }

    // Modelo v7: el Administrador NO asigna ni finaliza asignaciones (lo
    // hace el Resource Manager; el PM propone). Solo elige al PM al crear
    // el proyecto y ve los equipos en modo lectura. Por eso se retiraron
    // POST /administrador/proyectos/{id}/asignar y
    // POST /administrador/asignaciones/{id}/finalizar.
}
