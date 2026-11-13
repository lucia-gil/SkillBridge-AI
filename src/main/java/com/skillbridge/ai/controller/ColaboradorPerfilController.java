package com.skillbridge.ai.controller;

import com.skillbridge.ai.dto.HabilidadFila;
import com.skillbridge.ai.dto.HabilidadPerfilFila;
import com.skillbridge.ai.dto.MiProyectoFila;
import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.service.CuentaService;
import com.skillbridge.ai.service.HabilidadService;
import com.skillbridge.ai.service.ProyectoService;
import com.skillbridge.ai.service.ResumenProfesionalIaService;
import com.skillbridge.ai.service.ShellModelBuilder;
import com.skillbridge.ai.util.OperacionInvalidaException;
import com.skillbridge.ai.util.SesionKeys;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * Mi perfil (colaborador/perfil.html).
 *
 * El mockup mostraba "Área", "Ubicación", "Manager" e "ID colaborador"
 * fabricados: la tabla "perfiles" del esquema v4 no tiene esas columnas
 * (ver Perfil.java) - el panel "Datos de colaborador" se redujo a los
 * campos que sí existen (cargo, disponibilidad, experiencia, correo,
 * fecha de alta).
 */
@Controller
@RequestMapping("/colaborador")
public class ColaboradorPerfilController {

    private final ProyectoService proyectoService;
    private final HabilidadService habilidadService;
    private final CuentaService cuentaService;
    private final ShellModelBuilder shellModelBuilder;
    private final ResumenProfesionalIaService resumenProfesionalIaService;

    public ColaboradorPerfilController(ProyectoService proyectoService, HabilidadService habilidadService,
                                       CuentaService cuentaService, ShellModelBuilder shellModelBuilder,
                                       ResumenProfesionalIaService resumenProfesionalIaService) {
        this.proyectoService = proyectoService;
        this.habilidadService = habilidadService;
        this.cuentaService = cuentaService;
        this.shellModelBuilder = shellModelBuilder;
        this.resumenProfesionalIaService = resumenProfesionalIaService;
    }

    @GetMapping("/perfil.html")
    public String perfil(HttpSession session, Model model) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        Long perfilId = sesion.getPerfilId();

        List<HabilidadPerfilFila> habilidades = habilidadService.misHabilidades(perfilId);
        List<MiProyectoFila> historial = proyectoService.misProyectosHistorial(perfilId);
        List<MiProyectoFila> proyectosActivos = proyectoService.misProyectosActivos(perfilId);
        List<HabilidadFila> catalogo = habilidadService.listar();

        shellModelBuilder.aplicar(model, sesion, "perfil.html", "Mi perfil",
                "Colaboradores › " + sesion.getNombreCompleto());

        model.addAttribute("resumen", cuentaService.obtenerResumen(perfilId, sesion.getRolEfectivo()));
        model.addAttribute("habilidades", habilidades);
        model.addAttribute("historial", historial);
        model.addAttribute("proyectosActivos", proyectosActivos);
        model.addAttribute("cargaActiva", proyectoService.cargaActivaDe(perfilId));
        model.addAttribute("limiteCarga", proyectoService.limiteCargaConfigurado());
        model.addAttribute("catalogoHabilidades", catalogo);
        // Tarjeta "Resumen profesional con IA" arriba de las habilidades.
        model.addAttribute("resumenIa", resumenProfesionalIaService.obtener(perfilId).orElse(null));
        model.addAttribute("totalConstancias", habilidades.stream().mapToInt(h -> h.getCertificados().size()).sum());
        return "colaborador/perfil";
    }

    @PostMapping("/perfil/habilidades")
    public String agregarHabilidad(@RequestParam Long habilidadId, @RequestParam String nivel,
                                   @RequestParam(required = false) String nombreArchivo,
                                   @RequestParam(required = false) String urlArchivo,
                                   @RequestParam(required = false) MultipartFile archivo,
                                   HttpSession session, RedirectAttributes redirectAttributes) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        try {
            habilidadService.agregarAlPerfil(sesion.getPerfilId(), habilidadId, nivel, sesion.getUsuarioId(),
                    nombreArchivo, urlArchivo, archivo);
            redirectAttributes.addFlashAttribute("exito", "Habilidad agregada a tu perfil.");
        } catch (OperacionInvalidaException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/colaborador/perfil.html";
    }

    /**
     * Editar (o quitar, dejando urlArchivo vacío) la constancia de UNA
     * habilidad ya declarada - lo que faltaba para poder ver/editar el
     * link después de haberlo cargado la primera vez.
     */
    @PostMapping("/perfil/habilidades/{habilidadId}/certificado")
    public String editarCertificado(@PathVariable Long habilidadId,
                                    @RequestParam(required = false) String nombreArchivo,
                                    @RequestParam(required = false) String urlArchivo,
                                    @RequestParam(required = false) MultipartFile archivo,
                                    @RequestParam(defaultValue = "false") boolean quitarArchivo,
                                    HttpSession session, RedirectAttributes redirectAttributes) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        try {
            habilidadService.actualizarCertificado(sesion.getPerfilId(), habilidadId, nombreArchivo, urlArchivo,
                    archivo, quitarArchivo, sesion.getUsuarioId());
            redirectAttributes.addFlashAttribute("exito", "Constancia actualizada.");
            actualizarResumenSiCambio(sesion.getPerfilId());
        } catch (OperacionInvalidaException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/colaborador/perfil.html";
    }

    /** Edita solo el nivel de una habilidad ya declarada (completa el CRUD). */
    @PostMapping("/perfil/habilidades/{habilidadId}/nivel")
    public String editarNivel(@PathVariable Long habilidadId, @RequestParam String nivel,
                              HttpSession session, RedirectAttributes redirectAttributes) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        try {
            habilidadService.editarNivelDePerfil(sesion.getPerfilId(), habilidadId, nivel, sesion.getUsuarioId());
            redirectAttributes.addFlashAttribute("exito", "Nivel actualizado.");
            actualizarResumenSiCambio(sesion.getPerfilId());
        } catch (OperacionInvalidaException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/colaborador/perfil.html";
    }

    /**
     * Si el cambio tocó una habilidad ya validada (cambió su nivel o su
     * constancia, o la quitó), el Resumen profesional con IA se vuelve a
     * armar solo, en segundo plano, con lo que siga validado.
     */
    private void actualizarResumenSiCambio(Long perfilId) {
        resumenProfesionalIaService.obtener(perfilId)
                .filter(com.skillbridge.ai.dto.ResumenProfesionalIa::isDesactualizado)
                .ifPresent(r -> resumenProfesionalIaService.actualizarEnSegundoPlano(perfilId));
    }

    /** Quita una habilidad declarada del perfil (y su constancia, en cascada). */
    @PostMapping("/perfil/habilidades/{habilidadId}/eliminar")
    public String eliminarHabilidad(@PathVariable Long habilidadId, HttpSession session, RedirectAttributes redirectAttributes) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        try {
            habilidadService.eliminarDePerfil(sesion.getPerfilId(), habilidadId, sesion.getUsuarioId());
            redirectAttributes.addFlashAttribute("exito", "Habilidad eliminada de tu perfil.");
            actualizarResumenSiCambio(sesion.getPerfilId());
        } catch (OperacionInvalidaException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/colaborador/perfil.html";
    }
}
