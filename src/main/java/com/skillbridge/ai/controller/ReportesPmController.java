package com.skillbridge.ai.controller;

import com.skillbridge.ai.dto.ReportePmDatos;
import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.service.AsistenteIaService;
import com.skillbridge.ai.service.ReporteIaService;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/project-manager")
public class ReportesPmController {

    private final ReportePmService reporteService;
    private final ReportePmExportService exportService;
    private final ShellModelBuilder shellModelBuilder;
    private final ReporteIaService reporteIaService;

    public ReportesPmController(ReportePmService reporteService, ReportePmExportService exportService,
                                ShellModelBuilder shellModelBuilder, ReporteIaService reporteIaService) {
        this.reporteService = reporteService;
        this.exportService = exportService;
        this.shellModelBuilder = shellModelBuilder;
        this.reporteIaService = reporteIaService;
    }

    @GetMapping("/reportes.html")
    public String reportes(HttpSession session, Model model,
                           @RequestParam(name = "proyectoId", required = false) List<Long> proyectoId,
                           @RequestParam(required = false) String estado,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                           @RequestParam(required = false, defaultValue = "false") boolean soloRiesgo) {
        UsuarioSesion usuario = usuario(session);
        ReportePmDatos datos = reporteService.generar(usuario.getPerfilId(), proyectoId, estado, desde, hasta, soloRiesgo);
        shellModelBuilder.aplicar(model, usuario, "reportes.html", "Reportes de proyectos",
                datos.getTotalProyectos() + " proyecto(s) en el resultado · actualizado " + datos.getGenerado());
        model.addAttribute("datos", datos);
        model.addAttribute("proyectosDisponibles", reporteService.proyectosGestionados(usuario.getPerfilId()));
        model.addAttribute("proyectoId", proyectoId);
        model.addAttribute("estado", estado);
        model.addAttribute("desde", desde);
        model.addAttribute("hasta", hasta);
        model.addAttribute("queryExport", query(proyectoId, estado, desde, hasta, soloRiesgo));
        return "project-manager/reportes";
    }

    @GetMapping("/reportes.xlsx")
    public void excel(HttpSession session, HttpServletResponse response,
                      @RequestParam(name = "proyectoId", required = false) List<Long> proyectoId,
                      @RequestParam(required = false) String estado,
                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                      @RequestParam(required = false, defaultValue = "false") boolean soloRiesgo) throws IOException {
        ReportePmDatos datos = reporteService.generar(usuario(session).getPerfilId(), proyectoId, estado, desde, hasta, soloRiesgo);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=skillbridge-reporte-proyectos.xlsx");
        exportService.escribirExcel(datos, response.getOutputStream());
    }

    @GetMapping("/reportes.pdf")
    public void pdf(HttpSession session, HttpServletResponse response,
                    @RequestParam(name = "proyectoId", required = false) List<Long> proyectoId,
                    @RequestParam(required = false) String estado,
                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                    @RequestParam(required = false, defaultValue = "false") boolean soloRiesgo) throws IOException {
        ReportePmDatos datos = reporteService.generar(usuario(session).getPerfilId(), proyectoId, estado, desde, hasta, soloRiesgo);
        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "attachment; filename=skillbridge-reporte-proyectos.pdf");
        exportService.escribirPdf(datos, response.getOutputStream());
    }

    /**
     * Resumen ejecutivo con IA (RF nuevo): toma los mismos filtros que la
     * vista/exportables y le pide a Claude un resumen de 3-5 lineas. No
     * persiste nada - se regenera cada vez que el PM hace clic en el boton.
     */
    @PostMapping("/reportes/resumen-ia")
    @ResponseBody
    public Map<String, Object> resumenIa(HttpSession session,
                    @RequestParam(name = "proyectoId", required = false) List<Long> proyectoId,
                    @RequestParam(required = false) String estado,
                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                    @RequestParam(required = false, defaultValue = "false") boolean soloRiesgo) {
        ReportePmDatos datos = reporteService.generar(usuario(session).getPerfilId(), proyectoId, estado, desde, hasta, soloRiesgo);
        AsistenteIaService.Resultado r = reporteIaService.resumirReportePm(datos);
        return Map.of("ok", r.ok(), "texto", r.texto());
    }

    private UsuarioSesion usuario(HttpSession session) {
        return (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
    }

    private String query(List<Long> proyectoId, String estado, LocalDate desde, LocalDate hasta, boolean soloRiesgo) {
        StringBuilder q = new StringBuilder("?");
        // Selección múltiple (RG-217): se repite el parámetro por cada proyecto.
        if (proyectoId != null) for (Long id : proyectoId) if (id != null) q.append("proyectoId=").append(id).append('&');
        if (estado != null && !estado.isBlank()) q.append("estado=").append(URLEncoder.encode(estado, StandardCharsets.UTF_8)).append('&');
        if (desde != null) q.append("desde=").append(desde).append('&');
        if (hasta != null) q.append("hasta=").append(hasta).append('&');
        if (soloRiesgo) q.append("soloRiesgo=true").append('&');
        if (q.charAt(q.length() - 1) == '&') q.setLength(q.length() - 1);
        return q.toString();
    }
}
