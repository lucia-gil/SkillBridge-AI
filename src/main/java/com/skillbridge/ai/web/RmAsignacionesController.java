package com.skillbridge.ai.web;

import com.skillbridge.ai.service.SelectorPersonasService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ResponseBody;

import com.skillbridge.ai.dto.PerfilOpcion;
import com.skillbridge.ai.dto.ProyectoDetalle;
import com.skillbridge.ai.dto.ProyectoFila;
import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.service.AsignacionMasivaService;
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
    private final AsignacionMasivaService asignacionMasivaService;
    private final SelectorPersonasService selectorPersonasService;

    // Inyeccion por constructor (no @Autowired en el campo): es el estilo
    // que ya usa el resto del proyecto (ver AsignacionesPmController) y es
    // la forma recomendada en Spring porque permite que el objeto quede
    // "completo" desde que se crea, sin campos que puedan quedar en null.
    public RmAsignacionesController(ProyectoService proyectoService, ShellModelBuilder shellModelBuilder,
                                    AsignacionMasivaService asignacionMasivaService,
                                    SelectorPersonasService selectorPersonasService) {
        this.proyectoService = proyectoService;
        this.shellModelBuilder = shellModelBuilder;
        this.asignacionMasivaService = asignacionMasivaService;
        this.selectorPersonasService = selectorPersonasService;
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


        long totalAsignaciones = proyectos.stream().mapToLong(p -> p.getEquipo().size()).sum();

        // Estas dos sumas alimentan las tarjetas resumen de la nueva tabla
        // paginada (mismo patron que AsignacionesPmController): total de
        // colaboradores activos y total de vacantes pendientes, sumados
        // sobre TODOS los proyectos de la plataforma (no solo los del RM,
        // ya que el RM ve el panorama global).
        long totalColaboradores = proyectos.stream().mapToLong(ProyectoDetalle::getColaboradoresAsignados).sum();
        long totalVacantes = proyectos.stream().mapToLong(ProyectoDetalle::getVacantes).sum();

        // ShellModelBuilder llena el sidebar/topbar (nombre de usuario,
        // rol, badges de notificaciones, etc.) - es codigo compartido por
        // TODAS las pantallas del sistema, no algo que tengamos que armar
        // para Resource Manager.
        shellModelBuilder.aplicar(model, sesion, "asignaciones.html", "Asignaciones",
                totalAsignaciones + " asignaciones activas en " + proyectos.size() + " proyectos");

        model.addAttribute("proyectos", proyectos);
        // Modelo v7 (fases 4 y 5): las personas se buscan en el servidor desde
        // el selector (candidatos / candidatos-pm); ya no se incrusta la lista.
        model.addAttribute("limiteCarga", proyectoService.limiteCargaConfigurado());
        model.addAttribute("totalColaboradores", totalColaboradores);
        model.addAttribute("totalVacantes", totalVacantes);

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
    /**
     * Selector de personas (modelo v7, fase 4): búsqueda en el servidor con
     * filtros, orden y paginación. Devuelve JSON para selector-personas.js.
     */
    @GetMapping("/proyectos/{id}/candidatos")
    @ResponseBody
    public ResponseEntity<?> candidatos(@PathVariable Long id,
                                          @RequestParam(required = false) String q,
                                          @RequestParam(name = "habilidad", required = false) List<Long> habilidades,
                                          @RequestParam(required = false) String cargo,
                                          @RequestParam(required = false) Integer disponibleMin,
                                          @RequestParam(required = false) Integer experienciaMin,
                                          @RequestParam(defaultValue = "false") boolean soloConCapacidad,
                                          @RequestParam(defaultValue = "50") int dedicacion,
                                          @RequestParam(defaultValue = "match") String orden,
                                          @RequestParam(defaultValue = "1") int pagina) {
        try {
            return ResponseEntity.ok(selectorPersonasService.buscar(id, new SelectorPersonasService.Filtros(q, habilidades, cargo, disponibleMin,
                    experienciaMin, soloConCapacidad, dedicacion, orden, pagina)));
        } catch (OperacionInvalidaException ex) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", ex.getMessage()));
        }
    }

    /** Candidatos para "Cambiar PM" (modelo v7, fase 5): selector en modo "pm". */
    @GetMapping("/proyectos/{id}/candidatos-pm")
    @ResponseBody
    public ResponseEntity<?> candidatosPm(@PathVariable Long id,
                                              @RequestParam(required = false) String q,
                                              @RequestParam(defaultValue = "match") String orden,
                                              @RequestParam(defaultValue = "false") boolean soloConCapacidad,
                                              @RequestParam(defaultValue = "1") int pagina) {
        try {
            return ResponseEntity.ok(selectorPersonasService.buscarParaPm(id, new SelectorPersonasService.Filtros(q, null, null, null, null,
                    soloConCapacidad, ProyectoService.CARGA_PM_POR_DEFECTO, orden, pagina)));
        } catch (OperacionInvalidaException ex) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/proyectos/{id}/asignar")
    public String asignar(@PathVariable Long id,
                          @RequestParam Long perfilId,
                          @RequestParam int cargaPorcentaje,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
                          HttpSession session,
                          RedirectAttributes redirectAttributes) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        try {
            // Siempre como colaborador: el rol PM solo se otorga al crear el
            // proyecto o con "Cambiar PM" (modelo v7).
            proyectoService.asignarColaborador(id, perfilId, cargaPorcentaje, fechaInicio, sesion.getUsuarioId());
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
     * CREATE en masa - "Asignar seleccionados" (checkboxes del modal).
     * Reutiliza la misma regla de negocio que asignar(...): cada persona
     * pasa por proyectoService.asignarColaborador(...) en su propia
     * transaccion (ver AsignacionMasivaService). Si alguna no cumple, se
     * informa su error y las demas quedan asignadas.
     */
    @PostMapping("/proyectos/{id}/asignar-varios")
    public String asignarVarios(@PathVariable Long id,
                                @RequestParam(name = "perfilIds", required = false) List<Long> perfilIds,
                                @RequestParam int cargaPorcentaje,
                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
                                HttpSession session,
                                RedirectAttributes redirectAttributes) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        try {
            AsignacionMasivaService.Resultado r = asignacionMasivaService.asignarVarios(
                    id, perfilIds, cargaPorcentaje, fechaInicio, sesion.getUsuarioId());
            if (r.huboAsignados()) redirectAttributes.addFlashAttribute("exito", AsignacionMasivaService.mensajeExito(r));
            if (r.huboErrores()) redirectAttributes.addFlashAttribute("error", AsignacionMasivaService.mensajeError(r));
        } catch (OperacionInvalidaException ex) {
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
                            @RequestParam(required = false) String motivo,
                            @RequestParam(required = false) String volver,
                            HttpSession session,
                            RedirectAttributes redirectAttributes) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        try {
            proyectoService.finalizarAsignacion(asignacionId, motivo, sesion.getUsuarioId());
            redirectAttributes.addFlashAttribute("exito", "Asignación finalizada.");
        } catch (OperacionInvalidaException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        // volver=ocupacion: se finalizo desde "Rebalancear" en Ocupacion.
        return "ocupacion".equals(volver)
                ? "redirect:/resource-manager/ocupacion.html"
                : "redirect:/resource-manager/asignaciones.html";
    }

    /**
     * "Cambiar Project Manager" - reemplaza al PM activo por otro Miembro
     * en un solo paso (ver ProyectoService.reasignarProjectManager). El PM
     * saliente se queda como colaborador si se marcó "mantener" y hay
     * vacante; si no, su asignación se finaliza.
     */
    @PostMapping("/proyectos/{id}/cambiar-pm")
    public String cambiarPm(@PathVariable Long id,
                            @RequestParam Long nuevoPmPerfilId,
                            // Casilla "Mantener como colaborador" del modal (requiere vacante).
                            @RequestParam(defaultValue = "false") boolean mantener,
                            // Dedicación con la que se queda el PM saliente (vacío = la misma).
                            @RequestParam(required = false) Integer cargaSaliente,
                            HttpSession session,
                            RedirectAttributes redirectAttributes) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        try {
            proyectoService.reasignarProjectManager(id, nuevoPmPerfilId, mantener, cargaSaliente, sesion.getUsuarioId());
            redirectAttributes.addFlashAttribute("exito", "Project Manager actualizado.");
        } catch (OperacionInvalidaException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/resource-manager/asignaciones.html";
    }
}