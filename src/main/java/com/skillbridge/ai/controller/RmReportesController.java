package com.skillbridge.ai.controller;

import com.skillbridge.ai.dto.FichaProyecto;
import com.skillbridge.ai.dto.ReporteRm;
import com.skillbridge.ai.model.Proyecto;
import com.skillbridge.ai.repository.ProyectoRepository;
import com.skillbridge.ai.service.FichaProyectoService;
import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.service.ReporteExportService;
import com.skillbridge.ai.service.RmReporteExportService;
import com.skillbridge.ai.service.RmReporteService;
import com.skillbridge.ai.service.ShellModelBuilder;
import com.skillbridge.ai.util.SesionKeys;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dashboard de Reportes del Resource Manager (resource-manager/reportes.html)
 * y sus exportaciones (Excel con gráficos nativos y PDF ejecutivo). Los tres
 * salen del mismo {@link ReporteRm} con los mismos filtros (GET):
 * {@code q}, {@code estado} (repetible: banca, sub, opt, lim, sobre),
 * {@code cargo}, {@code proyecto}, {@code categoria}, {@code min}, {@code brecha}.
 */
@Controller
@RequestMapping("/resource-manager")
public class RmReportesController {

    /** Estados de proyecto "en curso": son los que tienen ficha cuando no se elige ningún proyecto. */
    private static final List<String> EN_CURSO = List.of("planificacion", "activo", "en_pausa");

    private final RmReporteService rmReporteService;
    private final RmReporteExportService exportRm;
    private final ReporteExportService exportService;
    private final ShellModelBuilder shellModelBuilder;
    private final FichaProyectoService fichaService;
    private final ProyectoRepository proyectoRepository;

    public RmReportesController(RmReporteService rmReporteService, RmReporteExportService exportRm,
                                ReporteExportService exportService, ShellModelBuilder shellModelBuilder,
                                FichaProyectoService fichaService, ProyectoRepository proyectoRepository) {
        this.rmReporteService = rmReporteService;
        this.exportRm = exportRm;
        this.exportService = exportService;
        this.shellModelBuilder = shellModelBuilder;
        this.fichaService = fichaService;
        this.proyectoRepository = proyectoRepository;
    }

    @GetMapping("/reportes.html")
    public String reportes(HttpSession session, Model model,
                           @RequestParam(required = false) String q,
                           @RequestParam(name = "estado", required = false) List<String> estados,
                           @RequestParam(required = false) String cargo,
                           @RequestParam(name = "proyecto", required = false) List<Long> proyectoIds,
                           @RequestParam(name = "categoria", required = false) Long categoriaId,
                           @RequestParam(name = "min", defaultValue = "0") int minimo,
                           @RequestParam(name = "brecha", defaultValue = "false") boolean soloBrecha) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        RmReporteService.Filtros filtros = new RmReporteService.Filtros(q, estados, cargo, proyectoIds, categoriaId, minimo, soloBrecha);
        ReporteRm datos = rmReporteService.generar(filtros);
        shellModelBuilder.aplicar(model, sesion, "reportes.html", "Reportes",
                "Capacidad, proyectos, habilidades y decisiones · " + datos.generado());
        List<Proyecto> proyectos = proyectosConFicha(filtros);
        model.addAttribute("d", datos);
        model.addAttribute("filtros", filtros);
        model.addAttribute("queryExport", query(filtros));
        model.addAttribute("fichas", fichaService.fichas(proyectos, false));
        model.addAttribute("totalFichas", proyectos.size());
        model.addAttribute("maxFichas", FichaProyectoService.MAXIMO_FICHAS);
        return "resource-manager/reportes";
    }

    @GetMapping("/reportes.xlsx")
    public void excel(HttpServletResponse response,
                      @RequestParam(required = false) String q,
                      @RequestParam(name = "estado", required = false) List<String> estados,
                      @RequestParam(required = false) String cargo,
                      @RequestParam(name = "proyecto", required = false) List<Long> proyectoIds,
                      @RequestParam(name = "categoria", required = false) Long categoriaId,
                      @RequestParam(name = "min", defaultValue = "0") int minimo,
                      @RequestParam(name = "brecha", defaultValue = "false") boolean soloBrecha,
                      @RequestParam(defaultValue = "true") boolean ia) throws IOException {
        RmReporteService.Filtros f = new RmReporteService.Filtros(q, estados, cargo, proyectoIds, categoriaId, minimo, soloBrecha);
        ReporteRm d = rmReporteService.generar(f);
        List<FichaProyecto> fichas = fichaService.fichas(proyectosConFicha(f), ia);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=" + exportService.nombreArchivo("skillbridge-reporte-rm") + ".xlsx");
        exportRm.excel(d, fichas, response.getOutputStream());
    }

    @GetMapping("/reportes.pdf")
    public void pdf(HttpServletResponse response,
                    @RequestParam(required = false) String q,
                    @RequestParam(name = "estado", required = false) List<String> estados,
                    @RequestParam(required = false) String cargo,
                    @RequestParam(name = "proyecto", required = false) List<Long> proyectoIds,
                    @RequestParam(name = "categoria", required = false) Long categoriaId,
                    @RequestParam(name = "min", defaultValue = "0") int minimo,
                    @RequestParam(name = "brecha", defaultValue = "false") boolean soloBrecha,
                    @RequestParam(defaultValue = "true") boolean ia) throws IOException {
        RmReporteService.Filtros f = new RmReporteService.Filtros(q, estados, cargo, proyectoIds, categoriaId, minimo, soloBrecha);
        ReporteRm d = rmReporteService.generar(f);
        List<FichaProyecto> fichas = fichaService.fichas(proyectosConFicha(f), ia);
        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "attachment; filename=" + exportService.nombreArchivo("skillbridge-reporte-rm") + ".pdf");
        exportRm.pdf(d, fichas, response.getOutputStream());
    }

    /** Resúmenes IA de las fichas visibles (botón de la pestaña "Fichas"). */
    @PostMapping("/reportes/fichas-ia")
    @ResponseBody
    public Map<String, Object> fichasIa(@RequestParam(name = "proyecto", required = false) List<Long> proyectoIds) {
        RmReporteService.Filtros f = new RmReporteService.Filtros(null, null, null, proyectoIds, null, 0, false);
        Map<String, String> resumenes = new LinkedHashMap<>();
        for (FichaProyecto ficha : fichaService.fichas(proyectosConFicha(f), true)) {
            if (ficha.resumenIa() != null) resumenes.put(String.valueOf(ficha.id()), ficha.resumenIa());
        }
        return Map.of("ok", !resumenes.isEmpty(), "resumenes", resumenes);
    }

    /**
     * Proyectos con ficha: los elegidos en el filtro o, si no se eligió
     * ninguno, los que están en curso. Siempre del más reciente al más antiguo.
     */
    private List<Proyecto> proyectosConFicha(RmReporteService.Filtros f) {
        List<Proyecto> base = f.porProyecto() ? proyectoRepository.findAllById(f.proyectoIds())
                : proyectoRepository.findAll().stream().filter(p -> EN_CURSO.contains(p.getEstado())).toList();
        return base.stream().sorted(Comparator.comparing(Proyecto::getFechaInicio, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Proyecto::getNombre)).toList();
    }

    /** Los mismos filtros, como query string para los enlaces de exportación. */
    private static String query(RmReporteService.Filtros f) {
        StringBuilder s = new StringBuilder();
        if (!f.q().isEmpty()) s.append("&q=").append(URLEncoder.encode(f.q(), StandardCharsets.UTF_8));
        for (String e : f.estados()) s.append("&estado=").append(URLEncoder.encode(e, StandardCharsets.UTF_8));
        if (f.cargo() != null) s.append("&cargo=").append(URLEncoder.encode(f.cargo(), StandardCharsets.UTF_8));
        for (Long id : f.proyectoIds()) s.append("&proyecto=").append(id);
        if (f.categoriaId() != null) s.append("&categoria=").append(f.categoriaId());
        if (f.minimo() > 0) s.append("&min=").append(f.minimo());
        if (f.soloBrecha()) s.append("&brecha=true");
        return s.length() == 0 ? "" : "?" + s.substring(1);
    }
}
