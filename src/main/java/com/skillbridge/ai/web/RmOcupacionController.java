package com.skillbridge.ai.web;

import com.skillbridge.ai.dto.AsignacionCargaFila;
import com.skillbridge.ai.dto.OcupacionColaboradorFila;
import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.service.ProyectoService;
import com.skillbridge.ai.service.ReporteService;
import com.skillbridge.ai.service.ShellModelBuilder;
import com.skillbridge.ai.util.OperacionInvalidaException;
import com.skillbridge.ai.util.SesionKeys;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * "Ocupación del equipo" del Resource Manager (resource-manager/ocupacion.html).
 *
 * La tabla es de lectura, pero cada fila tiene la accion "Rebalancear"
 * (RG-214): un modal con las asignaciones activas de esa persona donde se
 * puede bajar la dedicacion (POST .../dedicacion, aqui) o finalizar una
 * asignacion (reutiliza RmAsignacionesController con volver=ocupacion). Reutiliza ReporteService.ocupacionPorColaborador(),
 * el mismo metodo que ya usa administrador/reportes.html - la ocupacion de
 * un colaborador es un dato global del sistema (suma de sus asignaciones
 * activas en TODOS los proyectos), no algo que cambie segun quien lo mire.
 *
 * Simplificacion respecto al mockup original: se quito el grafico de
 * "evolucion mensual" (barras por mes) porque no existe ninguna tabla que
 * guarde un historico de ocupacion mes a mes - solo tenemos la foto actual.
 * Agregar eso requeriria una tabla nueva (ej. snapshot mensual) que no esta
 * en el alcance de este entregable. Se documenta aqui para que quede claro
 * que es una decision consciente, no un olvido.
 */
@Controller
@RequestMapping("/resource-manager")
public class RmOcupacionController {

    private final ReporteService reporteService;
    private final ShellModelBuilder shellModelBuilder;
    private final ProyectoService proyectoService;

    public RmOcupacionController(ReporteService reporteService, ShellModelBuilder shellModelBuilder,
                                 ProyectoService proyectoService) {
        this.reporteService = reporteService;
        this.shellModelBuilder = shellModelBuilder;
        this.proyectoService = proyectoService;
    }

    @GetMapping("/ocupacion.html")
    public String ocupacion(HttpSession session, Model model) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);

        List<OcupacionColaboradorFila> ocupacion = reporteService.ocupacionPorColaborador();

        // Los 4 "buckets" del mockup (Subutilizado/Optimo/Al limite/Sobre-
        // asignado) no vienen de ninguna tabla - se calculan aqui mismo
        // contando cuantas filas de "ocupacion" caen en cada rango. Es la
        // misma logica que ya usa OcupacionColaboradorFila para decidir su
        // propio badge (getEstadoLabel()), solo que aqui se cuenta cuantos
        // colaboradores hay en cada categoria para los KPI de arriba.
        long subutilizados = ocupacion.stream().filter(o -> o.getPromedio() < 60).count();
        long optimo = ocupacion.stream().filter(o -> o.getPromedio() >= 60 && o.getPromedio() < 90).count();
        long alLimite = ocupacion.stream().filter(o -> o.getPromedio() >= 90 && o.getPromedio() <= 100).count();
        long sobreAsignados = ocupacion.stream().filter(o -> o.getPromedio() > 100).count();

        shellModelBuilder.aplicar(model, sesion, "ocupacion.html", "Ocupación del equipo",
                ocupacion.size() + " colaboradores con asignaciones activas");

        model.addAttribute("ocupacion", ocupacion);
        model.addAttribute("subutilizados", subutilizados);
        model.addAttribute("optimo", optimo);
        model.addAttribute("alLimite", alLimite);
        model.addAttribute("sobreAsignados", sobreAsignados);

        // Asignaciones activas por persona (perfilId -> lista) para el modal
        // "Rebalancear". Volumen de curso: una consulta por colaborador.
        Map<Long, List<AsignacionCargaFila>> asignaciones = new LinkedHashMap<>();
        for (OcupacionColaboradorFila o : ocupacion) {
            if (o.getPerfilId() != null) asignaciones.put(o.getPerfilId(), proyectoService.asignacionesActivasDe(o.getPerfilId()));
        }
        model.addAttribute("asignacionesPorPerfil", asignaciones);
        model.addAttribute("limiteCarga", proyectoService.limiteCargaConfigurado());

        return "resource-manager/ocupacion";
    }

    /** "Rebalancear" -> cambiar la dedicacion (%) de una asignacion activa. */
    @PostMapping("/asignaciones/{asignacionId}/dedicacion")
    public String ajustarDedicacion(@PathVariable Long asignacionId,
                                    @RequestParam int cargaPorcentaje,
                                    @RequestParam(required = false) String motivo,
                                    HttpSession session, RedirectAttributes redirectAttributes) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        try {
            proyectoService.ajustarDedicacion(asignacionId, cargaPorcentaje, motivo, sesion.getUsuarioId());
            redirectAttributes.addFlashAttribute("exito", "Dedicación actualizada a " + cargaPorcentaje + "%.");
        } catch (OperacionInvalidaException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/resource-manager/ocupacion.html";
    }
}