package com.skillbridge.ai.controller;

import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.service.FusionHabilidadService;
import com.skillbridge.ai.service.ShellModelBuilder;
import com.skillbridge.ai.util.OperacionInvalidaException;
import com.skillbridge.ai.util.SesionKeys;
import jakarta.servlet.http.HttpSession;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.Serializable;
import java.util.UUID;

@Controller
public class AdminFusionHabilidadController {
    private static final String REVISION = "admin.habilidades.fusion";
    private static final String PAGINA = "redirect:/administrador/habilidades/fusionar.html";

    private final FusionHabilidadService fusiones;
    private final ShellModelBuilder shell;

    private record Revision(String token, Long actor, FusionHabilidadService.Vista vista) implements Serializable {}

    public AdminFusionHabilidadController(FusionHabilidadService fusiones, ShellModelBuilder shell) {
        this.fusiones = fusiones;
        this.shell = shell;
    }

    @GetMapping("/administrador/habilidades/fusionar.html")
    public String pagina(@RequestParam(required = false) Long origen, @RequestParam(required = false) Long destino,
                         HttpSession session, Model model) {
        UsuarioSesion actor = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        shell.aplicar(model, actor, "habilidades.html", "Fusionar habilidades", "Consolida duplicados del catálogo");
        model.addAttribute("opciones", fusiones.opciones());
        model.addAttribute("origen", origen);
        model.addAttribute("destino", destino);
        session.removeAttribute(REVISION);
        if (origen != null || destino != null) {
            try {
                var vista = fusiones.revisar(origen, destino);
                var revision = new Revision(UUID.randomUUID().toString(), actor.getUsuarioId(), vista);
                session.setAttribute(REVISION, revision);
                model.addAttribute("vista", vista);
                model.addAttribute("token", revision.token());
            } catch (OperacionInvalidaException ex) {
                if (!model.containsAttribute("error")) model.addAttribute("error", ex.getMessage());
            }
        }
        return "administrador/habilidades-fusion";
    }

    @PostMapping("/administrador/habilidades/fusionar")
    public String fusionar(@RequestParam String token, @RequestParam(defaultValue = "false") boolean confirmar,
                           HttpSession session, RedirectAttributes flash) {
        UsuarioSesion actor = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        var revision = (Revision) session.getAttribute(REVISION);
        if (!confirmar || revision == null || !revision.token().equals(token) || !revision.actor().equals(actor.getUsuarioId())) {
            flash.addFlashAttribute("error", "Selecciona las habilidades y revisa el impacto antes de confirmar.");
            return PAGINA;
        }

        session.removeAttribute(REVISION);
        try {
            fusiones.fusionar(revision.vista(), actor.getUsuarioId());
            flash.addFlashAttribute("exito", "Se fusionaron las habilidades en " + revision.vista().destino().nombre() + ".");
            return "redirect:/administrador/habilidades.html";
        } catch (OperacionInvalidaException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        } catch (DataIntegrityViolationException | ConcurrencyFailureException ex) {
            flash.addFlashAttribute("error", "Los datos cambiaron durante la operación. Revisa el impacto e intenta de nuevo.");
        }
        flash.addAttribute("origen", revision.vista().origen().id());
        flash.addAttribute("destino", revision.vista().destino().id());
        return PAGINA;
    }
}
