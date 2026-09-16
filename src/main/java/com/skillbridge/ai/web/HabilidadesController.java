package com.skillbridge.ai.web;

import com.skillbridge.ai.dto.CategoriaConConteo;
import com.skillbridge.ai.dto.HabilidadFila;
import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.model.CategoriaHabilidad;
import com.skillbridge.ai.service.HabilidadService;
import com.skillbridge.ai.service.ShellModelBuilder;
import com.skillbridge.ai.util.OperacionInvalidaException;
import com.skillbridge.ai.util.SesionKeys;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/** CRUD completo del catálogo de habilidades (administrador/habilidades.html). */
@Controller
@RequestMapping("/administrador")
public class HabilidadesController {

    private final HabilidadService habilidadService;
    private final ShellModelBuilder shellModelBuilder;

    public HabilidadesController(HabilidadService habilidadService, ShellModelBuilder shellModelBuilder) {
        this.habilidadService = habilidadService;
        this.shellModelBuilder = shellModelBuilder;
    }

    @GetMapping("/habilidades.html")
    public String habilidades(HttpSession session, Model model) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);

        List<HabilidadFila> habilidades = habilidadService.listar();
        List<CategoriaConConteo> categorias = habilidadService.categoriasConConteo();
        List<CategoriaHabilidad> categoriasParaSelect = habilidadService.categorias();

        shellModelBuilder.aplicar(model, sesion, "habilidades.html", "Catálogo de habilidades",
                habilidades.size() + " habilidades · " + categorias.size() + " categorías");

        model.addAttribute("habilidades", habilidades);
        model.addAttribute("categorias", categorias);
        model.addAttribute("categoriasParaSelect", categoriasParaSelect);
        return "administrador/habilidades";
    }

    @PostMapping("/habilidades")
    public String crear(@RequestParam String nombre, @RequestParam Long categoriaId,
                         HttpSession session, RedirectAttributes redirectAttributes) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        try {
            habilidadService.crear(nombre, categoriaId, sesion.getUsuarioId());
            redirectAttributes.addFlashAttribute("exito", "\"" + nombre.trim() + "\" se agregó al catálogo.");
        } catch (OperacionInvalidaException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/administrador/habilidades.html";
    }

    @PostMapping("/habilidades/{id}/editar")
    public String editar(@PathVariable Long id, @RequestParam String nombre, @RequestParam Long categoriaId,
                          HttpSession session, RedirectAttributes redirectAttributes) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        try {
            habilidadService.editar(id, nombre, categoriaId, sesion.getUsuarioId());
            redirectAttributes.addFlashAttribute("exito", "Habilidad actualizada.");
        } catch (OperacionInvalidaException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/administrador/habilidades.html";
    }

    @PostMapping("/habilidades/{id}/eliminar")
    public String eliminar(@PathVariable Long id, HttpSession session, RedirectAttributes redirectAttributes) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        try {
            habilidadService.eliminar(id, sesion.getUsuarioId());
            redirectAttributes.addFlashAttribute("exito", "Habilidad eliminada.");
        } catch (OperacionInvalidaException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/administrador/habilidades.html";
    }
}
