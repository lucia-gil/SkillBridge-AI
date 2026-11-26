package com.skillbridge.ai.controller;

import com.skillbridge.ai.dto.FichaProyecto;
import com.skillbridge.ai.dto.ReportePmDatos;
import com.skillbridge.ai.dto.ReportePmFila;
import com.skillbridge.ai.service.FichaProyectoService;
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
    private final FichaProyectoService fichaService;

    public ReportesPmController(ReportePmService reporteService, ReportePmExportService exportService,
                                ShellModelBuilder shellModelBuilder, ReporteIaService reporteIaService,
                                FichaProyectoService fichaService) {
        this.reporteService = reporteService;
        this.exportService = exportService;
        this.shellModelBuilder = shellModelBuilder;
        this.reporteIaService = reporteIaService;
        this.fichaService = fichaService;
    }

    @GetMapping("/reportes.html")
    public String reportes(HttpSession session, Model model,
                           @RequestParam(name = "proyectoId", required = false) List<Long> proyectoId,
                           @RequestParam(required = false) String estado,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                           @RequestParam(required = false, defaultValue = "false") boolean soloRiesgo,
                           @RequestParam(required = false) String periodo,
                           @RequestParam(required = false) Integer ultimos) {
        UsuarioSesion usuario = usuario(session);
        ReportePmService.FiltrosPm f = new ReportePmService.FiltrosPm(proyectoId, estado, desde, hasta, soloRiesgo, periodo, ultimos);
        ReportePmDatos datos = reporteService.generar(usuario.getPerfilId(), f);
        shellModelBuilder.aplicar(model, usuario, "reportes.html", "Reportes de proyectos",
                datos.getTotalProyectos() + " proyecto(s) en el resultado · actualizado " + datos.getGenerado());
        model.addAttribute("datos", datos);
        // En pantalla las fichas van sin IA (carga rápida); el botón "Generar resúmenes IA" las completa.
        model.addAttribute("fichas", fichas(usuario, f, datos, false));
        model.addAttribute("maxFichas", FichaProyectoService.MAXIMO_FICHAS);
        model.addAttribute("proyectosDisponibles", reporteService.proyectosGestionados(usuario.getPerfilId()));
        model.addAttribute("proyectoId", proyectoId);
        model.addAttribute("estado", estado);
        model.addAttribute("desde", desde);
        model.addAttribute("hasta", hasta);
        model.addAttribute("periodo", f.periodo());
        model.addAttribute("ultimos", f.ultimos());
        model.addAttribute("queryExport", query(proyectoId, estado, desde, hasta, soloRiesgo, f.periodo(), f.ultimos()));
        return "project-manager/reportes";
    }

    @GetMapping("/reportes.xlsx")
    public void excel(HttpSession session, HttpServletResponse response,
                      @RequestParam(name = "proyectoId", required = false) List<Long> proyectoId,
                      @RequestParam(required = false) String estado,
                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                      @RequestParam(required = false, defaultValue = "false") boolean soloRiesgo,
                      @RequestParam(required = false) String periodo,
                      @RequestParam(required = false) Integer ultimos,
                      @RequestParam(required = false, defaultValue = "true") boolean ia) throws IOException {
        UsuarioSesion usuario = usuario(session);
        ReportePmService.FiltrosPm f = new ReportePmService.FiltrosPm(proyectoId, estado, desde, hasta, soloRiesgo, periodo, ultimos);
        ReportePmDatos datos = reporteService.generar(usuario.getPerfilId(), f);
        List<FichaProyecto> fichas = fichas(usuario, f, datos, ia);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=skillbridge-reporte-proyectos.xlsx");
        exportService.escribirExcel(datos, fichas, response.getOutputStream());
    }

    @GetMapping("/reportes.pdf")
    public void pdf(HttpSession session, HttpServletResponse response,
                    @RequestParam(name = "proyectoId", required = false) List<Long> proyectoId,
                    @RequestParam(required = false) String estado,
                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                    @RequestParam(required = false, defaultValue = "false") boolean soloRiesgo,
                    @RequestParam(required = false) String periodo,
                    @RequestParam(required = false) Integer ultimos,
                    @RequestParam(required = false, defaultValue = "true") boolean ia) throws IOException {
        UsuarioSesion usuario = usuario(session);
        ReportePmService.FiltrosPm f = new ReportePmService.FiltrosPm(proyectoId, estado, desde, hasta, soloRiesgo, periodo, ultimos);
        ReportePmDatos datos = reporteService.generar(usuario.getPerfilId(), f);
        List<FichaProyecto> fichas = fichas(usuario, f, datos, ia);
        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "attachment; filename=skillbridge-reporte-proyectos.pdf");
        exportService.escribirPdf(datos, fichas, response.getOutputStream());
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
                    @RequestParam(required = false, defaultValue = "false") boolean soloRiesgo,
                    @RequestParam(required = false) String periodo,
                    @RequestParam(required = false) Integer ultimos) {
        ReportePmDatos datos = reporteService.generar(usuario(session).getPerfilId(),
                new ReportePmService.FiltrosPm(proyectoId, estado, desde, hasta, soloRiesgo, periodo, ultimos));
        AsistenteIaService.Resultado r = reporteIaService.resumirReportePm(datos);
        return Map.of("ok", r.ok(), "texto", r.texto());
    }

    /** Resúmenes IA de las fichas visibles (botón de la pestaña "Fichas"): id del proyecto → resumen. */
    @PostMapping("/reportes/fichas-ia")
    @ResponseBody
    public Map<String, Object> fichasIa(HttpSession session,
                    @RequestParam(name = "proyectoId", required = false) List<Long> proyectoId,
                    @RequestParam(required = false) String estado,
                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                    @RequestParam(required = false, defaultValue = "false") boolean soloRiesgo,
                    @RequestParam(required = false) String periodo,
                    @RequestParam(required = false) Integer ultimos) {
        UsuarioSesion usuario = usuario(session);
        ReportePmService.FiltrosPm f = new ReportePmService.FiltrosPm(proyectoId, estado, desde, hasta, soloRiesgo, periodo, ultimos);
        ReportePmDatos datos = reporteService.generar(usuario.getPerfilId(), f);
        Map<String, String> resumenes = new java.util.LinkedHashMap<>();
        for (FichaProyecto ficha : fichas(usuario, f, datos, true)) {
            if (ficha.resumenIa() != null) resumenes.put(String.valueOf(ficha.id()), ficha.resumenIa());
        }
        return Map.of("ok", !resumenes.isEmpty(), "resumenes", resumenes);
    }

    /** Fichas de los mismos proyectos que muestra el reporte (respeta "Solo en riesgo"). */
    private List<FichaProyecto> fichas(UsuarioSesion usuario, ReportePmService.FiltrosPm f, ReportePmDatos datos, boolean ia) {
        java.util.Set<Long> visibles = datos.getFilas().stream().map(ReportePmFila::getProyectoId).collect(java.util.stream.Collectors.toSet());
        return fichaService.fichas(reporteService.proyectosFiltrados(usuario.getPerfilId(), f).stream()
                .filter(p -> visibles.contains(p.getId())).toList(), ia);
    }

    private UsuarioSesion usuario(HttpSession session) {
        return (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
    }

    private String query(List<Long> proyectoId, String estado, LocalDate desde, LocalDate hasta, boolean soloRiesgo,
                         String periodo, Integer ultimos) {
        StringBuilder q = new StringBuilder("?");
        // Selección múltiple (RG-217): se repite el parámetro por cada proyecto.
        if (proyectoId != null) for (Long id : proyectoId) if (id != null) q.append("proyectoId=").append(id).append('&');
        if (estado != null && !estado.isBlank()) q.append("estado=").append(URLEncoder.encode(estado, StandardCharsets.UTF_8)).append('&');
        if (desde != null) q.append("desde=").append(desde).append('&');
        if (hasta != null) q.append("hasta=").append(hasta).append('&');
        if (soloRiesgo) q.append("soloRiesgo=true").append('&');
        if (periodo != null && !periodo.isEmpty()) q.append("periodo=").append(periodo).append('&');
        if (ultimos != null) q.append("ultimos=").append(ultimos).append('&');
        if (q.charAt(q.length() - 1) == '&') q.setLength(q.length() - 1);
        return q.toString();
    }
}
