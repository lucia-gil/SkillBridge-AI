package com.skillbridge.ai.controller;

import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.model.ConfiguracionGlobal;
import com.skillbridge.ai.service.ConfiguracionService;
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
import java.util.Map;

@Controller
@RequestMapping("/administrador")
public class AdminConfiguracionController {
    @org.springframework.beans.factory.annotation.Autowired
    private com.skillbridge.ai.service.AsistenteIaService proveedor;

    @org.springframework.beans.factory.annotation.Autowired
    private com.skillbridge.ai.service.AsistenteConversacionService asistente;


    private final ConfiguracionService configuracionService;
    private final ShellModelBuilder shellModelBuilder;

    public AdminConfiguracionController(ConfiguracionService configuracionService, ShellModelBuilder shellModelBuilder) {
        this.configuracionService = configuracionService;
        this.shellModelBuilder = shellModelBuilder;
    }

    @GetMapping("/configuracion.html")
    public String configuracion(HttpSession session, Model model) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        Map<String, ConfiguracionGlobal> mapa = configuracionService.obtenerMapa();

        shellModelBuilder.aplicar(model, sesion, "configuracion.html", "Configuración",
                "Ajustes generales de la plataforma (persistidos en configuracion_global)");
        model.addAttribute("zonaServidor", java.time.ZoneId.systemDefault().getId());
        model.addAttribute("proveedorConfigurado", proveedor.tieneClaude());
        model.addAttribute("motivo", asistente.motivoIndisponible());
        model.addAttribute("cfg", mapa);
        return "administrador/configuracion";
    }

    @PostMapping("/configuracion")
    public String guardar(@RequestParam Map<String, String> params, HttpSession session, RedirectAttributes redirectAttributes) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        try {
            configuracionService.guardarFormulario(params, sesion.getUsuarioId(), sesion.getPerfilId());
            redirectAttributes.addFlashAttribute("exito", "Configuración guardada.");
        } catch (OperacionInvalidaException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/administrador/configuracion.html";
    }
}
