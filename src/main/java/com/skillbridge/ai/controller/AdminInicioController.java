package com.skillbridge.ai.controller;

import com.skillbridge.ai.dto.EstadoProyectoConConteo;
import com.skillbridge.ai.dto.OcupacionColaboradorFila;
import com.skillbridge.ai.dto.UsuarioFila;
import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.dto.VacanteHabilidad;
import com.skillbridge.ai.service.HabilidadService;
import com.skillbridge.ai.service.ReporteService;
import com.skillbridge.ai.service.ShellModelBuilder;
import com.skillbridge.ai.service.UsuarioService;
import com.skillbridge.ai.service.AuditoriaService;
import com.skillbridge.ai.repository.CursoRepository;
import com.skillbridge.ai.repository.AuditoriaLogRepository;
import java.time.LocalDateTime;
import com.skillbridge.ai.util.OperacionInvalidaException;
import com.skillbridge.ai.util.SesionKeys;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

/**
 * Panel de administración (administrador/inicio.html).
 *
 * El mockup original mostraba una "Salud de la plataforma" (servicios de
 * infraestructura fabricados) y consultas al Asistente IA; ninguna de las
 * dos tiene un dato real detrás en esta entrega (no hay monitoreo de
 * infraestructura ni el chatbot está implementado, ver README) - se
 * reemplazan por "Proyectos por estado" y "Requiere tu atención"
 * (colaboradores sobrecargados, habilidades sin proyecto asociado,
 * usuarios sin rol), todo con datos reales de las tablas ya existentes.
 */
@Controller
@RequestMapping("/administrador")
public class AdminInicioController {

    private final UsuarioService usuarioService;
    private final ReporteService reporteService;
    private final HabilidadService habilidadService;
    private final ShellModelBuilder shellModelBuilder;
    private final CursoRepository cursoRepository;
    private final AuditoriaService auditoriaService;
    private final AuditoriaLogRepository auditoriaLogRepository;

    public AdminInicioController(UsuarioService usuarioService, ReporteService reporteService,
                                  HabilidadService habilidadService, ShellModelBuilder shellModelBuilder,
                                  CursoRepository cursoRepository, AuditoriaService auditoriaService,
                                  AuditoriaLogRepository auditoriaLogRepository) {
        this.usuarioService = usuarioService;
        this.reporteService = reporteService;
        this.habilidadService = habilidadService;
        this.shellModelBuilder = shellModelBuilder;
        this.cursoRepository = cursoRepository;
        this.auditoriaService = auditoriaService;
        this.auditoriaLogRepository = auditoriaLogRepository;
    }

    @GetMapping("/inicio.html")
    public String inicio(HttpSession session, Model model) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);

        Map<String, Long> kpisReporte = reporteService.kpisAdministrador();
        Map<String, Long> kpisUsuarios = usuarioService.kpis();
        List<UsuarioFila> usuarios = usuarioService.listar();
        List<EstadoProyectoConConteo> proyectosPorEstado = reporteService.proyectosPorEstadoDetallado();
        List<OcupacionColaboradorFila> sobrecargados = reporteService.colaboradoresSobrecargados();
        List<VacanteHabilidad> topHabilidades = reporteService.topHabilidadesDeclaradas(5);
        long maxDeclarada = topHabilidades.stream().mapToLong(VacanteHabilidad::vacantes).max().orElse(1);

        shellModelBuilder.aplicar(model, sesion, "inicio.html", "Panel de administración",
                "SkillBridge AI · Gestión de talento con IA");

        model.addAttribute("kpiColaboradoresActivos", kpisReporte.get("colaboradoresActivos"));
        model.addAttribute("kpiProyectosEnCurso", kpisReporte.get("proyectosEnCurso"));
        model.addAttribute("kpiOcupacionPromedio", reporteService.ocupacionPromedio());
        model.addAttribute("kpiAsignacionesActivas", kpisReporte.get("asignacionesActivas"));

        model.addAttribute("proyectosPorEstado", proyectosPorEstado);
        model.addAttribute("sobrecargados", sobrecargados);
        model.addAttribute("habilidadesSinAsignar", kpisReporte.get("habilidadesSinAsignar"));
        // Modelo v7: "Miembro" (sin rol fijo) es lo normal, ya no se alerta.

        model.addAttribute("usuariosPreview", usuarios.size() > 6 ? usuarios.subList(0, 6) : usuarios);
        model.addAttribute("totalUsuarios", usuarios.size());

        model.addAttribute("topHabilidades", topHabilidades);
        model.addAttribute("maxDeclarada", maxDeclarada);
        model.addAttribute("cursosPublicados", cursoRepository.countByEstado("PUBLICADO"));
        model.addAttribute("totalCursos", cursoRepository.count());
        model.addAttribute("eventos24h", auditoriaLogRepository.countByFechaAfter(LocalDateTime.now().minusDays(1)));
        model.addAttribute("eventosRecientes", auditoriaService.listarRecientes().stream().limit(5).toList());
        model.addAttribute("totalProyectos", proyectosPorEstado.stream().mapToLong(EstadoProyectoConConteo::getConteo).sum());

        return "administrador/inicio";
    }

    @PostMapping("/inicio/invitar")
    public String invitar(@RequestParam String correo, HttpSession session, RedirectAttributes redirectAttributes) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        try {
            usuarioService.invitar(correo, sesion.getUsuarioId());
            redirectAttributes.addFlashAttribute("exito", "Correo autorizado. Invitación en cola para " + correo + ".");
        } catch (OperacionInvalidaException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/administrador/inicio.html";
    }
}
