package com.skillbridge.ai.controller;

import com.skillbridge.ai.service.SelectorPersonasService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ResponseBody;

import com.skillbridge.ai.dto.HabilidadOpcion;
import com.skillbridge.ai.dto.MiProyectoFila;
import com.skillbridge.ai.dto.ProyectoDetalle;
import com.skillbridge.ai.dto.ProyectoExtraidoIa;
import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.model.Asignacion;
import com.skillbridge.ai.model.Habilidad;
import com.skillbridge.ai.repository.AsignacionRepository;
import com.skillbridge.ai.repository.HabilidadRepository;
import com.skillbridge.ai.service.AsignacionMasivaService;
import com.skillbridge.ai.service.ExtraccionProyectoIaService;
import com.skillbridge.ai.service.ProyectoService;
import com.skillbridge.ai.service.TecnologiaCatalogoService;
import com.skillbridge.ai.service.ShellModelBuilder;
import com.skillbridge.ai.service.TalentMatchingService;
import com.skillbridge.ai.util.OperacionInvalidaException;
import com.skillbridge.ai.util.Roles;
import com.skillbridge.ai.util.SesionKeys;
import jakarta.servlet.http.HttpSession;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * CRUD de Proyectos del Project Manager, restringido a SUS proyectos (aquellos
 * donde tiene una asignacion activa como project_manager). Reutiliza
 * ProyectoService: mismas reglas de negocio y validaciones que el panel del
 * administrador. La diferencia es el alcance (solo sus proyectos) y que al
 * crear un proyecto el propio PM queda como responsable automaticamente.
 */
@Controller
@RequestMapping("/project-manager")
public class ProyectosPmController {

    private final ProyectoService proyectoService;
    private final AsignacionRepository asignacionRepository;
    private final ShellModelBuilder shellModelBuilder;
    private final TecnologiaCatalogoService tecnologiaCatalogoService;
    private final TalentMatchingService talentMatchingService;
    private final CambioEstadoProyecto cambioEstadoProyecto;
    private final SelectorPersonasService selectorPersonasService;
    private final ExtraccionProyectoIaService extraccionProyectoIaService;
    private final HabilidadRepository habilidadRepository;

    public ProyectosPmController(ProyectoService proyectoService, AsignacionRepository asignacionRepository,
                                 ShellModelBuilder shellModelBuilder, TecnologiaCatalogoService tecnologiaCatalogoService,
                                 TalentMatchingService talentMatchingService, CambioEstadoProyecto cambioEstadoProyecto,
                                 SelectorPersonasService selectorPersonasService,
                                 ExtraccionProyectoIaService extraccionProyectoIaService,
                                 HabilidadRepository habilidadRepository) {
        this.proyectoService = proyectoService;
        this.asignacionRepository = asignacionRepository;
        this.shellModelBuilder = shellModelBuilder;
        this.tecnologiaCatalogoService = tecnologiaCatalogoService;
        this.talentMatchingService = talentMatchingService;
        this.cambioEstadoProyecto = cambioEstadoProyecto;
        this.selectorPersonasService = selectorPersonasService;
        this.extraccionProyectoIaService = extraccionProyectoIaService;
        this.habilidadRepository = habilidadRepository;
    }

    /** "React, MySQL" -> ["React", "MySQL"] (el formulario manda los chips unidos por coma). */
    static List<String> separarTecnologias(String tecnologias) {
        return tecnologias == null ? List.of() : Arrays.stream(tecnologias.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
    }

    /** IDs de los proyectos donde este perfil es Project Manager activo. */
    private List<Long> misProyectoIds(Long perfilId) {
        return proyectoService.misProyectosActivos(perfilId).stream()
                .filter(m -> Roles.PROJECT_MANAGER.equals(m.getRolEnProyectoCrudo()))
                .map(MiProyectoFila::getProyectoId)
                .collect(Collectors.toList());
    }

    private void exigirPropiedad(Long perfilId, Long proyectoId) {
        if (!misProyectoIds(perfilId).contains(proyectoId)) {
            throw new OperacionInvalidaException("Ese proyecto no esta bajo tu gestion.");
        }
    }

    @GetMapping("/proyectos.html")
    public String proyectos(HttpSession session, Model model) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        List<ProyectoDetalle> proyectos = misProyectoIds(sesion.getPerfilId()).stream()
                .map(proyectoService::obtenerDetalle)
                .collect(Collectors.toList());
        int colaboradoresDisponibles = proyectoService.contarColaboradoresDisponibles(sesion.getPerfilId());

        shellModelBuilder.aplicar(model, sesion, "proyectos.html", "Proyectos",
                "Tus " + proyectos.size() + " proyectos como Project Manager");
        model.addAttribute("proyectos", proyectos);
        model.addAttribute("limiteCarga", proyectoService.limiteCargaConfigurado());
        model.addAttribute("colaboradoresDisponibles", colaboradoresDisponibles);
        // Catálogo del Admin para el autocompletado de tecnologías (chips).
        model.addAttribute("catalogoTecnologias", tecnologiaCatalogoService.catalogo());
        model.addAttribute("maxTecnologias", TecnologiaCatalogoService.MAX_TECNOLOGIAS);
        // Catálogo con id real (tabla habilidades), para "Habilidades requeridas" y "Extraer con IA".
        model.addAttribute("catalogoHabilidadesIa", habilidadRepository.findAllConCategoriaOrderByNombre().stream()
                .map(h -> new HabilidadOpcion(h.getId(), h.getNombre(),
                        h.getCategoria() != null ? h.getCategoria().getNombre() : "Otras"))
                .collect(Collectors.toList()));
        // Tarjetas KPI de "Proyectos" por estado (RF03).
        model.addAttribute("kpiPlanificacion", proyectos.stream().filter(p -> "planificacion".equals(p.getEstadoCrudo())).count());
        model.addAttribute("kpiActivos", proyectos.stream().filter(p -> "activo".equals(p.getEstadoCrudo())).count());
        model.addAttribute("kpiEnPausa", proyectos.stream().filter(p -> "en_pausa".equals(p.getEstadoCrudo())).count());
        model.addAttribute("kpiCancelados", proyectos.stream().filter(p -> "cancelado".equals(p.getEstadoCrudo())).count());
        return "project-manager/proyectos";
    }

    @PostMapping("/proyectos")
    public String crear(@RequestParam String nombre,
                        @RequestParam(required = false) String descripcion,
                        @RequestParam(required = false) String tecnologias,
                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFinEstimada,
                        @RequestParam(defaultValue = "0") int colaboradoresRequeridos,
                        @RequestParam(required = false) String githubUrl,
                        @RequestParam(name = "habilidadId", required = false) List<Long> habilidadId,
                        @RequestParam(name = "nivelRequerido", required = false) List<Integer> nivelRequerido,
                        HttpSession session, RedirectAttributes redirectAttributes) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        try {
            List<String> lista = tecnologiaCatalogoService.normalizar(separarTecnologias(tecnologias), List.of());
            // El Project Manager del proyecto es el propio usuario que lo crea.
            com.skillbridge.ai.model.Proyecto p = proyectoService.crear(nombre, descripcion, lista, fechaInicio, fechaFinEstimada,
                    colaboradoresRequeridos, sesion.getPerfilId(), sesion.getUsuarioId(), githubUrl);
            // Habilidades requeridas (manuales o pre-llenadas por "Extraer con IA"); opcional.
            proyectoService.guardarHabilidadesRequeridas(p.getId(), habilidadId, nivelRequerido);
            redirectAttributes.addFlashAttribute("exito", "Proyecto \"" + nombre.trim() + "\" creado.");
        } catch (OperacionInvalidaException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/project-manager/proyectos.html";
    }

    /**
     * "Extraer con IA" (RF modelo v7 fase 5): sube un documento (PDF, Word o
     * texto) con los datos de un proyecto nuevo y devuelve un borrador JSON
     * para pre-llenar el formulario "Nuevo proyecto". No crea nada: el PM
     * revisa/edita el borrador y solo se guarda si confirma con "Crear proyecto".
     */
    @PostMapping(value = "/proyectos/extraer-ia", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseBody
    public ResponseEntity<?> extraerIa(@RequestParam("archivo") MultipartFile archivo) {
        try {
            ProyectoExtraidoIa borrador = extraccionProyectoIaService.extraer(archivo);
            return ResponseEntity.ok(borrador);
        } catch (OperacionInvalidaException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    /** Cambiar estado de un proyecto propio (ver CambioEstadoProyecto). */
    @PostMapping("/proyectos/{id}/estado")
    public String cambiarEstado(@PathVariable Long id, @RequestParam String estado,
                                @RequestParam(required = false) String razonCancelacion,
                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaReanudacion,
                                @RequestParam(name = "reincorporar", required = false) List<Long> reincorporar,
                                HttpSession session, RedirectAttributes redirectAttributes) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        try {
            exigirPropiedad(sesion.getPerfilId(), id);
        } catch (OperacionInvalidaException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/project-manager/proyectos.html";
        }
        cambioEstadoProyecto.aplicar(id, estado, razonCancelacion, fechaReanudacion, reincorporar,
                sesion.getUsuarioId(), redirectAttributes);
        return "redirect:/project-manager/proyectos.html";
    }

    @PostMapping("/proyectos/{id}/editar")
    public String editar(@PathVariable Long id,
                         @RequestParam String nombre,
                         @RequestParam(required = false) String descripcion,
                         @RequestParam(required = false) String tecnologias,
                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaInicio,
                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFinEstimada,
                         @RequestParam(required = false) String githubUrl,
                         HttpSession session, RedirectAttributes redirectAttributes) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        try {
            exigirPropiedad(sesion.getPerfilId(), id);
            // Se conservan las tecnologías que el proyecto ya tenía aunque no estén en el catálogo.
            List<String> actuales = proyectoService.obtenerDetalle(id).getTecnologias();
            List<String> lista = tecnologiaCatalogoService.normalizar(separarTecnologias(tecnologias), actuales);
            // El estado se cambia aparte ("Cambiar estado"), porque cada
            // transición tiene efectos sobre el equipo (modelo v7, fase 2).
            proyectoService.editarProyecto(id, nombre, descripcion, lista, fechaInicio, fechaFinEstimada,
                    githubUrl, sesion.getUsuarioId());
            redirectAttributes.addFlashAttribute("exito", "Proyecto \"" + nombre.trim() + "\" actualizado.");
        } catch (OperacionInvalidaException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/project-manager/proyectos.html";
    }

    @PostMapping("/proyectos/{id}/eliminar")
    public String eliminar(@PathVariable Long id, @RequestParam(required = false) String motivo,
                           HttpSession session, RedirectAttributes redirectAttributes) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        try {
            exigirPropiedad(sesion.getPerfilId(), id);
            proyectoService.eliminar(id, motivo, sesion.getUsuarioId());
            redirectAttributes.addFlashAttribute("exito", "Proyecto eliminado.");
        } catch (OperacionInvalidaException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/project-manager/proyectos.html";
    }

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
                                          @RequestParam(defaultValue = "1") int pagina,
                                          HttpSession session) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        try {
            exigirPropiedad(sesion.getPerfilId(), id);
            return ResponseEntity.ok(selectorPersonasService.buscar(id, new SelectorPersonasService.Filtros(q, habilidades, cargo, disponibleMin,
                    experienciaMin, soloConCapacidad, dedicacion, orden, pagina)));
        } catch (OperacionInvalidaException ex) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", ex.getMessage()));
        }
    }

    /**
     * Modelo v7: el PM NO asigna directo. "Proponer colaborador" crea una
     * propuesta manual (origen=manual) que el Resource Manager aprueba o
     * rechaza en su bandeja, igual que las de AI Talent Matching.
     */
    @PostMapping("/proyectos/{id}/proponer")
    public String proponer(@PathVariable Long id, @RequestParam Long perfilId,
                           @RequestParam int cargaPorcentaje,
                           @RequestParam(required = false) String volver,
                           HttpSession session, RedirectAttributes redirectAttributes) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        try {
            exigirPropiedad(sesion.getPerfilId(), id);
            talentMatchingService.proponerManual(sesion.getPerfilId(), sesion.getUsuarioId(), id, perfilId, cargaPorcentaje);
            redirectAttributes.addFlashAttribute("exito", "Propuesta enviada al Resource Manager. Te avisaremos cuando la revise.");
        } catch (OperacionInvalidaException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return destino(volver);
    }

    /**
     * Propuesta en masa (checkboxes del modal en Asignaciones): una propuesta
     * por persona; las que no cumplen se informan y las demás se envían.
     */
    @PostMapping("/proyectos/{id}/proponer-varios")
    public String proponerVarios(@PathVariable Long id,
                                 @RequestParam(name = "perfilIds", required = false) List<Long> perfilIds,
                                 @RequestParam int cargaPorcentaje,
                                 @RequestParam(required = false) String volver,
                                 HttpSession session, RedirectAttributes redirectAttributes) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        try {
            exigirPropiedad(sesion.getPerfilId(), id);
            AsignacionMasivaService.Resultado r = talentMatchingService.proponerVarios(
                    sesion.getPerfilId(), sesion.getUsuarioId(), id, perfilIds, cargaPorcentaje);
            if (r.huboAsignados()) {
                redirectAttributes.addFlashAttribute("exito", r.asignados().size() == 1
                        ? "Propuesta enviada al Resource Manager: " + r.asignados().get(0) + "."
                        : r.asignados().size() + " propuestas enviadas al Resource Manager: " + String.join(", ", r.asignados()) + ".");
            }
            if (r.huboErrores()) {
                redirectAttributes.addFlashAttribute("error",
                        (r.huboAsignados() ? "Algunas propuestas no se enviaron. " : "") + String.join(" ", r.errores()));
            }
        } catch (OperacionInvalidaException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return destino(volver);
    }

    /**
     * "Transferir liderazgo": el PM cede su rol a un colaborador de SU
     * equipo, de forma directa (nadie suma carga). Él decide si se queda
     * como colaborador (si hay vacante) o sale del proyecto.
     */
    @PostMapping("/proyectos/{id}/transferir-liderazgo")
    public String transferirLiderazgo(@PathVariable Long id, @RequestParam Long nuevoPmPerfilId,
                                      @RequestParam(defaultValue = "false") boolean mantener,
                                      @RequestParam(required = false) Integer cargaSaliente,
                                      HttpSession session, RedirectAttributes redirectAttributes) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        try {
            exigirPropiedad(sesion.getPerfilId(), id);
            proyectoService.transferirLiderazgo(id, sesion.getPerfilId(), nuevoPmPerfilId, mantener, cargaSaliente,
                    sesion.getUsuarioId());
            redirectAttributes.addFlashAttribute("exito", mantener
                    ? "Liderazgo transferido. Sigues en el proyecto como colaborador."
                    : "Liderazgo transferido. Tu asignación en ese proyecto finalizó.");
        } catch (OperacionInvalidaException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/project-manager/proyectos.html";
        }
        // Si ya no lidera ningún proyecto, el interceptor lo llevará a su
        // vista Colaborador en el siguiente clic; redirigir a proyectos.html
        // de PM lo resuelve en este mismo paso.
        return "redirect:/project-manager/proyectos.html";
    }

    @PostMapping("/asignaciones/{asignacionId}/finalizar")
    public String finalizar(@PathVariable Long asignacionId, @RequestParam(required = false) String volver,
                            @RequestParam(required = false) String motivo,
                            HttpSession session, RedirectAttributes redirectAttributes) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        try {
            Asignacion a = asignacionRepository.findById(asignacionId)
                    .orElseThrow(() -> new OperacionInvalidaException("La asignacion ya no existe."));
            exigirPropiedad(sesion.getPerfilId(), a.getProyectoId());
            proyectoService.finalizarAsignacion(asignacionId, motivo, sesion.getUsuarioId());
            redirectAttributes.addFlashAttribute("exito", "Asignacion finalizada.");
        } catch (OperacionInvalidaException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return destino(volver);
    }

    private String destino(String volver) {
        if (volver != null && volver.matches("proyecto-\\d+")) {
            return "redirect:/project-manager/proyectos/" + volver.substring("proyecto-".length()) + ".html";
        }
        if ("matching".equals(volver)) return "redirect:/project-manager/ai-talent-matching.html";
        return "redirect:" + ("asignaciones".equals(volver)
                ? "/project-manager/asignaciones.html"
                : "/project-manager/proyectos.html");
    }
}
