package com.skillbridge.ai.web;

import com.skillbridge.ai.dto.HiloFila;
import com.skillbridge.ai.dto.ProyectoOpcion;
import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.model.ForoPublicacion;
import com.skillbridge.ai.service.ForoService;
import com.skillbridge.ai.service.ShellModelBuilder;
import com.skillbridge.ai.util.OperacionInvalidaException;
import com.skillbridge.ai.util.SesionKeys;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * Foros (colaborador/foros.html). Ver comentario de ForoService: "Categorías"
 * del mockup se reinterpreta como "tus proyectos" (única agrupación real que
 * ofrece el esquema v4), y el resumen generado por IA de cada hilo NO se
 * implementa (fuera de alcance - sin motor de IA en esta entrega).
 */
@Controller
@RequestMapping("/colaborador")
public class ForosController {

    private final ForoService foroService;
    private final ShellModelBuilder shellModelBuilder;

    public ForosController(ForoService foroService, ShellModelBuilder shellModelBuilder) {
        this.foroService = foroService;
        this.shellModelBuilder = shellModelBuilder;
    }

    @GetMapping("/foros.html")
    public String foros(HttpSession session, Model model) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        Long perfilId = sesion.getPerfilId();

        List<HiloFila> hilos = foroService.listarHilosDeMisProyectos(perfilId);
        List<ProyectoOpcion> proyectos = foroService.misProyectosConHilos(perfilId);
        long totalRespuestas = hilos.stream().mapToLong(HiloFila::getNumRespuestas).sum();

        shellModelBuilder.aplicar(model, sesion, "foros.html", "Foros",
                "Conocimiento compartido de tus proyectos · " + hilos.size() + " hilos");

        model.addAttribute("hilos", hilos);
        model.addAttribute("proyectos", proyectos);
        model.addAttribute("totalRespuestas", totalRespuestas);
        return "colaborador/foros";
    }

    @PostMapping("/foros/nuevo-hilo")
    public String nuevoHilo(@RequestParam Long proyectoId, @RequestParam String titulo, @RequestParam String contenido,
                             HttpSession session, RedirectAttributes redirectAttributes) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        try {
            ForoPublicacion hilo = foroService.crearHilo(sesion.getPerfilId(), proyectoId, titulo, contenido);
            redirectAttributes.addFlashAttribute("exito", "Tu hilo se publicó correctamente.");
            return "redirect:/colaborador/foro-hilo.html?id=" + hilo.getId();
        } catch (OperacionInvalidaException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/colaborador/foros.html";
        }
    }
}
