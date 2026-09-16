package com.skillbridge.ai.web;

import com.skillbridge.ai.dto.PerfilOpcion;
import com.skillbridge.ai.dto.ProyectoDetalle;
import com.skillbridge.ai.dto.ProyectoFila;
import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.service.ProyectoService;
import com.skillbridge.ai.service.ShellModelBuilder;
import com.skillbridge.ai.util.OperacionInvalidaException;
import com.skillbridge.ai.util.SesionKeys;
import jakarta.servlet.http.HttpSession;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * CRUD de Asignaciones para el Resource Manager (resource-manager/asignaciones.html).
 *
 * A diferencia de AsignacionesPmController (que solo muestra los proyectos
 * donde el usuario es Project Manager activo), aqui el Resource Manager ve
 * TODOS los proyectos de la organizacion: RF04 dice que tanto PM como RM
 * pueden asignar colaboradores, y el RM lo hace a nivel global, no solo en
 * "sus" proyectos (el no lidera ningun proyecto por defecto, ver
 * Roles.RESOURCE_MANAGER).
 *
 * Reutiliza ProyectoService.asignarColaborador(...) y finalizarAsignacion(...)
 * TAL CUAL las usa el PM y el Administrador: la regla de negocio (limite de
 * carga, maximo de proyectos simultaneos, no duplicar asignacion activa) es
 * una sola, definida una sola vez en el Service, y los 3 roles que pueden
 * asignar la comparten. Esto evita que, si el dia de manana cambia el
 * limite de carga, haya que acordarse de actualizarlo en 3 lugares
 * distintos.
 */
@Controller
@RequestMapping("/resource-manager")
public class RmAsignacionesController {

    private final ProyectoService proyectoService;
    private final ShellModelBuilder shellModelBuilder;

    // Inyeccion por constructor (no @Autowired en el campo): es el estilo
    // que ya usa el resto del proyecto (ver AsignacionesPmController) y es
    // la forma recomendada en Spring porque permite que el objeto quede
    // "completo" desde que se crea, sin campos que puedan quedar en null.
    public RmAsignacionesController(ProyectoService proyectoService, ShellModelBuilder shellModelBuilder) {
        this.proyectoService = proyectoService;
        this.shellModelBuilder = shellModelBuilder;
    }

    /**
     * READ - Muestra el tablero de asignaciones con TODOS los proyectos y
     * sus equipos.
     *
     * Por que armamos "detalle por detalle" en vez de traer todo en una
     * sola consulta: el numero de proyectos esperado en un curso es chico
     * (decenas, no miles), asi que el costo de hacer varias consultas
     * pequenas es insignificante, y a cambio el codigo queda mucho mas
     * simple de leer y de explicar. Es la misma decision que ya tomo tu
     * companero en AdminProyectosController.
     */
    @GetMapping("/asignaciones.html")
    public String asignaciones(HttpSession session, Model model) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);

        List<ProyectoFila> filas = proyectoService.listar();
        List<ProyectoDetalle> proyectos = filas.stream()
                .map(f -> proyectoService.obtenerDetalle(f.getId()))
                .collect(Collectors.toList());

        // Lista de colaboradores para llenar el <select> del modal
        // "Registrar asignacion" - ya existe en ProyectoService, no hay
        // que escribir ninguna consulta nueva.
        List<PerfilOpcion> perfiles = proyectoService.listarPerfilesParaAsignar();

        long totalAsignaciones = proyectos.stream().mapToLong(p -> p.getEquipo().size()).sum();

        // ShellModelBuilder llena el sidebar/topbar (nombre de usuario,
        // rol, badges de notificaciones, etc.) - es codigo compartido por
        // TODAS las pantallas del sistema, no algo que tengamos que armar
        // para Resource Manager.
        shellModelBuilder.aplicar(model, sesion, "asignaciones.html", "Asignaciones",
                totalAsignaciones + " asignaciones activas en " + proyectos.size() + " proyectos");

        model.addAttribute("proyectos", proyectos);
        model.addAttribute("perfiles", perfiles);

        // El nombre del archivo que devolvemos ("resource-manager/asignaciones")
        // le dice a Spring Boot que busque la plantilla en
        // templates/resource-manager/asignaciones.html
        return "resource-manager/asignaciones";
    }

    /**
     * CREATE - Registrar una nueva asignacion (boton "Registrar asignacion").
     *
     * Este metodo NO valida limite de carga ni proyectos simultaneos aqui:
     * esa logica ya vive dentro de proyectoService.asignarColaborador(...)
     * (ver ProyectoService.java) y lanza OperacionInvalidaException si algo
     * no cumple. El Controller solo se encarga de recibir los datos del
     * formulario y mostrar el mensaje de exito o error - separacion de
     * responsabilidades: el Controller habla HTTP, el Service habla reglas
     * de negocio.
     */
    @PostMapping("/proyectos/{id}/asignar")
    public String asignar(@PathVariable Long id,
                          @RequestParam Long perfilId,
                          @RequestParam String rolEnProyecto,
                          @RequestParam int cargaPorcentaje,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
                          HttpSession session,
                          RedirectAttributes redirectAttributes) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        try {
            proyectoService.asignarColaborador(id, perfilId, rolEnProyecto, cargaPorcentaje,
                    fechaInicio, sesion.getUsuarioId());
            // addFlashAttribute (no addAttribute): el mensaje debe
            // sobrevivir UN solo redirect y desaparecer despues, para que
            // si el usuario refresca la pagina no se le repita el aviso.
            redirectAttributes.addFlashAttribute("exito", "Colaborador asignado al proyecto.");
        } catch (OperacionInvalidaException ex) {
            // Aqui es donde "aterriza" el mensaje de error si, por
            // ejemplo, la carga supera el 100% - el texto exacto lo arma
            // el Service (ej. "Ana ya tiene 90% de carga activa...").
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/resource-manager/asignaciones.html";
    }

    /**
     * UPDATE (soft) - "Finalizar" una asignacion.
     *
     * IMPORTANTE para la sustentacion: esto NO es un DELETE. Nunca se
     * borra una fila de la tabla asignaciones - se marca estado='finalizada'
     * y se llena fecha_fin. Es una decision de disenio de todo el proyecto
     * (soft delete en todas las tablas) para no perder el historial que
     * pide RF03 ("consultar informacion historica de proyectos"). Por eso
     * el metodo del Service se llama finalizarAsignacion, no
     * eliminarAsignacion.
     */
    @PostMapping("/asignaciones/{asignacionId}/finalizar")
    public String finalizar(@PathVariable Long asignacionId,
                            HttpSession session,
                            RedirectAttributes redirectAttributes) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        try {
            proyectoService.finalizarAsignacion(asignacionId, sesion.getUsuarioId());
            redirectAttributes.addFlashAttribute("exito", "Asignación finalizada.");
        } catch (OperacionInvalidaException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/resource-manager/asignaciones.html";
    }
}