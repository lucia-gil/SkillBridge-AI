package com.skillbridge.ai.controller;

import com.skillbridge.ai.dto.UsuarioFila;
import com.skillbridge.ai.dto.CorreoAutorizadoFila;
import com.skillbridge.ai.service.IdentidadDniService;
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
    private final IdentidadDniService identidadDni;

    public UsuariosController(UsuarioService usuarioService, ShellModelBuilder shellModelBuilder, IdentidadDniService identidadDni) {
        this.usuarioService = usuarioService;
        this.shellModelBuilder = shellModelBuilder;
        this.identidadDni = identidadDni;
    }

    @GetMapping("/usuarios.html")
    public String usuarios(HttpSession session, Model model) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);

        List<UsuarioFila> usuarios = usuarioService.listar();
        Map<String, Long> kpis = usuarioService.kpis();

        shellModelBuilder.aplicar(model, sesion, "usuarios.html", "Usuarios y roles",
                usuarios.size() + " cuentas registradas");

        model.addAttribute("usuarios", usuarios);
        model.addAttribute("kpis", kpis);
        var invitaciones = usuarioService.listarAutorizados();
        model.addAttribute("correosAutorizados", invitaciones);
        model.addAttribute("padronDisponible", identidadDni.disponible());
        model.addAttribute("usuariosVerificados", identidadDni.usuariosVerificados(usuarios.stream().map(UsuarioFila::getId).toList()));
        model.addAttribute("invitacionesConDni", identidadDni.invitacionesReservadas(invitaciones.stream()
                .filter(c -> !c.utilizado()).map(CorreoAutorizadoFila::id).toList()));
        // Para ocultar en la tabla las acciones sobre la propia cuenta (modelo v7).
        model.addAttribute("usuarioActualId", sesion.getUsuarioId());
        return "administrador/usuarios";
    }

    @PostMapping("/usuarios/invitar")
    public String invitar(@RequestParam String correo,
                          // Modelo v7: rol con el que nacerá la cuenta (NINGUNO = Miembro).
                          @RequestParam(required = false) String rol,
                          @RequestParam(required = false) String dni,
                          HttpSession session, RedirectAttributes redirectAttributes) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        try {
            usuarioService.invitar(correo, rol, dni, sesion.getUsuarioId());
            redirectAttributes.addFlashAttribute("exito", "Correo autorizado. Invitación en cola para " + correo + ".");
        } catch (OperacionInvalidaException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/administrador/usuarios.html";
    }

    @PostMapping("/usuarios/{id}/rol")
    public String cambiarRol(@PathVariable Long id, @RequestParam String rol,
                              @RequestParam(required = false) String motivo,
                              // true cuando el Admin confirmó que sus asignaciones como colaborador se finalizan.
                              @RequestParam(defaultValue = "false") boolean confirmarSalida,
                              HttpSession session, RedirectAttributes redirectAttributes) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        try {
            usuarioService.cambiarRol(id, rol, motivo, confirmarSalida, sesion.getUsuarioId());
            redirectAttributes.addFlashAttribute("exito", "Rol actualizado.");
        } catch (OperacionInvalidaException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/administrador/usuarios.html";
    }

    @PostMapping("/usuarios/{id}/estado")
    public String cambiarEstado(@PathVariable Long id, @RequestParam boolean activar,
                                 @RequestParam(required = false) String motivo,
                                 @RequestParam(defaultValue = "false") boolean confirmarSalida,
                                 HttpSession session, RedirectAttributes redirectAttributes) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        try {
            usuarioService.cambiarEstado(id, activar, motivo, confirmarSalida, sesion.getUsuarioId());
            redirectAttributes.addFlashAttribute("exito", activar ? "Usuario reactivado." : "Usuario suspendido.");
        } catch (OperacionInvalidaException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/administrador/usuarios.html";
    }

    @PostMapping("/usuarios/{id}/eliminar")
    public String eliminar(@PathVariable Long id, @RequestParam(required = false) String motivo,
                           HttpSession session, RedirectAttributes redirectAttributes) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        try {
            usuarioService.eliminar(id, motivo, sesion.getUsuarioId());
            redirectAttributes.addFlashAttribute("exito", "Usuario eliminado.");
        } catch (OperacionInvalidaException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/administrador/usuarios.html";
    }

    @PostMapping("/usuarios/correos-autorizados/{id}/reenviar")
    public String reenviar(@PathVariable Long id, HttpSession session, RedirectAttributes flash) {
        UsuarioSesion actor = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        try {
            usuarioService.reenviarInvitacion(id, actor.getUsuarioId());
            flash.addFlashAttribute("exito", "Invitación puesta en cola para reenvío.");
        } catch (OperacionInvalidaException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/administrador/usuarios.html#invitaciones";
    }

    /** Cambiar el rol de una invitación todavía no usada (modelo v7). */
    @PostMapping("/usuarios/correos-autorizados/{id}/rol")
    public String cambiarRolInvitacion(@PathVariable Long id, @RequestParam String rol,
                                       HttpSession session, RedirectAttributes redirectAttributes) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        try {
            usuarioService.cambiarRolInvitacion(id, rol, sesion.getUsuarioId());
            redirectAttributes.addFlashAttribute("exito", "Rol de la invitación actualizado.");
        } catch (OperacionInvalidaException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/administrador/usuarios.html";
    }

    @PostMapping("/usuarios/correos-autorizados/{id}/eliminar")
    public String revocarAutorizacion(@PathVariable Long id, HttpSession session, RedirectAttributes redirectAttributes) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        try {
            usuarioService.revocarAutorizacion(id, sesion.getUsuarioId());
            redirectAttributes.addFlashAttribute("exito", "Autorización revocada.");
        } catch (OperacionInvalidaException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/administrador/usuarios.html";
    }
}
