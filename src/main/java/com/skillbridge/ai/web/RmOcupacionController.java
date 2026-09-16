package com.skillbridge.ai.web;

import com.skillbridge.ai.dto.OcupacionColaboradorFila;
import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.service.ReporteService;
import com.skillbridge.ai.service.ShellModelBuilder;
import com.skillbridge.ai.util.SesionKeys;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

/**
 * "Ocupación del equipo" del Resource Manager (resource-manager/ocupacion.html).
 *
 * Es una pantalla de SOLO LECTURA: no crea, edita ni borra nada, por eso no
 * hay operaciones POST aqui. Reutiliza ReporteService.ocupacionPorColaborador(),
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

    public RmOcupacionController(ReporteService reporteService, ShellModelBuilder shellModelBuilder) {
        this.reporteService = reporteService;
        this.shellModelBuilder = shellModelBuilder;
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

        return "resource-manager/ocupacion";
    }
}