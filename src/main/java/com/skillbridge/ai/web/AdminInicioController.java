package com.skillbridge.ai.web;

import com.skillbridge.ai.dto.EstadoProyectoConConteo;
import com.skillbridge.ai.dto.OcupacionColaboradorFila;
import com.skillbridge.ai.dto.UsuarioFila;
import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.dto.VacanteHabilidad;
import com.skillbridge.ai.service.HabilidadService;
import com.skillbridge.ai.service.ReporteService;
import com.skillbridge.ai.service.ShellModelBuilder;
import com.skillbridge.ai.service.UsuarioService;
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
 * Panel de administración (administrador/inicio.html).
 *
 * El mockup original mostraba una "Salud de la plataforma" (servicios de
 * infraestructura fabricados) y consultas al Asistente IA; ninguna de las
 * dos tiene un dato real detrás en esta entrega (no hay monitoreo de
 * infraestructura ni el chatbot está implementado, ver README) - se
 * reemplazan por "Proyectos por estado" y "Requiere tu atención"
 * (colaboradores sobrecargados, habilidades sin proyecto asociado,
 * usuarios sin rol), todo con datos reales de las tablas ya existentes.
 */
@Controller
@RequestMapping("/administrador")
public class AdminInicioController {

    private final UsuarioService usuarioService;
    private final ReporteService reporteService;
    private final HabilidadService habilidadService;
    private final ShellModelBuilder shellModelBuilder;

    public AdminInicioController(UsuarioService usuarioService, ReporteService reporteService,
                                  HabilidadService habilidadService, ShellModelBuilder shellModelBuilder) {
        this.usuarioService = usuarioService;
        this.reporteService = reporteService;
        this.habilidadService = habilidadService;
        this.shellModelBuilder = shellModelBuilder;
    }

    @GetMapping("/inicio.html")
    public String inicio(HttpSession session, Model model) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);

        Map<String, Long> kpisReporte = reporteService.kpisAdministrador();
        Map<String, Long> kpisUsuarios = usuarioService.kpis();
        List<UsuarioFila> usuarios = usuarioService.listar();
        List<EstadoProyectoConConteo> proyectosPorEstado = reporteService.proyectosPorEstadoDetallado();
        List<OcupacionColaboradorFila> sobrecargados = reporteService.colaboradoresSobrecargados();
        List<VacanteHabilidad> topHabilidades = reporteService.topHabilidadesDeclaradas(5);
        long maxDeclarada = topHabilidades.stream().mapToLong(VacanteHabilidad::vacantes).max().orElse(1);

        shellModelBuilder.aplicar(model, sesion, "inicio.html", "Panel de administración",
                "NexaCorp · SkillBridge AI");

        model.addAttribute("kpiColaboradoresActivos", kpisReporte.get("colaboradoresActivos"));
        model.addAttribute("kpiProyectosEnCurso", kpisReporte.get("proyectosEnCurso"));
        model.addAttribute("kpiOcupacionPromedio", reporteService.ocupacionPromedio());
        model.addAttribute("kpiAsignacionesActivas", kpisReporte.get("asignacionesActivas"));

        model.addAttribute("proyectosPorEstado", proyectosPorEstado);
        model.addAttribute("sobrecargados", sobrecargados);
        model.addAttribute("habilidadesSinAsignar", kpisReporte.get("habilidadesSinAsignar"));
        model.addAttribute("usuariosSinRol", kpisUsuarios.get("sinRol"));

        model.addAttribute("usuariosPreview", usuarios.size() > 6 ? usuarios.subList(0, 6) : usuarios);
        model.addAttribute("totalUsuarios", usuarios.size());

        model.addAttribute("topHabilidades", topHabilidades);
        model.addAttribute("maxDeclarada", maxDeclarada);

        return "administrador/inicio";
    }

    @PostMapping("/inicio/invitar")
    public String invitar(@RequestParam String correo, HttpSession session, RedirectAttributes redirectAttributes) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        try {
            usuarioService.invitar(correo, sesion.getUsuarioId());
            redirectAttributes.addFlashAttribute("exito", "Invitación enviada a " + correo + ".");
        } catch (OperacionInvalidaException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/administrador/inicio.html";
    }
}
