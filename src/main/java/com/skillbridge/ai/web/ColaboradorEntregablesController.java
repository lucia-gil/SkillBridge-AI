package com.skillbridge.ai.web;

import com.skillbridge.ai.dto.EntregableFila;
import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.model.Entregable;
import com.skillbridge.ai.repository.ProyectoRepository;
import com.skillbridge.ai.service.EntregableService;
import com.skillbridge.ai.service.ShellModelBuilder;
import com.skillbridge.ai.util.OperacionInvalidaException;
import com.skillbridge.ai.util.SesionKeys;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * "Mis entregables" (colaborador/entregables.html): entregables de TODOS
 * los proyectos donde tiene una asignación activa, con su propio estado
 * de entrega en cada uno. El detalle (colaborador/entregable-detalle.html)
 * es donde sube/edita/borra su entrega puntual.
 */
@Controller
@RequestMapping("/colaborador")
public class ColaboradorEntregablesController {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final EntregableService entregableService;
    private final ProyectoRepository proyectoRepository;
    private final ShellModelBuilder shellModelBuilder;

    public ColaboradorEntregablesController(EntregableService entregableService, ProyectoRepository proyectoRepository,
                                            ShellModelBuilder shellModelBuilder) {
        this.entregableService = entregableService;
        this.proyectoRepository = proyectoRepository;
        this.shellModelBuilder = shellModelBuilder;
    }

    @GetMapping("/entregables.html")
    public String listar(HttpSession session, Model model) {
        UsuarioSesion sesion = sesion(session);
        List<EntregableFila> entregables = entregableService.misEntregables(sesion.getPerfilId());

        long pendientes = entregables.stream().filter(e -> e.getMiEntrega() == null && !e.isVencido()).count();
        shellModelBuilder.aplicar(model, sesion, "entregables.html", "Mis entregables",
                entregables.size() + " entregable(s) · " + pendientes + " pendiente(s)");
        model.addAttribute("entregables", entregables);
        return "colaborador/entregables";
    }

    @GetMapping("/entregables/{id}.html")
    public String detalle(@PathVariable Long id, HttpSession session, Model model) {
        UsuarioSesion sesion = sesion(session);
        Entregable e = entregableService.obtener(id);
        var proyecto = proyectoRepository.findById(e.getProyectoId())
                .orElseThrow(() -> new OperacionInvalidaException("El proyecto no existe."));
        // Reutiliza misEntregables() y filtra el que corresponde: así la
        // regla "solo ve entregables de SUS proyectos" vive en un único
        // lugar (EntregableRepository.listarVisiblesParaPerfil), sin
        // duplicar la verificación de pertenencia acá.
        EntregableFila fila = entregableService.misEntregables(sesion.getPerfilId()).stream()
                .filter(f -> f.getId().equals(id)).findFirst()
                .orElseThrow(() -> new OperacionInvalidaException("No perteneces a este proyecto."));

        shellModelBuilder.aplicar(model, sesion, "entregables.html", e.getTitulo(), proyecto.getNombre());
        model.addAttribute("entregable", e);
        model.addAttribute("fechaAperturaLabel", FECHA.format(e.getFechaApertura()));
        model.addAttribute("fechaCierreLabel", FECHA.format(e.getFechaCierre()));
        model.addAttribute("proyecto", proyecto);
        model.addAttribute("fila", fila);
        return "colaborador/entregable-detalle";
    }

    @PostMapping("/entregables/{id}/entregar")
    public String entregar(@PathVariable Long id, @RequestParam(required = false) String texto,
                           @RequestParam(required = false) String urlEntrega,
                           @RequestParam(required = false) MultipartFile archivo,
                           HttpSession session, RedirectAttributes ra) {
        UsuarioSesion sesion = sesion(session);
        try {
            entregableService.entregar(id, sesion.getPerfilId(), sesion.getUsuarioId(), texto, urlEntrega, archivo);
            ra.addFlashAttribute("exito", "Entrega registrada.");
        } catch (OperacionInvalidaException ex) { ra.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/colaborador/entregables/" + id + ".html";
    }

    @PostMapping("/entregables/{id}/eliminar")
    public String eliminar(@PathVariable Long id, HttpSession session, RedirectAttributes ra) {
        UsuarioSesion sesion = sesion(session);
        try {
            entregableService.eliminarEntrega(id, sesion.getPerfilId(), sesion.getUsuarioId());
            ra.addFlashAttribute("exito", "Entrega eliminada.");
        } catch (OperacionInvalidaException ex) { ra.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/colaborador/entregables/" + id + ".html";
    }

    private UsuarioSesion sesion(HttpSession session) { return (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO); }
}
