package com.skillbridge.ai.web;

import com.skillbridge.ai.dto.UsuarioFila;
import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.service.ShellModelBuilder;
import com.skillbridge.ai.service.UsuarioService;
import com.skillbridge.ai.util.OperacionInvalidaException;
import com.skillbridge.ai.util.SesionKeys;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

/**
 * CRUD completo de Usuarios y roles (administrador/usuarios.html):
 * listar (con KPIs reales), invitar (correos_autorizados), cambiar rol,
 * suspender/reactivar, eliminar.
 */
@Controller
@RequestMapping("/administrador")
public class UsuariosController {

    private final UsuarioService usuarioService;
    private final ShellModelBuilder shellModelBuilder;

    public UsuariosController(UsuarioService usuarioService, ShellModelBuilder shellModelBuilder) {
        this.usuarioService = usuarioService;
        this.shellModelBuilder = shellModelBuilder;
    }

    @GetMapping("/usuarios.html")
    public String usuarios(HttpSession session, Model model) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);

        List<UsuarioFila> usuarios = usuarioService.listar();
        Map<String, Long> kpis = usuarioService.kpis();

        shellModelBuilder.aplicar(model, sesion, "usuarios.html", "Usuarios y roles",
                usuarios.size() + " cuentas registradas",
                Map.of("usuarios.html", kpis.get("sinRol").intValue()));

        model.addAttribute("usuarios", usuarios);
        model.addAttribute("kpis", kpis);
        return "administrador/usuarios";
    }

    @PostMapping("/usuarios/invitar")
    public String invitar(@RequestParam String correo, HttpSession session, RedirectAttributes redirectAttributes) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        try {
            usuarioService.invitar(correo, sesion.getUsuarioId());
            redirectAttributes.addFlashAttribute("exito", "Invitación enviada a " + correo + ".");
        } catch (OperacionInvalidaException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/administrador/usuarios.html";
    }

    @PostMapping("/usuarios/{id}/rol")
    public String cambiarRol(@PathVariable Long id, @RequestParam String rol,
                              HttpSession session, RedirectAttributes redirectAttributes) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        try {
            usuarioService.cambiarRol(id, rol, sesion.getUsuarioId());
            redirectAttributes.addFlashAttribute("exito", "Rol actualizado.");
        } catch (OperacionInvalidaException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/administrador/usuarios.html";
    }

    @PostMapping("/usuarios/{id}/estado")
    public String cambiarEstado(@PathVariable Long id, @RequestParam boolean activar,
                                 HttpSession session, RedirectAttributes redirectAttributes) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        try {
            usuarioService.cambiarEstado(id, activar, sesion.getUsuarioId());
            redirectAttributes.addFlashAttribute("exito", activar ? "Usuario reactivado." : "Usuario suspendido.");
        } catch (OperacionInvalidaException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/administrador/usuarios.html";
    }

    @PostMapping("/usuarios/{id}/eliminar")
    public String eliminar(@PathVariable Long id, HttpSession session, RedirectAttributes redirectAttributes) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        try {
            usuarioService.eliminar(id, sesion.getUsuarioId());
            redirectAttributes.addFlashAttribute("exito", "Usuario eliminado.");
        } catch (OperacionInvalidaException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/administrador/usuarios.html";
    }
}
