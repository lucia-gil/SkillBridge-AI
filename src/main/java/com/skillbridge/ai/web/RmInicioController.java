package com.skillbridge.ai.web;

import com.skillbridge.ai.dto.HeatmapColaboradorFila;
import com.skillbridge.ai.dto.OcupacionColaboradorFila;
import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.dto.VacanteHabilidad;
import com.skillbridge.ai.model.Asignacion;
import com.skillbridge.ai.model.Perfil;
import com.skillbridge.ai.repository.AsignacionRepository;
import com.skillbridge.ai.repository.PerfilRepository;
import com.skillbridge.ai.repository.PropuestaAsignacionRepository;
import com.skillbridge.ai.service.ReporteService;
import com.skillbridge.ai.service.ShellModelBuilder;
import com.skillbridge.ai.util.SesionKeys;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Inicio de Resource Manager (resource-manager/inicio.html). Reemplaza la
 * versión anterior, que pintaba datos 100% inventados desde MOCK.* (nombres
 * como "Mariana Ruiz", semanas fijas "S34-S39" sin forma de navegar). Esta
 * versión calcula el mapa de carga a partir de asignaciones reales: cada
 * columna es una semana ISO real, y "semanaOffset" mueve la ventana de 6
 * semanas hacia adelante/atrás (ver botones «Anteriores»/«Siguientes» en la
 * plantilla).
 *
 * No hay una tabla que guarde "carga por semana": se deriva sumando
 * carga_porcentaje de las asignaciones activas cuyo rango
 * [fecha_inicio, fecha_fin] cubre cada semana, igual que
 * ReporteService.ocupacionPorColaborador() hace para "ahora mismo".
 */
@Controller
public class RmInicioController {

    private static final int SEMANAS_VISIBLES = 6;
    private static final int HORAS_JORNADA_COMPLETA = 40;

    private final AsignacionRepository asignacionRepository;
    private final PerfilRepository perfilRepository;
    private final PropuestaAsignacionRepository propuestaAsignacionRepository;
    private final ReporteService reporteService;
    private final ShellModelBuilder shellModelBuilder;

    public RmInicioController(AsignacionRepository asignacionRepository, PerfilRepository perfilRepository,
                               PropuestaAsignacionRepository propuestaAsignacionRepository,
                               ReporteService reporteService, ShellModelBuilder shellModelBuilder) {
        this.asignacionRepository = asignacionRepository;
        this.perfilRepository = perfilRepository;
        this.propuestaAsignacionRepository = propuestaAsignacionRepository;
        this.reporteService = reporteService;
        this.shellModelBuilder = shellModelBuilder;
    }

    @GetMapping("/resource-manager/inicio.html")
    public String inicio(@RequestParam(defaultValue = "0") int semanaOffset, HttpSession session, Model model) {
        // Nunca se muestran semanas futuras más allá de "hoy": el botón
        // "Siguientes" solo puede acercar de vuelta al presente, no superarlo.
        if (semanaOffset > 0) semanaOffset = 0;
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);

        // ---------- KPIs ----------
        List<OcupacionColaboradorFila> ocupacionActual = reporteService.ocupacionPorColaborador();
        int ocupacionPromedio = reporteService.ocupacionPromedio();
        long sobreAsignados = ocupacionActual.stream().filter(o -> o.getPromedio() > 100).count();

        Map<Long, Integer> cargaActualPorPerfil = new LinkedHashMap<>();
        for (Object[] fila : asignacionRepository.sumarCargaActivaAgrupadaPorPerfil()) {
            cargaActualPorPerfil.put((Long) fila[0], ((Number) fila[1]).intValue());
        }
        List<Perfil> perfilesActivos = perfilRepository.listarActivosConUsuario();
        double capacidadLibreHoras = perfilesActivos.stream()
                .mapToDouble(p -> Math.max(0, 100 - cargaActualPorPerfil.getOrDefault(p.getId(), 0)) / 100.0 * HORAS_JORNADA_COMPLETA)
                .sum();

        long solicitudesPendientes = propuestaAsignacionRepository.listarPendientes().size();

        // ---------- Mapa de carga semanal ----------
        LocalDate lunesBase = LocalDate.now().with(DayOfWeek.MONDAY).plusWeeks((long) semanaOffset * SEMANAS_VISIBLES);
        // Antes esta columna se etiquetaba con el número de semana ISO
        // ("S38"...), que reinicia en 1 cada Año Nuevo (semana 52 → semana 1)
        // sin mostrar el año — confuso al navegar varios meses. Se etiqueta
        // con la fecha real del lunes de cada semana: sin ambigüedad y sin
        // depender de que la persona sepa a qué "número de semana" corresponde
        // una fecha.
        List<LocalDate> iniciosSemana = new ArrayList<>();
        List<String> etiquetasSemana = new ArrayList<>();
        java.time.format.DateTimeFormatter etiquetaFmt = java.time.format.DateTimeFormatter.ofPattern("dd/MM");
        for (int i = 0; i < SEMANAS_VISIBLES; i++) {
            LocalDate inicio = lunesBase.plusWeeks(i);
            iniciosSemana.add(inicio);
            etiquetasSemana.add(inicio.format(etiquetaFmt));
        }
        LocalDate ventanaInicio = iniciosSemana.get(0);
        LocalDate ventanaFin = iniciosSemana.get(SEMANAS_VISIBLES - 1).plusDays(6);

        List<Asignacion> activas = asignacionRepository.listarActivasConPerfil();
        Map<Long, int[]> cargaPorPerfilYSemana = new LinkedHashMap<>();
        Map<Long, String> nombrePorPerfil = new LinkedHashMap<>();
        Map<Long, String> cargoPorPerfil = new LinkedHashMap<>();
        for (Asignacion a : activas) {
            LocalDate fin = a.getFechaFin();
            if (a.getFechaInicio().isAfter(ventanaFin) || (fin != null && fin.isBefore(ventanaInicio))) continue;
            Long perfilId = a.getPerfilId();
            int[] semanas = cargaPorPerfilYSemana.computeIfAbsent(perfilId, k -> new int[SEMANAS_VISIBLES]);
            nombrePorPerfil.putIfAbsent(perfilId, a.getPerfil().getUsuario().getNombreCompleto());
            cargoPorPerfil.putIfAbsent(perfilId, a.getPerfil().getCargo());
            for (int i = 0; i < SEMANAS_VISIBLES; i++) {
                LocalDate semanaInicio = iniciosSemana.get(i);
                LocalDate semanaFin = semanaInicio.plusDays(6);
                boolean cubierta = !a.getFechaInicio().isAfter(semanaFin) && (fin == null || !fin.isBefore(semanaInicio));
                if (cubierta) semanas[i] += a.getCargaPorcentaje();
            }
        }
        List<HeatmapColaboradorFila> heatmap = cargaPorPerfilYSemana.entrySet().stream()
                .map(e -> new HeatmapColaboradorFila(nombrePorPerfil.get(e.getKey()), cargoPorPerfil.get(e.getKey()),
                        java.util.Arrays.stream(e.getValue()).boxed().collect(java.util.stream.Collectors.toList())))
                .sorted((x, y) -> Integer.compare(y.getPromedio(), x.getPromedio()))
                .collect(java.util.stream.Collectors.toList());

        List<OcupacionColaboradorFila> sobrecargados = reporteService.colaboradoresSobrecargados();
        List<VacanteHabilidad> habilidadesDeclaradas = reporteService.topHabilidadesDeclaradas(8);

        shellModelBuilder.aplicar(model, sesion, "inicio.html", "Inicio",
                "Ocupación del equipo · " + perfilesActivos.size() + " colaboradores");

        model.addAttribute("kpiOcupacionPromedio", ocupacionPromedio);
        model.addAttribute("kpiSobreAsignados", sobreAsignados);
        model.addAttribute("kpiCapacidadLibre", Math.round(capacidadLibreHoras));
        model.addAttribute("kpiSolicitudesPendientes", solicitudesPendientes);

        model.addAttribute("etiquetasSemana", etiquetasSemana);
        model.addAttribute("rangoLabel", ventanaInicio.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM", Locale.forLanguageTag("es"))) +
                " – " + ventanaFin.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("es"))));
        model.addAttribute("heatmap", heatmap);
        model.addAttribute("semanaOffset", semanaOffset);

        model.addAttribute("sobrecargados", sobrecargados);
        model.addAttribute("habilidadesDeclaradas", habilidadesDeclaradas);

        return "resource-manager/inicio";
    }
}
