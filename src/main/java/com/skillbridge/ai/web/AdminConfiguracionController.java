package com.skillbridge.ai.web;

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

/**
 * Configuración global (administrador/configuracion.html), real sobre la
 * tabla clave-valor "configuracion_global" (13 parámetros ya sembrados por
 * skillbridge_db_v4.sql). Ver ConfiguracionService y el README: algunos de
 * estos parámetros SÍ tienen efecto real (dominio de registro, duración de
 * sesión, límite de carga y de proyectos simultáneos por colaborador);
 * otros quedan persistidos y editables pero sin un sistema que los consuma
 * todavía en esta entrega (2FA, umbral de auto-aprobación, pesos de AI
 * Talent Matching, resúmenes de IA en foros, asistente IA, idioma/zona
 * horaria) - se declara explícitamente en la plantilla para no aparentar
 * una función que no existe.
 */
@Controller
@RequestMapping("/administrador")
public class AdminConfiguracionController {

    /** Checkboxes: si el navegador no los envía (desmarcados) se guarda "false". */
    private static final List<String> CLAVES_BOOLEANAS = List.of(
            "doble_factor_obligatorio_admin", "asistente_ia_habilitado", "resumenes_ia_foros_activos");

    private static final List<String> CLAVES_TEXTO = List.of(
            "dominio_correo_permitido", "expiracion_sesion_minutos", "limite_carga_colaborador",
            "maximo_proyectos_simultaneos", "auto_aprobar_asignaciones_menores_a", "peso_matching_habilidades",
            "peso_matching_experiencia", "peso_matching_disponibilidad", "idioma_por_defecto", "zona_horaria_defecto");

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
        model.addAttribute("cfg", mapa);
        return "administrador/configuracion";
    }

    @PostMapping("/configuracion")
    public String guardar(@RequestParam Map<String, String> params, HttpSession session, RedirectAttributes redirectAttributes) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        Map<String, ConfiguracionGlobal> actual = configuracionService.obtenerMapa();
        try {
            for (String clave : CLAVES_TEXTO) {
                String nuevo = params.get(clave);
                if (nuevo == null) continue;
                nuevo = nuevo.trim();
                String anterior = configuracionService.valor(actual, clave, "");
                if (!nuevo.equals(anterior)) {
                    configuracionService.actualizar(clave, nuevo, sesion.getUsuarioId(), sesion.getPerfilId());
                }
            }
            for (String clave : CLAVES_BOOLEANAS) {
                String nuevo = String.valueOf(params.containsKey(clave));
                String anterior = configuracionService.valor(actual, clave, "false");
                if (!nuevo.equals(anterior)) {
                    configuracionService.actualizar(clave, nuevo, sesion.getUsuarioId(), sesion.getPerfilId());
                }
            }
            redirectAttributes.addFlashAttribute("exito", "Configuración guardada.");
        } catch (OperacionInvalidaException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/administrador/configuracion.html";
    }
}
