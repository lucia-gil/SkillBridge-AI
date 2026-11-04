package com.skillbridge.ai.controller;

import com.skillbridge.ai.dto.CursoVista;
import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.repository.PerfilHabilidadRepository;
import com.skillbridge.ai.service.CursoService;
import com.skillbridge.ai.service.ShellModelBuilder;
import com.skillbridge.ai.util.SesionKeys;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

/**
 * Vista "Cursos" de Colaborador, Project Manager y Resource Manager: el
 * catálogo de formación que publica el Administrador (solo lo PUBLICADO).
 * Una sola plantilla (compartido/cursos.html) para los tres roles; el
 * sidebar y el topbar los arma ShellModelBuilder según el área visitada.
 */
@Controller
public class CursosCatalogoController {

    private final CursoService cursoService;
    private final PerfilHabilidadRepository perfilHabilidadRepository;
    private final ShellModelBuilder shell;

    public CursosCatalogoController(CursoService cursoService, PerfilHabilidadRepository perfilHabilidadRepository,
                                    ShellModelBuilder shell) {
        this.cursoService = cursoService;
        this.perfilHabilidadRepository = perfilHabilidadRepository;
        this.shell = shell;
    }

    @GetMapping({"/colaborador/cursos.html", "/project-manager/cursos.html", "/resource-manager/cursos.html"})
    public String catalogo(HttpSession session, Model model) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        shell.aplicar(model, sesion, "cursos.html", "Cursos", "Aprende a tu ritmo con recursos elegidos para el equipo");

        List<CursoVista> catalogo = cursoService.catalogoPublicado();
        // Habilidades que ya declaró: sus recursos se marcan "Para ti" (refuerzo).
        List<Long> misHabilidades = sesion.getPerfilId() == null ? List.of()
                : perfilHabilidadRepository.findById_PerfilId(sesion.getPerfilId()).stream()
                    .map(ph -> ph.getId().getHabilidadId()).toList();

        model.addAttribute("cursos", catalogo);
        model.addAttribute("misHabilidades", misHabilidades);
        model.addAttribute("claveUsuario", sesion.getUsuarioId());
        return "compartido/cursos";
    }
}
