package com.skillbridge.ai.web;

import com.skillbridge.ai.dto.EventoFila;
import com.skillbridge.ai.dto.ProyectoOpcion;
import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.repository.TipoAudienciaRepository;
import com.skillbridge.ai.repository.TipoEventoRepository;
import com.skillbridge.ai.service.EventoService;
import com.skillbridge.ai.service.ShellModelBuilder;
import com.skillbridge.ai.util.OperacionInvalidaException;
import com.skillbridge.ai.util.SesionKeys;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * Calendario conectado a datos reales (colaborador y project-manager): agenda de
 * eventos de "eventos_proyecto" (reunion/entregable/hito) de los proyectos del
 * usuario, respetando la audiencia. "Nuevo evento" guarda en la tabla real.
 *
 * Los <select> de Tipo y Audiencia del formulario "Nuevo evento" YA NO tienen
 * las opciones escritas a mano en el HTML - se listan dinamicamente desde
 * tipos_evento/tipos_audiencia (ver tiposEvento/tiposAudiencia en el modelo).
 * Asi, si el Administrador agrega un tipo nuevo desde su panel de Catalogos
 * tecnicos, aparece aqui solo, sin tocar este archivo.
 */
@Controller
public class CalendarioController {

    private final EventoService eventoService;
    private final TipoEventoRepository tipoEventoRepository;
    private final TipoAudienciaRepository tipoAudienciaRepository;
    private final ShellModelBuilder shellModelBuilder;

    public CalendarioController(EventoService eventoService, TipoEventoRepository tipoEventoRepository,
                                TipoAudienciaRepository tipoAudienciaRepository, ShellModelBuilder shellModelBuilder) {
        this.eventoService = eventoService;
        this.tipoEventoRepository = tipoEventoRepository;
        this.tipoAudienciaRepository = tipoAudienciaRepository;
        this.shellModelBuilder = shellModelBuilder;
    }

    @GetMapping("/colaborador/calendario.html")
    public String colaborador(HttpSession session, Model model) {
        return vista(session, model, "colaborador/calendario", false, "/colaborador/calendario/nuevo");
    }

    @GetMapping("/project-manager/calendario.html")
    public String projectManager(HttpSession session, Model model) {
        return vista(session, model, "project-manager/calendario", true, "/project-manager/calendario/nuevo");
    }

    private String vista(HttpSession session, Model model, String plantilla, boolean esPm, String nuevoUrl) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        List<EventoFila> eventos = eventoService.listar(sesion.getPerfilId(), esPm);
        List<ProyectoOpcion> proyectos = eventoService.misProyectos(sesion.getPerfilId());
        shellModelBuilder.aplicar(model, sesion, "calendario.html", "Calendario",
                "Eventos de tus proyectos - " + eventos.size() + " eventos");
        model.addAttribute("eventos", eventos);
        model.addAttribute("proyectos", proyectos);
        model.addAttribute("nuevoUrl", nuevoUrl);
        model.addAttribute("tiposEvento", tipoEventoRepository.findAll());
        model.addAttribute("tiposAudiencia", tipoAudienciaRepository.findAll());
        return plantilla;
    }

    @PostMapping("/colaborador/calendario/nuevo")
    public String crearColaborador(@RequestParam Long proyectoId, @RequestParam String tipo, @RequestParam String titulo,
                                   @RequestParam(required = false) String descripcion, @RequestParam String fechaInicio,
                                   @RequestParam(required = false) String ubicacion, @RequestParam(required = false) String enlace,
                                   @RequestParam(required = false) String audiencia,
                                   HttpSession session, RedirectAttributes ra) {
        crear(session, ra, proyectoId, tipo, titulo, descripcion, fechaInicio, ubicacion, enlace, audiencia);
        return "redirect:/colaborador/calendario.html";
    }

    @PostMapping("/project-manager/calendario/nuevo")
    public String crearPm(@RequestParam Long proyectoId, @RequestParam String tipo, @RequestParam String titulo,
                          @RequestParam(required = false) String descripcion, @RequestParam String fechaInicio,
                          @RequestParam(required = false) String ubicacion, @RequestParam(required = false) String enlace,
                          @RequestParam(required = false) String audiencia,
                          HttpSession session, RedirectAttributes ra) {
        crear(session, ra, proyectoId, tipo, titulo, descripcion, fechaInicio, ubicacion, enlace, audiencia);
        return "redirect:/project-manager/calendario.html";
    }

    private void crear(HttpSession session, RedirectAttributes ra, Long proyectoId, String tipo, String titulo,
                       String descripcion, String fechaInicio, String ubicacion, String enlace, String audiencia) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        try {
            eventoService.crear(sesion.getPerfilId(), proyectoId, tipo, titulo, descripcion, fechaInicio, ubicacion, enlace, audiencia);
            ra.addFlashAttribute("exito", "Evento \"" + titulo.trim() + "\" creado.");
        } catch (OperacionInvalidaException ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
    }
}