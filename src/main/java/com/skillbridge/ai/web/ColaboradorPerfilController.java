package com.skillbridge.ai.web;

import com.skillbridge.ai.dto.HabilidadFila;
import com.skillbridge.ai.dto.HabilidadPerfilFila;
import com.skillbridge.ai.dto.MiProyectoFila;
import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.service.CuentaService;
import com.skillbridge.ai.service.HabilidadService;
import com.skillbridge.ai.service.ProyectoService;
import com.skillbridge.ai.service.ShellModelBuilder;
import com.skillbridge.ai.util.OperacionInvalidaException;
import com.skillbridge.ai.util.SesionKeys;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    public ColaboradorPerfilController(ProyectoService proyectoService, HabilidadService habilidadService,
                                        CuentaService cuentaService, ShellModelBuilder shellModelBuilder) {
        this.proyectoService = proyectoService;
        this.habilidadService = habilidadService;
        this.cuentaService = cuentaService;
        this.shellModelBuilder = shellModelBuilder;
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
        model.addAttribute("catalogoHabilidades", catalogo);
        return "colaborador/perfil";
    }

    @PostMapping("/perfil/habilidades")
    public String agregarHabilidad(@RequestParam Long habilidadId, @RequestParam String nivel,
                                    HttpSession session, RedirectAttributes redirectAttributes) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        try {
            habilidadService.agregarAlPerfil(sesion.getPerfilId(), habilidadId, nivel, sesion.getUsuarioId());
            redirectAttributes.addFlashAttribute("exito", "Habilidad agregada a tu perfil.");
        } catch (OperacionInvalidaException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/colaborador/perfil.html";
    }
}
