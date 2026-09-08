package com.skillbridge.ai.web;

import com.skillbridge.ai.dto.CuentaResumen;
import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.service.CuentaService;
import com.skillbridge.ai.service.ShellModelBuilder;
import com.skillbridge.ai.util.OperacionInvalidaException;
import com.skillbridge.ai.util.Roles;
import com.skillbridge.ai.util.SesionKeys;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * "Mi cuenta" (administrador/mi-cuenta.html y colaborador/mi-cuenta.html):
 * un único controlador para ambas rutas, porque el contenido es el mismo
 * salvo un par de textos de alcance de rol - ver Javadoc de CuentaService
 * para qué partes del mockup original (2FA, sesiones fabricadas, aviso de
 * login) se dejaron fuera por no tener soporte real en el esquema.
 */
@Controller
public class CuentaController {

    private final CuentaService cuentaService;
    private final ShellModelBuilder shellModelBuilder;

    public CuentaController(CuentaService cuentaService, ShellModelBuilder shellModelBuilder) {
        this.cuentaService = cuentaService;
        this.shellModelBuilder = shellModelBuilder;
    }

    @GetMapping({"/administrador/mi-cuenta.html", "/colaborador/mi-cuenta.html"})
    public String miCuenta(HttpSession session, Model model) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        String volver = "/" + Roles.slug(sesion.getRolEfectivo()) + "/mi-cuenta.html";

        CuentaResumen resumen = cuentaService.obtenerResumen(sesion.getPerfilId(), sesion.getRolEfectivo());

        // "Mi cuenta" no vive en el sidebar (se accede desde el menú de usuario, ver fragments/modals.html), así que ningún item del nav queda activo.
        shellModelBuilder.aplicar(model, sesion, "", "Mi cuenta", "Datos personales y seguridad");
        model.addAttribute("resumen", resumen);
        model.addAttribute("esAdministrador", Roles.ADMINISTRADOR.equals(sesion.getRolEfectivo()));
        model.addAttribute("volver", volver);
        return Roles.slug(sesion.getRolEfectivo()) + "/mi-cuenta";
    }

    @PostMapping("/cuenta/datos")
    public String actualizarDatos(@RequestParam(required = false) String cargo,
                                   @RequestParam(required = false) String biografia,
                                   @RequestParam(defaultValue = "0") int experienciaAnios,
                                   @RequestParam String volver,
                                   HttpSession session, RedirectAttributes redirectAttributes) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        try {
            cuentaService.actualizarDatos(sesion.getPerfilId(), cargo, biografia, experienciaAnios, sesion.getUsuarioId());
            redirectAttributes.addFlashAttribute("exito", "Cambios guardados en tu perfil.");
        } catch (OperacionInvalidaException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:" + rutaSegura(volver);
    }

    @PostMapping("/cuenta/contrasena")
    public String cambiarContrasena(@RequestParam String actual, @RequestParam String nueva, @RequestParam String nueva2,
                                     @RequestParam String volver, HttpSession session, RedirectAttributes redirectAttributes) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        try {
            cuentaService.cambiarContrasena(sesion.getUsuarioId(), actual, nueva, nueva2, sesion.getUsuarioId());
            redirectAttributes.addFlashAttribute("exito", "Contraseña actualizada correctamente.");
        } catch (OperacionInvalidaException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:" + rutaSegura(volver);
    }

    /** Evita un open-redirect: solo se acepta volver a una de las 2 rutas reales de "Mi cuenta". */
    private String rutaSegura(String volver) {
        if ("/administrador/mi-cuenta.html".equals(volver) || "/colaborador/mi-cuenta.html".equals(volver)) {
            return volver;
        }
        return "/auth/login.html";
    }
}
