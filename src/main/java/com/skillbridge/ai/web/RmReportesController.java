package com.skillbridge.ai.web;

import com.skillbridge.ai.dto.OcupacionColaboradorFila;
import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.dto.VacanteHabilidad;
import com.skillbridge.ai.model.Asignacion;
import com.skillbridge.ai.repository.AsignacionRepository;
import com.skillbridge.ai.service.ReporteService;
import com.skillbridge.ai.service.ShellModelBuilder;
import com.skillbridge.ai.util.SesionKeys;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reportes de Resource Manager (resource-manager/reportes.html). Antes
 * pintaba datos 100% inventados desde MOCK.rmReport; ahora reutiliza las
 * mismas fuentes reales que resource-manager/inicio.html y
 * RmOcupacionController (ver Javadoc de esos archivos).
 *
 * "Evolución de ocupación" no tiene una tabla histórica real detrás (el
 * modelo no guarda una foto de ocupación por semana), así que se deriva
 * igual que el mapa de carga de inicio.html: se recalcula sumando
 * carga_porcentaje de asignaciones activas cuyo rango de fechas cubre cada
 * una de las últimas 6 semanas, promediado entre los colaboradores con
 * carga esa semana.
 */
@Controller
public class RmReportesController {

    private static final int SEMANAS_EVOLUCION = 6;

    private final AsignacionRepository asignacionRepository;
    private final ReporteService reporteService;
    private final ShellModelBuilder shellModelBuilder;

    public RmReportesController(AsignacionRepository asignacionRepository, ReporteService reporteService,
                                 ShellModelBuilder shellModelBuilder) {
        this.asignacionRepository = asignacionRepository;
        this.reporteService = reporteService;
        this.shellModelBuilder = shellModelBuilder;
    }

    private record PuntoEvolucion(String etiqueta, int promedio) {
    }

    @GetMapping("/resource-manager/reportes.html")
    public String reportes(HttpSession session, Model model) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);

        List<OcupacionColaboradorFila> ocupacion = reporteService.ocupacionPorColaborador();
        int ocupacionPromedio = reporteService.ocupacionPromedio();
        long sobreAsignados = ocupacion.stream().filter(o -> o.getPromedio() > 100).count();
        List<VacanteHabilidad> habilidadesDeclaradas = reporteService.topHabilidadesDeclaradas(8);
        long maxDemanda = habilidadesDeclaradas.stream().mapToLong(VacanteHabilidad::vacantes).max().orElse(1);

        LocalDate lunesBase = LocalDate.now().with(DayOfWeek.MONDAY).minusWeeks(SEMANAS_EVOLUCION - 1L);
        List<Asignacion> activas = asignacionRepository.listarActivasConPerfil();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM", Locale.forLanguageTag("es"));
        List<PuntoEvolucion> evolucion = new ArrayList<>();
        for (int i = 0; i < SEMANAS_EVOLUCION; i++) {
            LocalDate semanaInicio = lunesBase.plusWeeks(i);
            LocalDate semanaFin = semanaInicio.plusDays(6);
            java.util.Map<Long, Integer> cargaSemana = new java.util.HashMap<>();
            for (Asignacion a : activas) {
                LocalDate fin = a.getFechaFin();
                boolean cubierta = !a.getFechaInicio().isAfter(semanaFin) && (fin == null || !fin.isBefore(semanaInicio));
                if (cubierta) cargaSemana.merge(a.getPerfilId(), a.getCargaPorcentaje(), Integer::sum);
            }
            int promedio = cargaSemana.isEmpty() ? 0
                    : (int) Math.round(cargaSemana.values().stream().mapToInt(Integer::intValue).average().orElse(0));
            evolucion.add(new PuntoEvolucion(semanaInicio.format(fmt), promedio));
        }
        int metaOcupacion = 85;

        shellModelBuilder.aplicar(model, sesion, "reportes.html", "Reportes",
                "Ocupación y demanda de habilidades del equipo");

        model.addAttribute("kpiOcupacionPromedio", ocupacionPromedio);
        model.addAttribute("kpiSobreAsignados", sobreAsignados);
        model.addAttribute("metaOcupacion", metaOcupacion);
        model.addAttribute("evolucion", evolucion);
        model.addAttribute("habilidadesDeclaradas", habilidadesDeclaradas);
        model.addAttribute("maxDemanda", maxDemanda);
        model.addAttribute("ocupacion", ocupacion);

        return "resource-manager/reportes";
    }
}
