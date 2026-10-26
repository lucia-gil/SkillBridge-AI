package com.skillbridge.ai.controller;

import com.skillbridge.ai.dto.ReporteRmDatos;
import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.service.ReporteExportService;
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
 * Reportes del Resource Manager (resource-manager/reportes.html).
 *
 * <p>Dos reportes con los mismos filtros, aplicados en el servidor
 * ({@link RmReporteService}), de modo que la pantalla, el Excel y el PDF
 * muestran siempre lo mismo:</p>
 * <ul>
 *   <li>Ocupación por colaborador (incluye a quien no tiene asignaciones).</li>
 *   <li>Oferta contra demanda de habilidades de los proyectos vigentes.</li>
 * </ul>
 *
 * <p>Filtros (GET): {@code q} texto, {@code estado} (repetible), {@code min}
 * ocupación mínima en %, {@code brecha=1} solo habilidades con brecha.</p>
 */
@Controller
@RequestMapping("/resource-manager")
public class RmReportesController {

    private final RmReporteService rmReporteService;
    private final ReporteExportService exportService;
    private final ShellModelBuilder shellModelBuilder;

    public RmReportesController(RmReporteService rmReporteService, ReporteExportService exportService,
                                ShellModelBuilder shellModelBuilder) {
        this.rmReporteService = rmReporteService;
        this.exportService = exportService;
        this.shellModelBuilder = shellModelBuilder;
    }

    @GetMapping("/reportes.html")
    public String reportes(HttpSession session, Model model,
                           @RequestParam(required = false) String q,
                           @RequestParam(name = "estado", required = false) List<String> estados,
                           @RequestParam(name = "min", defaultValue = "0") int minimo,
                           @RequestParam(name = "brecha", defaultValue = "false") boolean soloBrecha) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        RmReporteService.Filtros filtros = new RmReporteService.Filtros(q, estados, minimo, soloBrecha);
        ReporteRmDatos datos = rmReporteService.generar(filtros);

        shellModelBuilder.aplicar(model, sesion, "reportes.html", "Reportes",
                "Ocupación del equipo y cobertura de habilidades · " + datos.generado());

        model.addAttribute("datos", datos);
        model.addAttribute("filtros", filtros);
        model.addAttribute("queryExport", query(filtros));
        return "resource-manager/reportes";
    }

    @GetMapping("/reportes.xlsx")
    public void excel(HttpServletResponse response,
                      @RequestParam(required = false) String q,
                      @RequestParam(name = "estado", required = false) List<String> estados,
                      @RequestParam(name = "min", defaultValue = "0") int minimo,
                      @RequestParam(name = "brecha", defaultValue = "false") boolean soloBrecha) throws IOException {
        ReporteRmDatos datos = rmReporteService.generar(new RmReporteService.Filtros(q, estados, minimo, soloBrecha));
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=" + exportService.nombreArchivo("skillbridge-reporte-rm") + ".xlsx");
        exportService.escribirExcel(rmReporteService.paraExportar(datos), response.getOutputStream());
    }

    @GetMapping("/reportes.pdf")
    public void pdf(HttpServletResponse response,
                    @RequestParam(required = false) String q,
                    @RequestParam(name = "estado", required = false) List<String> estados,
                    @RequestParam(name = "min", defaultValue = "0") int minimo,
                    @RequestParam(name = "brecha", defaultValue = "false") boolean soloBrecha) throws IOException {
        ReporteRmDatos datos = rmReporteService.generar(new RmReporteService.Filtros(q, estados, minimo, soloBrecha));
        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "attachment; filename=" + exportService.nombreArchivo("skillbridge-reporte-rm") + ".pdf");
        exportService.escribirPdf(rmReporteService.paraExportar(datos), response.getOutputStream());
    }

    /** Los mismos filtros, como query string para los enlaces de exportación. */
    private static String query(RmReporteService.Filtros f) {
        StringBuilder s = new StringBuilder();
        if (!f.q().isEmpty()) s.append("&q=").append(URLEncoder.encode(f.q(), StandardCharsets.UTF_8));
        for (String e : f.estados()) s.append("&estado=").append(URLEncoder.encode(e, StandardCharsets.UTF_8));
        if (f.minimo() > 0) s.append("&min=").append(f.minimo());
        if (f.soloBrecha()) s.append("&brecha=true");
        return s.length() == 0 ? "" : "?" + s.substring(1);
    }
}
