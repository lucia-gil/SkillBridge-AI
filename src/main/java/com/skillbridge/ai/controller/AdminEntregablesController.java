package com.skillbridge.ai.controller;

import com.skillbridge.ai.dto.EntregaFila;
import com.skillbridge.ai.dto.EntregableFila;
import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.model.Entregable;
import com.skillbridge.ai.repository.ProyectoRepository;
import com.skillbridge.ai.service.EntregableService;
import com.skillbridge.ai.service.ShellModelBuilder;
import com.skillbridge.ai.util.OperacionInvalidaException;
import com.skillbridge.ai.util.SesionKeys;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.server.ResponseStatusException;

import java.time.format.DateTimeFormatter;
import java.util.List;

/** Mismo módulo que EntregablesPmController, para el Administrador (gestiona cualquier proyecto). */
@Controller
@RequestMapping("/administrador")
public class AdminEntregablesController {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final EntregableService entregableService;
    private final ProyectoRepository proyectoRepository;
    private final ShellModelBuilder shellModelBuilder;

    public AdminEntregablesController(EntregableService entregableService, ProyectoRepository proyectoRepository,
                                      ShellModelBuilder shellModelBuilder) {
        this.entregableService = entregableService;
        this.proyectoRepository = proyectoRepository;
        this.shellModelBuilder = shellModelBuilder;
    }

    @GetMapping("/proyectos/{proyectoId}/entregables.html")
    public String listar(@PathVariable Long proyectoId, HttpSession session, Model model) {
        UsuarioSesion sesion = sesion(session);
        var proyecto = proyectoRepository.findById(proyectoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        List<EntregableFila> entregables = entregableService.listarParaGestion(proyectoId);

        shellModelBuilder.aplicar(model, sesion, "proyectos.html", "Entregables · " + proyecto.getNombre(),
                entregables.size() + " entregable(s)");
        model.addAttribute("proyecto", proyecto);
        model.addAttribute("entregables", entregables);
        return "administrador/entregables";
    }

    @PostMapping("/proyectos/{proyectoId}/entregables")
    public String crear(@PathVariable Long proyectoId, @RequestParam String titulo,
                        @RequestParam(required = false) String descripcion,
                        @RequestParam String fechaApertura, @RequestParam String fechaCierre,
                        HttpSession session, RedirectAttributes ra) {
        UsuarioSesion sesion = sesion(session);
        try {
            entregableService.crear(proyectoId, sesion.getPerfilId(), sesion.getRolEfectivo(), sesion.getUsuarioId(),
                    titulo, descripcion, fechaApertura, fechaCierre);
            ra.addFlashAttribute("exito", "Entregable creado.");
        } catch (OperacionInvalidaException ex) { ra.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/administrador/proyectos/" + proyectoId + "/entregables.html";
    }

    @PostMapping("/entregables/{id}/editar")
    public String editar(@PathVariable Long id, @RequestParam Long proyectoId, @RequestParam String titulo,
                         @RequestParam(required = false) String descripcion,
                         @RequestParam String fechaApertura, @RequestParam String fechaCierre,
                          HttpSession session, RedirectAttributes ra) {
        UsuarioSesion sesion = sesion(session);
        try {
            entregableService.editar(id, sesion.getPerfilId(), sesion.getRolEfectivo(), sesion.getUsuarioId(),
                    titulo, descripcion, fechaApertura, fechaCierre);
            ra.addFlashAttribute("exito", "Entregable actualizado.");
        } catch (OperacionInvalidaException ex) { ra.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/administrador/proyectos/" + proyectoId + "/entregables.html";
    }

    @PostMapping("/entregables/{id}/cancelar")
    public String cancelar(@PathVariable Long id, @RequestParam Long proyectoId,
                           @RequestParam(required = false) String motivo, HttpSession session, RedirectAttributes ra) {
        UsuarioSesion sesion = sesion(session);
        try {
            entregableService.cancelar(id, sesion.getPerfilId(), sesion.getRolEfectivo(), motivo, sesion.getUsuarioId());
            ra.addFlashAttribute("exito", "Entregable cancelado.");
        } catch (OperacionInvalidaException ex) { ra.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/administrador/proyectos/" + proyectoId + "/entregables.html";
    }

    @GetMapping("/entregables/{id}.html")
    public String detalle(@PathVariable Long id, HttpSession session, Model model) {
        UsuarioSesion sesion = sesion(session);
        Entregable e = entregableService.obtener(id);
        var proyecto = proyectoRepository.findById(e.getProyectoId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        List<EntregaFila> entregas = entregableService.detalleParaRevision(id, sesion.getPerfilId(), sesion.getRolEfectivo());

        shellModelBuilder.aplicar(model, sesion, "proyectos.html", e.getTitulo(),
                proyecto.getNombre() + " · " + entregas.stream().filter(EntregaFila::isEntrego).count() + " de " + entregas.size() + " entregaron");
        model.addAttribute("entregable", e);
        model.addAttribute("fechaAperturaLabel", FECHA.format(e.getFechaApertura()));
        model.addAttribute("fechaCierreLabel", FECHA.format(e.getFechaCierre()));
        model.addAttribute("proyecto", proyecto);
        model.addAttribute("entregas", entregas);
        return "administrador/entregable-detalle";
    }

    /** Revisión por estados (Completado / Requiere ajustes / Rehacer) con comentario obligatorio. */
    @PostMapping("/entregas/{id}/revisar")
    public String revisar(@PathVariable Long id, @RequestParam Long entregableId,
                          @RequestParam(required = false) String estado,
                          @RequestParam(required = false) String comentario,
                            HttpSession session, RedirectAttributes ra) {
        UsuarioSesion sesion = sesion(session);
        try {
            entregableService.revisar(id, sesion.getPerfilId(), sesion.getRolEfectivo(), sesion.getUsuarioId(),
                    estado, comentario);
            ra.addFlashAttribute("exito", "Revisión guardada: " + EntregableService.etiquetaEstado(estado) + ".");
        } catch (OperacionInvalidaException ex) { ra.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/administrador/entregables/" + entregableId + ".html";
    }

    private UsuarioSesion sesion(HttpSession session) { return (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO); }
}
