package com.skillbridge.ai.web;

import com.skillbridge.ai.dto.ReportePmDatos;
import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.service.ReportePmExportService;
import com.skillbridge.ai.service.ReportePmService;
import com.skillbridge.ai.service.ShellModelBuilder;
import com.skillbridge.ai.util.SesionKeys;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

@Controller
@RequestMapping("/project-manager")
public class ReportesPmController {

    private final ReportePmService reporteService;
    private final ReportePmExportService exportService;
    private final ShellModelBuilder shellModelBuilder;

    public ReportesPmController(ReportePmService reporteService, ReportePmExportService exportService,
                                ShellModelBuilder shellModelBuilder) {
        this.reporteService = reporteService;
        this.exportService = exportService;
        this.shellModelBuilder = shellModelBuilder;
    }

    @GetMapping("/reportes.html")
    public String reportes(HttpSession session, Model model,
                           @RequestParam(required = false) Long proyectoId,
                           @RequestParam(required = false) String estado,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        UsuarioSesion usuario = usuario(session);
        ReportePmDatos datos = reporteService.generar(usuario.getPerfilId(), proyectoId, estado, desde, hasta);
        shellModelBuilder.aplicar(model, usuario, "reportes.html", "Reportes de proyectos",
                datos.getTotalProyectos() + " proyecto(s) en el resultado · actualizado " + datos.getGenerado());
        model.addAttribute("datos", datos);
        model.addAttribute("proyectosDisponibles", reporteService.proyectosGestionados(usuario.getPerfilId()));
        model.addAttribute("proyectoId", proyectoId);
        model.addAttribute("estado", estado);
        model.addAttribute("desde", desde);
        model.addAttribute("hasta", hasta);
        model.addAttribute("queryExport", query(proyectoId, estado, desde, hasta));
        return "project-manager/reportes";
    }

    @GetMapping("/reportes.xlsx")
    public void excel(HttpSession session, HttpServletResponse response,
                      @RequestParam(required = false) Long proyectoId,
                      @RequestParam(required = false) String estado,
                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) throws IOException {
        ReportePmDatos datos = reporteService.generar(usuario(session).getPerfilId(), proyectoId, estado, desde, hasta);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=skillbridge-reporte-proyectos.xlsx");
        exportService.escribirExcel(datos, response.getOutputStream());
    }

    @GetMapping("/reportes.pdf")
    public void pdf(HttpSession session, HttpServletResponse response,
                    @RequestParam(required = false) Long proyectoId,
                    @RequestParam(required = false) String estado,
                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) throws IOException {
        ReportePmDatos datos = reporteService.generar(usuario(session).getPerfilId(), proyectoId, estado, desde, hasta);
        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "attachment; filename=skillbridge-reporte-proyectos.pdf");
        exportService.escribirPdf(datos, response.getOutputStream());
    }

    private UsuarioSesion usuario(HttpSession session) {
        return (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
    }

    private String query(Long proyectoId, String estado, LocalDate desde, LocalDate hasta) {
        StringBuilder q = new StringBuilder("?");
        if (proyectoId != null) q.append("proyectoId=").append(proyectoId).append('&');
        if (estado != null && !estado.isBlank()) q.append("estado=").append(URLEncoder.encode(estado, StandardCharsets.UTF_8)).append('&');
        if (desde != null) q.append("desde=").append(desde).append('&');
        if (hasta != null) q.append("hasta=").append(hasta).append('&');
        if (q.charAt(q.length() - 1) == '&') q.setLength(q.length() - 1);
        return q.toString();
    }
}
