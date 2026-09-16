package com.skillbridge.ai.web;

import com.skillbridge.ai.dto.MiProyectoFila;
import com.skillbridge.ai.dto.ProyectoDetalle;
import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.service.ProyectoService;
import com.skillbridge.ai.service.ShellModelBuilder;
import com.skillbridge.ai.util.SesionKeys;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mis proyectos (colaborador/proyectos.html): "Proyectos actuales" (mismas
 * filas que colaborador/inicio.html) más "Historial completo" (todas las
 * asignaciones, activas o finalizadas). El detalle por proyecto reutiliza
 * ProyectoService.obtenerDetalle(...), el mismo que usa Administrador, pero
 * la plantilla del colaborador solo muestra la parte de lectura (equipo y
 * descripción) - no expone los formularios de asignar/cambiar estado.
 */
@Controller
@RequestMapping("/colaborador")
public class ColaboradorProyectosController {

    private final ProyectoService proyectoService;
    private final ShellModelBuilder shellModelBuilder;

    public ColaboradorProyectosController(ProyectoService proyectoService, ShellModelBuilder shellModelBuilder) {
        this.proyectoService = proyectoService;
        this.shellModelBuilder = shellModelBuilder;
    }

    @GetMapping("/proyectos.html")
    public String proyectos(HttpSession session, Model model) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        Long perfilId = sesion.getPerfilId();

        List<MiProyectoFila> activos = proyectoService.misProyectosActivos(perfilId);
        List<MiProyectoFila> historial = proyectoService.misProyectosHistorial(perfilId);

        // Un colaborador puede tener más de una asignación histórica al mismo
        // proyecto (p.ej. reincorporado tras una finalización) - se deduplica
        // por proyectoId para no repetir la misma consulta de detalle.
        Map<Long, ProyectoDetalle> detallesPorProyecto = new LinkedHashMap<>();
        for (MiProyectoFila fila : historial) {
            detallesPorProyecto.computeIfAbsent(fila.getProyectoId(), proyectoService::obtenerDetalle);
        }
        List<ProyectoDetalle> detalles = new ArrayList<>(detallesPorProyecto.values());

        shellModelBuilder.aplicar(model, sesion, "proyectos.html", "Mis proyectos",
                activos.size() + " proyectos actuales · " + historial.size() + " en tu historial");

        model.addAttribute("proyectosActivos", activos);
        model.addAttribute("historial", historial);
        model.addAttribute("detalles", detalles);
        return "colaborador/proyectos";
    }
}
