package com.skillbridge.ai.web;

import com.skillbridge.ai.dto.HiloDetalle;
import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.service.ForoService;
import com.skillbridge.ai.service.ShellModelBuilder;
import com.skillbridge.ai.util.OperacionInvalidaException;
import com.skillbridge.ai.util.Roles;
import com.skillbridge.ai.util.SesionKeys;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Detalle de un hilo (colaborador/foro-hilo.html): responder y marcar una
 * respuesta como solución (solo quien abrió el hilo, o un Administrador).
 */
@Controller
@RequestMapping("/colaborador")
public class ForoHiloController {

    private final ForoService foroService;
    private final ShellModelBuilder shellModelBuilder;

    public ForoHiloController(ForoService foroService, ShellModelBuilder shellModelBuilder) {
        this.foroService = foroService;
        this.shellModelBuilder = shellModelBuilder;
    }

    @GetMapping("/foro-hilo.html")
    public String hilo(@RequestParam Long id, HttpSession session, Model model, RedirectAttributes redirectAttributes) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        boolean esAdmin = Roles.ADMINISTRADOR.equals(sesion.getRolEfectivo());

        HiloDetalle detalle;
        try {
            detalle = foroService.obtenerDetalle(id, sesion.getPerfilId(), esAdmin);
        } catch (OperacionInvalidaException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/colaborador/foros.html";
        }

        shellModelBuilder.aplicar(model, sesion, "foros.html", detalle.getTitulo(),
                "Foros › " + detalle.getProyectoNombre());
        model.addAttribute("hilo", detalle);
        return "colaborador/foro-hilo";
    }

    @PostMapping("/foro-hilo/{id}/responder")
    public String responder(@PathVariable Long id, @RequestParam String contenido,
                             HttpSession session, RedirectAttributes redirectAttributes) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        try {
            foroService.responder(id, sesion.getPerfilId(), contenido);
            redirectAttributes.addFlashAttribute("exito", "Tu respuesta se publicó en el hilo.");
        } catch (OperacionInvalidaException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/colaborador/foro-hilo.html?id=" + id;
    }

    @PostMapping("/foro-hilo/{hiloId}/respuestas/{respuestaId}/marcar-solucion")
    public String marcarSolucion(@PathVariable Long hiloId, @PathVariable Long respuestaId,
                                  HttpSession session, RedirectAttributes redirectAttributes) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        boolean esAdmin = Roles.ADMINISTRADOR.equals(sesion.getRolEfectivo());
        try {
            foroService.marcarSolucion(respuestaId, sesion.getPerfilId(), esAdmin);
            redirectAttributes.addFlashAttribute("exito", "Respuesta marcada como solución del hilo.");
        } catch (OperacionInvalidaException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/colaborador/foro-hilo.html?id=" + hiloId;
    }
}
