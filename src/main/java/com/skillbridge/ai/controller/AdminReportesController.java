package com.skillbridge.ai.controller;

import com.skillbridge.ai.dto.EstadoProyectoConConteo;
import com.skillbridge.ai.dto.OcupacionColaboradorFila;
import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.dto.VacanteHabilidad;
import com.skillbridge.ai.service.ReporteService;
import com.skillbridge.ai.service.ReporteExportService;
import com.skillbridge.ai.dto.ReporteExportable;
import com.skillbridge.ai.repository.CursoRepository;
import com.skillbridge.ai.repository.UsuarioRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestParam;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import com.skillbridge.ai.service.ShellModelBuilder;
import com.skillbridge.ai.util.SesionKeys;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;
import java.util.Map;

/**
 * Reportes globales (administrador/reportes.html). Ver Javadoc de
 * ReporteService: "Vacantes por habilidad" y "consultas al Asistente IA"
 * del mockup no tienen fuente real y se reemplazan por métricas que sí la
 * tienen (habilidades más declaradas, KPIs reales).
 */
@Controller
@RequestMapping("/administrador")
public class AdminReportesController {

    private final ReporteService reporteService;
    private final ShellModelBuilder shellModelBuilder;
    private final CursoRepository cursoRepository;
    private final UsuarioRepository usuarioRepository;
    private final ReporteExportService exportador;

    public AdminReportesController(ReporteService reporteService, ShellModelBuilder shellModelBuilder,
                                   CursoRepository cursoRepository, UsuarioRepository usuarioRepository,
                                   ReporteExportService exportador) {
        this.reporteService = reporteService;
        this.shellModelBuilder = shellModelBuilder;
        this.cursoRepository = cursoRepository;
        this.usuarioRepository = usuarioRepository;
        this.exportador = exportador;
    }

    @GetMapping("/reportes.html")
    public String reportes(HttpSession session, Model model) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);

        Map<String, Long> kpis = reporteService.kpisAdministrador();
        List<VacanteHabilidad> topHabilidades = reporteService.topHabilidadesDeclaradas(8);
        long maxDeclarada = topHabilidades.stream().mapToLong(VacanteHabilidad::vacantes).max().orElse(1);
        List<EstadoProyectoConConteo> proyectosPorEstado = reporteService.proyectosPorEstadoDetallado();
        List<OcupacionColaboradorFila> ocupacion = reporteService.ocupacionPorColaborador();

        shellModelBuilder.aplicar(model, sesion, "reportes.html", "Reportes globales",
                "Vista consolidada de toda la plataforma");

        model.addAttribute("kpis", kpis);
        model.addAttribute("topHabilidades", topHabilidades);
        model.addAttribute("maxDeclarada", maxDeclarada);
        model.addAttribute("proyectosPorEstado", proyectosPorEstado);
        model.addAttribute("ocupacion", ocupacion);
        model.addAttribute("cursosPublicados", cursoRepository.countByEstado("PUBLICADO"));
        model.addAttribute("usuariosActivos", usuarioRepository.countByEstado("activo"));
        return "administrador/reportes";
    }

    @GetMapping("/reportes/exportar")
    public ResponseEntity<byte[]> exportar(@RequestParam(defaultValue = "xlsx") String formato) throws IOException {
        if (!formato.equals("xlsx") && !formato.equals("pdf")) return ResponseEntity.badRequest().build();
        Map<String, Long> kpis = reporteService.kpisAdministrador();
        ReporteExportable reporte = new ReporteExportable("Reporte global", "Datos actuales de la plataforma")
                .kpi("Colaboradores activos", kpis.get("colaboradoresActivos"))
                .kpi("Usuarios activos", usuarioRepository.countByEstado("activo"))
                .kpi("Proyectos en curso", kpis.get("proyectosEnCurso"))
                .kpi("Cursos publicados", cursoRepository.countByEstado("PUBLICADO"));
        ReporteExportable.Tabla estados = reporte.tabla("Proyectos por estado", new String[]{"Estado", "Proyectos"}, new float[]{3, 1});
        reporteService.proyectosPorEstadoDetallado().forEach(e -> estados.fila(e.getLabel(), e.getConteo()));
        ReporteExportable.Tabla habilidades = reporte.tabla("Habilidades declaradas", new String[]{"Habilidad", "Personas"}, new float[]{3, 1});
        reporteService.topHabilidadesDeclaradas(50).forEach(h -> habilidades.fila(h.nombre(), h.vacantes()));
        ReporteExportable.Tabla ocupacion = reporte.tabla("Ocupacion", new String[]{"Colaborador", "Cargo", "Carga", "Límite", "Estado"}, new float[]{3, 2, 1, 1, 2});
        reporteService.ocupacionPorColaborador().forEach(o -> ocupacion.fila(o.getNombre(), o.getCargo(), ReporteExportable.porcentaje(o.getPromedio()), o.getLimite() + "%", o.getEstadoLabel()));
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        if (formato.equals("pdf")) exportador.escribirPdf(reporte, salida); else exportador.escribirExcel(reporte, salida);
        String nombre = exportador.nombreArchivo("skillbridge-reporte-global") + "." + formato;
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nombre + "\"")
                .contentType(MediaType.parseMediaType(formato.equals("pdf") ? "application/pdf" : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(salida.toByteArray());
    }
}
