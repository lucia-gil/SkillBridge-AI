package com.skillbridge.ai.controller;

import com.skillbridge.ai.dto.HabilidadPerfilFila;
import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.service.CuentaService;
import com.skillbridge.ai.service.HabilidadService;
import com.skillbridge.ai.service.ResumenProfesionalIaService;
import com.skillbridge.ai.service.ShellModelBuilder;
import com.skillbridge.ai.util.SesionKeys;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

/**
 * Resumen profesional con IA del colaborador (colaborador/resumen-profesional.html).
 * Solo lectura: lo arma la IA automáticamente con las habilidades que el
 * Resource Manager ya validó (ver RmColaboradoresController.validarHabilidad).
 */
@Controller
@RequestMapping("/colaborador")
public class ColaboradorResumenIaController {

    private final ResumenProfesionalIaService resumenService;
    private final HabilidadService habilidadService;
    private final CuentaService cuentaService;
    private final ShellModelBuilder shellModelBuilder;

    public ColaboradorResumenIaController(ResumenProfesionalIaService resumenService, HabilidadService habilidadService,
                                          CuentaService cuentaService, ShellModelBuilder shellModelBuilder) {
        this.resumenService = resumenService;
        this.habilidadService = habilidadService;
        this.cuentaService = cuentaService;
        this.shellModelBuilder = shellModelBuilder;
    }

    @GetMapping("/resumen-profesional.html")
    public String ver(HttpSession session, Model model) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        Long perfilId = sesion.getPerfilId();
        List<HabilidadPerfilFila> habilidades = habilidadService.habilidadesConValidacion(perfilId);

        shellModelBuilder.aplicar(model, sesion, "resumen-profesional.html", "Resumen profesional",
                "Se arma con IA a partir de tus habilidades validadas");
        model.addAttribute("resumen", cuentaService.obtenerResumen(perfilId, sesion.getRolEfectivo()));
        model.addAttribute("resumenIa", resumenService.obtener(perfilId).orElse(null));
        model.addAttribute("actualizando", resumenService.estaActualizando(perfilId));
        model.addAttribute("totalHabilidades", habilidades.size());
        List<HabilidadPerfilFila> validadas = habilidades.stream().filter(HabilidadPerfilFila::isValidada).toList();
        model.addAttribute("habilidadesValidadas", validadas);
        model.addAttribute("totalValidadas", validadas.size());
        model.addAttribute("pendientesConConstancia", habilidades.stream()
                .filter(h -> !h.isValidada() && !h.getCertificados().isEmpty()).count());
        return "colaborador/resumen-profesional";
    }
}
