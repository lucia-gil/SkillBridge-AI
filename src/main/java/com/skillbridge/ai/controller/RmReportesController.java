package com.skillbridge.ai.controller;

import com.skillbridge.ai.dto.ReporteRm;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

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

    private final RmReporteService rmReporteService;
    private final RmReporteExportService exportRm;
    private final ReporteExportService exportService;
    private final ShellModelBuilder shellModelBuilder;

    public RmReportesController(RmReporteService rmReporteService, RmReporteExportService exportRm,
                                ReporteExportService exportService, ShellModelBuilder shellModelBuilder) {
        this.rmReporteService = rmReporteService;
        this.exportRm = exportRm;
        this.exportService = exportService;
        this.shellModelBuilder = shellModelBuilder;
    }

    @GetMapping("/reportes.html")
    public String reportes(HttpSession session, Model model,
                           @RequestParam(required = false) String q,
                           @RequestParam(name = "estado", required = false) List<String> estados,
                           @RequestParam(required = false) String cargo,
                           @RequestParam(name = "proyecto", required = false) Long proyectoId,
                           @RequestParam(name = "categoria", required = false) Long categoriaId,
                           @RequestParam(name = "min", defaultValue = "0") int minimo,
                           @RequestParam(name = "brecha", defaultValue = "false") boolean soloBrecha) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        RmReporteService.Filtros filtros = new RmReporteService.Filtros(q, estados, cargo, proyectoId, categoriaId, minimo, soloBrecha);
        ReporteRm datos = rmReporteService.generar(filtros);
        shellModelBuilder.aplicar(model, sesion, "reportes.html", "Reportes",
                "Capacidad, proyectos, habilidades y decisiones · " + datos.generado());
        model.addAttribute("d", datos);
        model.addAttribute("filtros", filtros);
        model.addAttribute("queryExport", query(filtros));
        return "resource-manager/reportes";
    }

    @GetMapping("/reportes.xlsx")
    public void excel(HttpServletResponse response,
                      @RequestParam(required = false) String q,
                      @RequestParam(name = "estado", required = false) List<String> estados,
                      @RequestParam(required = false) String cargo,
                      @RequestParam(name = "proyecto", required = false) Long proyectoId,
                      @RequestParam(name = "categoria", required = false) Long categoriaId,
                      @RequestParam(name = "min", defaultValue = "0") int minimo,
                      @RequestParam(name = "brecha", defaultValue = "false") boolean soloBrecha) throws IOException {
        ReporteRm d = rmReporteService.generar(new RmReporteService.Filtros(q, estados, cargo, proyectoId, categoriaId, minimo, soloBrecha));
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=" + exportService.nombreArchivo("skillbridge-reporte-rm") + ".xlsx");
        exportRm.excel(d, response.getOutputStream());
    }

    @GetMapping("/reportes.pdf")
    public void pdf(HttpServletResponse response,
                    @RequestParam(required = false) String q,
                    @RequestParam(name = "estado", required = false) List<String> estados,
                    @RequestParam(required = false) String cargo,
                    @RequestParam(name = "proyecto", required = false) Long proyectoId,
                    @RequestParam(name = "categoria", required = false) Long categoriaId,
                    @RequestParam(name = "min", defaultValue = "0") int minimo,
                    @RequestParam(name = "brecha", defaultValue = "false") boolean soloBrecha) throws IOException {
        ReporteRm d = rmReporteService.generar(new RmReporteService.Filtros(q, estados, cargo, proyectoId, categoriaId, minimo, soloBrecha));
        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "attachment; filename=" + exportService.nombreArchivo("skillbridge-reporte-rm") + ".pdf");
        exportRm.pdf(d, response.getOutputStream());
    }

    /** Los mismos filtros, como query string para los enlaces de exportación. */
    private static String query(RmReporteService.Filtros f) {
        StringBuilder s = new StringBuilder();
        if (!f.q().isEmpty()) s.append("&q=").append(URLEncoder.encode(f.q(), StandardCharsets.UTF_8));
        for (String e : f.estados()) s.append("&estado=").append(URLEncoder.encode(e, StandardCharsets.UTF_8));
        if (f.cargo() != null) s.append("&cargo=").append(URLEncoder.encode(f.cargo(), StandardCharsets.UTF_8));
        if (f.proyectoId() != null) s.append("&proyecto=").append(f.proyectoId());
        if (f.categoriaId() != null) s.append("&categoria=").append(f.categoriaId());
        if (f.minimo() > 0) s.append("&min=").append(f.minimo());
        if (f.soloBrecha()) s.append("&brecha=true");
        return s.length() == 0 ? "" : "?" + s.substring(1);
    }
}
