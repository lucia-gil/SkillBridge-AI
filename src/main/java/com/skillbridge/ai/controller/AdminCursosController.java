package com.skillbridge.ai.controller;

import com.skillbridge.ai.dto.CursoRequest;
import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.repository.CategoriaHabilidadRepository;
import com.skillbridge.ai.repository.HabilidadRepository;
import com.skillbridge.ai.service.CursoService;
import com.skillbridge.ai.service.ShellModelBuilder;
import com.skillbridge.ai.util.OperacionInvalidaException;
import com.skillbridge.ai.util.SesionKeys;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/administrador")
public class AdminCursosController {
    private final CursoService cursos;
    private final CategoriaHabilidadRepository categorias;
    private final HabilidadRepository habilidades;
    private final ShellModelBuilder shell;
    public AdminCursosController(CursoService cursos, CategoriaHabilidadRepository categorias,
                                 HabilidadRepository habilidades, ShellModelBuilder shell) {
        this.cursos = cursos; this.categorias = categorias; this.habilidades = habilidades; this.shell = shell;
    }
    @GetMapping("/cursos.html")
    public String listar(@RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "") String estado,
                         @RequestParam(defaultValue = "0") int page, HttpSession session, Model model) {
        UsuarioSesion usuario = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        shell.aplicar(model, usuario, "cursos.html", "Cursos", "Catálogo de formación y habilidades");
        model.addAttribute("pagina", cursos.buscar(q, estado, PageRequest.of(Math.max(0, page), 12, Sort.by("titulo"))));
        model.addAttribute("categorias", categorias.findAll());
        model.addAttribute("habilidades", habilidades.findAllConCategoriaOrderByNombre());
        model.addAttribute("q", q);
        model.addAttribute("estado", estado);
        return "administrador/cursos";
    }
    @PostMapping("/cursos")
    public String crear(@Valid CursoRequest request, BindingResult errors, HttpSession session, RedirectAttributes flash) {
        return guardar(null, request, errors, session, flash);
    }
    @PostMapping("/cursos/{id}/editar")
    public String editar(@PathVariable Long id, @Valid CursoRequest request, BindingResult errors,
                         HttpSession session, RedirectAttributes flash) {
        return guardar(id, request, errors, session, flash);
    }
    private String guardar(Long id, CursoRequest request, BindingResult errors, HttpSession session, RedirectAttributes flash) {
        if (errors.hasErrors()) {
            flash.addFlashAttribute("error", errors.getFieldErrors().get(0).getField() + ": " + errors.getFieldErrors().get(0).getDefaultMessage());
        } else try {
            cursos.guardar(id, request, ((UsuarioSesion) session.getAttribute(SesionKeys.USUARIO)).getUsuarioId());
            flash.addFlashAttribute("exito", "Curso guardado.");
        } catch (OperacionInvalidaException ex) { flash.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/administrador/cursos.html";
    }
    @PostMapping("/cursos/{id}/estado")
    public String estado(@PathVariable Long id, @RequestParam String estado, HttpSession session, RedirectAttributes flash) {
        try {
            cursos.cambiarEstado(id, estado, ((UsuarioSesion) session.getAttribute(SesionKeys.USUARIO)).getUsuarioId());
            flash.addFlashAttribute("exito", "Estado actualizado.");
        } catch (OperacionInvalidaException ex) { flash.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/administrador/cursos.html";
    }
}
