package com.skillbridge.ai.web;

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

/**
 * Reportes del Resource Manager (resource-manager/reportes.html).
 *
 * Misma logica que RmInicioController, reutilizando los mismos servicios
 * (ReporteService) - esta pantalla es la version "detalle" con la tabla
 * completa de ocupacion por colaborador, en vez del resumen que ya se ve
 * en Inicio.
 *
 * "Evolucion de ocupacion" (grafico mensual) y "Reuniones de esta semana"
 * del mock original se retiran: el primero necesitaria una tabla de
 * historico que no existe (mismo motivo que el heatmap de Inicio); el
 * segundo requeriria una consulta nueva de eventos_proyecto cruzando TODOS
 * los proyectos de la plataforma por rango de fecha, que no existe hoy en
 * EventoProyectoRepository/EventoService (ambos solo listan eventos de los
 * proyectos de un perfil puntual) - agregarla es una funcionalidad nueva,
 * no una conexion de datos que ya exista.
 *
 * "Solicitudes pendientes" tambien se retira: dependia de
 * TalentMatchingService.pendientes(), que apunta a la tabla
 * propuestas_asignacion - esa tabla NO existe en skillbridge_db_v4.sql, asi
 * que esa consulta revienta en runtime aunque compile. No hay otra tabla
 * real para ese KPI en este esquema.
 */
@Controller
@RequestMapping("/resource-manager")
public class RmReportesController {

    private final ReporteService reporteService;
    private final ShellModelBuilder shellModelBuilder;

    public RmReportesController(ReporteService reporteService, ShellModelBuilder shellModelBuilder) {
        this.reporteService = reporteService;
        this.shellModelBuilder = shellModelBuilder;
    }

    @GetMapping("/reportes.html")
    public String reportes(HttpSession session, Model model) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);

        List<OcupacionColaboradorFila> ocupacion = reporteService.ocupacionPorColaborador();
        List<OcupacionColaboradorFila> sobrecargados = reporteService.colaboradoresSobrecargados();
        List<VacanteHabilidad> topHabilidades = reporteService.topHabilidadesDeclaradas(8);
        long maxDeclarada = topHabilidades.stream().mapToLong(VacanteHabilidad::vacantes).max().orElse(1);

        shellModelBuilder.aplicar(model, sesion, "reportes.html", "Reportes",
                "Ocupación y demanda de habilidades · datos actuales");

        model.addAttribute("kpiOcupacionPromedio", reporteService.ocupacionPromedio());
        model.addAttribute("kpiSobreAsignados", sobrecargados.size());
        model.addAttribute("kpiColaboradoresEvaluados", ocupacion.size());

        model.addAttribute("ocupacion", ocupacion);
        model.addAttribute("topHabilidades", topHabilidades);
        model.addAttribute("maxDeclarada", maxDeclarada);

        return "resource-manager/reportes";
    }
}