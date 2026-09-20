package com.skillbridge.ai.web;

import com.skillbridge.ai.dto.CuentaResumen;
import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.model.Usuario;
import com.skillbridge.ai.service.CuentaService;
import com.skillbridge.ai.service.ShellModelBuilder;
import com.skillbridge.ai.util.OperacionInvalidaException;
import com.skillbridge.ai.util.Roles;
import com.skillbridge.ai.util.SesionKeys;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.concurrent.TimeUnit;

/**
 * "Mi cuenta" (administrador/mi-cuenta.html y colaborador/mi-cuenta.html):
 * un único controlador para ambas rutas.
 */
@Controller
public class CuentaController {

    private final CuentaService cuentaService;
    private final ShellModelBuilder shellModelBuilder;

    public CuentaController(
            CuentaService cuentaService,
            ShellModelBuilder shellModelBuilder) {

        this.cuentaService = cuentaService;
        this.shellModelBuilder = shellModelBuilder;
    }

    @GetMapping({
            "/administrador/mi-cuenta.html",
            "/colaborador/mi-cuenta.html",
            "/resource-manager/mi-cuenta.html",
            "/project-manager/mi-cuenta.html"
    })
    public String miCuenta(
            HttpSession session,
            Model model) {

        UsuarioSesion sesion =
                (UsuarioSesion) session.getAttribute(
                        SesionKeys.USUARIO
                );

        String volver =
                "/" + Roles.slug(sesion.getRolEfectivo())
                        + "/mi-cuenta.html";

        CuentaResumen resumen =
                cuentaService.obtenerResumen(
                        sesion.getPerfilId(),
                        sesion.getRolEfectivo()
                );

        shellModelBuilder.aplicar(
                model,
                sesion,
                "",
                "Mi cuenta",
                "Datos personales y seguridad"
        );

        model.addAttribute("resumen", resumen);
        model.addAttribute(
                "esAdministrador",
                Roles.ADMINISTRADOR.equals(
                        sesion.getRolEfectivo()
                )
        );
        model.addAttribute("volver", volver);

        return Roles.slug(sesion.getRolEfectivo())
                + "/mi-cuenta";
    }

    /**
     * Devuelve la fotografía del usuario autenticado.
     *
     * El navegador la consume mediante:
     * /perfil/foto
     */
    @GetMapping("/perfil/foto")
    public ResponseEntity<byte[]> obtenerFoto(
            HttpSession session) {

        UsuarioSesion sesion =
                (UsuarioSesion) session.getAttribute(
                        SesionKeys.USUARIO
                );

        if (sesion == null || sesion.getUsuarioId() == null) {
            return ResponseEntity.notFound().build();
        }

        try {
            Usuario usuario =
                    cuentaService.obtenerFoto(
                            sesion.getUsuarioId()
                    );

            if (!usuario.tieneFotoPerfil()) {
                return ResponseEntity.notFound().build();
            }

            MediaType mediaType;

            try {
                mediaType = MediaType.parseMediaType(
                        usuario.getFotoPerfilTipo()
                );
            } catch (Exception ex) {
                mediaType = MediaType.IMAGE_JPEG;
            }

            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .cacheControl(
                            CacheControl.noCache()
                    )
                    .header(
                            HttpHeaders.CONTENT_DISPOSITION,
                            "inline"
                    )
                    .body(usuario.getFotoPerfil());

        } catch (OperacionInvalidaException ex) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Sube o reemplaza la fotografia del usuario autenticado.
     */
    @PostMapping("/cuenta/foto")
    public String actualizarFoto(
            @RequestParam("foto") MultipartFile foto,
            @RequestParam String volver,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        UsuarioSesion sesion =
                (UsuarioSesion) session.getAttribute(
                        SesionKeys.USUARIO
                );

        try {
            cuentaService.actualizarFoto(
                    sesion.getUsuarioId(),
                    foto
            );

            redirectAttributes.addFlashAttribute(
                    "exito",
                    "Tu fotografía de perfil se actualizó correctamente."
            );

        } catch (OperacionInvalidaException ex) {

            redirectAttributes.addFlashAttribute(
                    "error",
                    ex.getMessage()
            );
        }

        return "redirect:" + rutaSegura(volver);
    }

    /**
     * Elimina la fotografía del usuario autenticado.
     */
    @PostMapping("/cuenta/foto/eliminar")
    public String eliminarFoto(
            @RequestParam String volver,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        UsuarioSesion sesion =
                (UsuarioSesion) session.getAttribute(
                        SesionKeys.USUARIO
                );

        try {
            cuentaService.eliminarFoto(
                    sesion.getUsuarioId()
            );

            redirectAttributes.addFlashAttribute(
                    "exito",
                    "Tu fotografía de perfil fue eliminada."
            );

        } catch (OperacionInvalidaException ex) {

            redirectAttributes.addFlashAttribute(
                    "error",
                    ex.getMessage()
            );
        }

        return "redirect:" + rutaSegura(volver);
    }

    @PostMapping("/cuenta/datos")
    public String actualizarDatos(
            @RequestParam(required = false) String cargo,
            @RequestParam(required = false) String biografia,
            @RequestParam(defaultValue = "0") int experienciaAnios,
            @RequestParam String volver,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        UsuarioSesion sesion =
                (UsuarioSesion) session.getAttribute(
                        SesionKeys.USUARIO
                );

        try {

            cuentaService.actualizarDatos(
                    sesion.getPerfilId(),
                    cargo,
                    biografia,
                    experienciaAnios,
                    sesion.getUsuarioId()
            );

            redirectAttributes.addFlashAttribute(
                    "exito",
                    "Cambios guardados en tu perfil."
            );

        } catch (OperacionInvalidaException ex) {

            redirectAttributes.addFlashAttribute(
                    "error",
                    ex.getMessage()
            );
        }

        return "redirect:" + rutaSegura(volver);
    }

    @PostMapping("/cuenta/contrasena")
    public String cambiarContrasena(
            @RequestParam String actual,
            @RequestParam String nueva,
            @RequestParam String nueva2,
            @RequestParam String volver,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        UsuarioSesion sesion =
                (UsuarioSesion) session.getAttribute(
                        SesionKeys.USUARIO
                );

        try {

            cuentaService.cambiarContrasena(
                    sesion.getUsuarioId(),
                    actual,
                    nueva,
                    nueva2,
                    sesion.getUsuarioId()
            );

            redirectAttributes.addFlashAttribute(
                    "exito",
                    "Contraseña actualizada correctamente."
            );

        } catch (OperacionInvalidaException ex) {

            redirectAttributes.addFlashAttribute(
                    "error",
                    ex.getMessage()
            );
        }

        return "redirect:" + rutaSegura(volver);
    }

    /** Evita un open-redirect. */
    private String rutaSegura(String volver) {

        if ("/administrador/mi-cuenta.html".equals(volver)
                || "/colaborador/mi-cuenta.html".equals(volver)
                || "/resource-manager/mi-cuenta.html".equals(volver)
                || "/project-manager/mi-cuenta.html".equals(volver)) {

            return volver;
        }

        return "/auth/login.html";
    }
}