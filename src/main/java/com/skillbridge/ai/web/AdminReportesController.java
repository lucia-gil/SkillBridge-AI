package com.skillbridge.ai.web;

import com.skillbridge.ai.dto.EstadoProyectoConConteo;
import com.skillbridge.ai.dto.OcupacionColaboradorFila;
import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.dto.VacanteHabilidad;
import com.skillbridge.ai.service.ReporteService;
import com.skillbridge.ai.service.ShellModelBuilder;
import com.skillbridge.ai.util.SesionKeys;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;
import java.util.Map;

/**
 * Reportes globales (administrador/reportes.html). Ver Javadoc de
 * ReporteService: "Vacantes por habilidad" y "consultas al Asistente IA"
 * del mockup no tienen fuente real y se reemplazan por métricas que sí la
 * tienen (habilidades más declaradas, KPIs reales).
 */
@Controller
@RequestMapping("/administrador")
public class AdminReportesController {

    private final ReporteService reporteService;
    private final ShellModelBuilder shellModelBuilder;

    public AdminReportesController(ReporteService reporteService, ShellModelBuilder shellModelBuilder) {
        this.reporteService = reporteService;
        this.shellModelBuilder = shellModelBuilder;
    }

    @GetMapping("/reportes.html")
    public String reportes(HttpSession session, Model model) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);

        Map<String, Long> kpis = reporteService.kpisAdministrador();
        List<VacanteHabilidad> topHabilidades = reporteService.topHabilidadesDeclaradas(8);
        long maxDeclarada = topHabilidades.stream().mapToLong(VacanteHabilidad::vacantes).max().orElse(1);
        List<EstadoProyectoConConteo> proyectosPorEstado = reporteService.proyectosPorEstadoDetallado();
        List<OcupacionColaboradorFila> ocupacion = reporteService.ocupacionPorColaborador();

        shellModelBuilder.aplicar(model, sesion, "reportes.html", "Reportes globales",
                "Vista consolidada de toda la plataforma");

        model.addAttribute("kpis", kpis);
        model.addAttribute("topHabilidades", topHabilidades);
        model.addAttribute("maxDeclarada", maxDeclarada);
        model.addAttribute("proyectosPorEstado", proyectosPorEstado);
        model.addAttribute("ocupacion", ocupacion);
        return "administrador/reportes";
    }
}
