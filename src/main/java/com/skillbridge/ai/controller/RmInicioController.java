package com.skillbridge.ai.controller;

import com.skillbridge.ai.dto.OcupacionColaboradorFila;
import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.dto.VacanteHabilidad;
import com.skillbridge.ai.dto.PanelRm;
import com.skillbridge.ai.service.ReporteService;
import com.skillbridge.ai.service.RmPanelService;
import com.skillbridge.ai.service.ShellModelBuilder;
import com.skillbridge.ai.util.SesionKeys;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

/**
 * Inicio del Resource Manager (resource-manager/inicio.html).
 *
 * Reemplaza la version mock (heatmap semanal S34-S39 con numeros
 * inventados en mock-data.js) por datos reales, reutilizando EXACTAMENTE
 * los mismos servicios que ya usan administrador/inicio.html y
 * resource-manager/ocupacion.html - no se agrega ninguna consulta nueva.
 *
 * El mapa de calor semanal se retira a proposito: no existe ninguna tabla
 * que guarde un historico de ocupacion semana a semana (mismo motivo ya
 * documentado en RmOcupacionController), asi que solo se puede mostrar la
 * "foto" actual. Se reemplaza por "Sobre-asignacion" (colaboradores por
 * encima del 100% ahora mismo) y "Habilidades mas demandadas", con el
 * mismo patron visual que ya usa administrador/inicio.html.
 *
 * "Solicitudes pendientes" del mock original TAMBIEN se retira: la version
 * inicial de este controlador intento reusar TalentMatchingService.pendientes(),
 * pero esa consulta apunta a la tabla propuestas_asignacion, que no existe
 * en skillbridge_db_v4.sql (el codigo compila pero revienta en runtime con
 * "Table doesn't exist"). No hay ninguna otra tabla/servicio real para
 * "solicitudes pendientes" en este esquema (excepciones_carga tampoco tiene
 * entidad/repositorio implementado), asi que se deja fuera hasta que exista
 * un dato real que respalde ese KPI.
 *
 * 2026-10 (rediseño): se suma RmPanelService con datos reales de la BD -
 * banca/disponibilidad, tendencia de ocupación, predicción de sobrecarga,
 * balance de carga, liberaciones próximas, solicitudes pendientes de los PM
 * (propuestas_asignacion, que en el modelo v7 ya existe), proyectos por
 * iniciar, línea de tiempo y demanda vs. oferta de habilidades. Los atributos
 * anteriores se mantienen (la plantilla los sigue usando).
 */
@Controller
@RequestMapping("/resource-manager")
public class RmInicioController {

    private final ReporteService reporteService;
    private final ShellModelBuilder shellModelBuilder;
    private final RmPanelService rmPanelService;

    public RmInicioController(ReporteService reporteService, ShellModelBuilder shellModelBuilder,
                              RmPanelService rmPanelService) {
        this.reporteService = reporteService;
        this.shellModelBuilder = shellModelBuilder;
        this.rmPanelService = rmPanelService;
    }

    @GetMapping("/inicio.html")
    public String inicio(HttpSession session, Model model) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);

        List<OcupacionColaboradorFila> ocupacion = reporteService.ocupacionPorColaborador();
        List<OcupacionColaboradorFila> sobrecargados = reporteService.colaboradoresSobrecargados();
        List<VacanteHabilidad> topHabilidades = reporteService.topHabilidadesDeclaradas(5);
        long maxDeclarada = topHabilidades.stream().mapToLong(VacanteHabilidad::vacantes).max().orElse(1);

        shellModelBuilder.aplicar(model, sesion, "inicio.html", "Inicio",
                "Ocupación del equipo · " + ocupacion.size() + " colaboradores con asignaciones activas");

        model.addAttribute("kpiOcupacionPromedio", reporteService.ocupacionPromedio());
        model.addAttribute("kpiSobreAsignados", sobrecargados.size());
        model.addAttribute("kpiColaboradoresEvaluados", ocupacion.size());

        model.addAttribute("sobrecargados", sobrecargados);
        model.addAttribute("topHabilidades", topHabilidades);
        model.addAttribute("maxDeclarada", maxDeclarada);

        PanelRm panel = rmPanelService.construir();
        model.addAttribute("panel", panel);
        // Pestaña inicial: "Alertas de carga" si hay algo que atender; si no, "La banca".
        int alertas = sobrecargados.size() + panel.prediccion().enRiesgo().size();
        model.addAttribute("alertasCarga", alertas);
        model.addAttribute("disponiblesTotal", panel.balance().sinAsignar() + panel.balance().baja());

        return "resource-manager/inicio";
    }
}