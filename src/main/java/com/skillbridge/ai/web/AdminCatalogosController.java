package com.skillbridge.ai.web;

import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.service.CatalogoTecnicoService;
import com.skillbridge.ai.service.ShellModelBuilder;
import com.skillbridge.ai.util.OperacionInvalidaException;
import com.skillbridge.ai.util.SesionKeys;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Módulo 4 del Admin: catálogos técnicos chicos (tipos_notificacion,
 * tipos_evento, tipos_audiencia) - una sola pantalla con los 3, porque son
 * chicos y comparten la misma forma. TODO (equipo): proteger este
 * controlador para que solo entre gente con rol_organizacional =
 * 'administrador', igual que el resto de /administrador/**.
 */
@Controller
@RequestMapping("/administrador")
public class AdminCatalogosController {

    private final CatalogoTecnicoService catalogoTecnicoService;
    private final ShellModelBuilder shellModelBuilder;

    public AdminCatalogosController(CatalogoTecnicoService catalogoTecnicoService, ShellModelBuilder shellModelBuilder) {
        this.catalogoTecnicoService = catalogoTecnicoService;
        this.shellModelBuilder = shellModelBuilder;
    }

    @GetMapping("/catalogos.html")
    public String catalogos(HttpSession session, Model model) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);

        shellModelBuilder.aplicar(model, sesion, "catalogos.html", "Catálogos técnicos",
                "Tipos de notificación, evento y audiencia");

        model.addAttribute("tiposNotificacion", catalogoTecnicoService.listarTiposNotificacion());
        model.addAttribute("tiposEvento", catalogoTecnicoService.listarTiposEvento());
        model.addAttribute("tiposAudiencia", catalogoTecnicoService.listarTiposAudiencia());
        return "administrador/catalogos";
    }

    // ---------- tipos_notificacion ----------

    @PostMapping("/catalogos/notificacion")
    public String crearTipoNotificacion(@RequestParam String codigo, @RequestParam String nombre,
                                        @RequestParam(required = false) String descripcion,
                                        HttpSession session, RedirectAttributes redirectAttributes) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        try {
            catalogoTecnicoService.crearTipoNotificacion(codigo, nombre, descripcion, sesion.getUsuarioId());
            redirectAttributes.addFlashAttribute("exito", "Tipo de notificación creado.");
        } catch (OperacionInvalidaException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/administrador/catalogos.html";
    }

    @PostMapping("/catalogos/notificacion/{id}/editar")
    public String editarTipoNotificacion(@PathVariable Long id, @RequestParam String nombre,
                                         @RequestParam(required = false) String descripcion,
                                         HttpSession session, RedirectAttributes redirectAttributes) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        try {
            catalogoTecnicoService.editarTipoNotificacion(id, nombre, descripcion, sesion.getUsuarioId());
            redirectAttributes.addFlashAttribute("exito", "Tipo de notificación actualizado.");
        } catch (OperacionInvalidaException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/administrador/catalogos.html";
    }

    @PostMapping("/catalogos/notificacion/{id}/eliminar")
    public String eliminarTipoNotificacion(@PathVariable Long id, HttpSession session, RedirectAttributes redirectAttributes) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        try {
            catalogoTecnicoService.eliminarTipoNotificacion(id, sesion.getUsuarioId());
            redirectAttributes.addFlashAttribute("exito", "Tipo de notificación eliminado.");
        } catch (OperacionInvalidaException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/administrador/catalogos.html";
    }

    // ---------- tipos_evento ----------

    @PostMapping("/catalogos/evento")
    public String crearTipoEvento(@RequestParam String codigo, @RequestParam String nombre,
                                  @RequestParam(required = false) String descripcion,
                                  @RequestParam(required = false) String color,
                                  HttpSession session, RedirectAttributes redirectAttributes) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        try {
            catalogoTecnicoService.crearTipoEvento(codigo, nombre, descripcion, color, sesion.getUsuarioId());
            redirectAttributes.addFlashAttribute("exito", "Tipo de evento creado.");
        } catch (OperacionInvalidaException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/administrador/catalogos.html";
    }

    @PostMapping("/catalogos/evento/{id}/editar")
    public String editarTipoEvento(@PathVariable Long id, @RequestParam String nombre,
                                   @RequestParam(required = false) String descripcion,
                                   @RequestParam(required = false) String color,
                                   HttpSession session, RedirectAttributes redirectAttributes) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        try {
            catalogoTecnicoService.editarTipoEvento(id, nombre, descripcion, color, sesion.getUsuarioId());
            redirectAttributes.addFlashAttribute("exito", "Tipo de evento actualizado.");
        } catch (OperacionInvalidaException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/administrador/catalogos.html";
    }

    @PostMapping("/catalogos/evento/{id}/eliminar")
    public String eliminarTipoEvento(@PathVariable Long id, HttpSession session, RedirectAttributes redirectAttributes) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        try {
            catalogoTecnicoService.eliminarTipoEvento(id, sesion.getUsuarioId());
            redirectAttributes.addFlashAttribute("exito", "Tipo de evento eliminado.");
        } catch (OperacionInvalidaException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/administrador/catalogos.html";
    }

    // ---------- tipos_audiencia ----------

    @PostMapping("/catalogos/audiencia")
    public String crearTipoAudiencia(@RequestParam String codigo, @RequestParam String nombre,
                                     @RequestParam(required = false) String descripcion,
                                     HttpSession session, RedirectAttributes redirectAttributes) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        try {
            catalogoTecnicoService.crearTipoAudiencia(codigo, nombre, descripcion, sesion.getUsuarioId());
            redirectAttributes.addFlashAttribute("exito", "Tipo de audiencia creado.");
        } catch (OperacionInvalidaException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/administrador/catalogos.html";
    }

    @PostMapping("/catalogos/audiencia/{id}/editar")
    public String editarTipoAudiencia(@PathVariable Long id, @RequestParam String nombre,
                                      @RequestParam(required = false) String descripcion,
                                      HttpSession session, RedirectAttributes redirectAttributes) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        try {
            catalogoTecnicoService.editarTipoAudiencia(id, nombre, descripcion, sesion.getUsuarioId());
            redirectAttributes.addFlashAttribute("exito", "Tipo de audiencia actualizado.");
        } catch (OperacionInvalidaException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/administrador/catalogos.html";
    }

    @PostMapping("/catalogos/audiencia/{id}/eliminar")
    public String eliminarTipoAudiencia(@PathVariable Long id, HttpSession session, RedirectAttributes redirectAttributes) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        try {
            catalogoTecnicoService.eliminarTipoAudiencia(id, sesion.getUsuarioId());
            redirectAttributes.addFlashAttribute("exito", "Tipo de audiencia eliminado.");
        } catch (OperacionInvalidaException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/administrador/catalogos.html";
    }
}