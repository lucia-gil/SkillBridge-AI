package com.skillbridge.ai.controller;

import com.skillbridge.ai.dto.ColaboradorFila;
import com.skillbridge.ai.dto.HabilidadPerfilFila;
import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.model.Habilidad;
import com.skillbridge.ai.model.Perfil;
import com.skillbridge.ai.repository.AsignacionRepository;
import com.skillbridge.ai.repository.HabilidadRepository;
import com.skillbridge.ai.repository.PerfilRepository;
import com.skillbridge.ai.service.AuditoriaService;
import com.skillbridge.ai.service.HabilidadService;
import com.skillbridge.ai.service.ShellModelBuilder;
import com.skillbridge.ai.service.UsuarioService;
import com.skillbridge.ai.util.OperacionInvalidaException;
import com.skillbridge.ai.util.Roles;
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

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * "Colaboradores" del Resource Manager (resource-manager/colaboradores.html).
 *
 * Complementa a AI Talent Matching en vez de duplicarlo: Matching sirve
 * para "tengo una necesidad puntual, ¿quién encaja?" (búsqueda semántica,
 * resultado rankeado); esta pantalla sirve para "quiero explorar/gestionar
 * a mi gente en general" (directorio completo, filtrable por habilidad
 * exacta, con acciones de gestión que no tienen sentido en un resultado de
 * matching: suspender, validar habilidades).
 *
 * El filtro "Célula" del mockup original se quitó (no hay ninguna tabla
 * equivalente en el esquema v4). Se reemplazó por un filtro de Habilidad
 * (sí existe: perfil_habilidad), que no compite con Matching porque busca
 * distinto: aquí es manual y exacto ("tiene React sí o no"), allá es
 * semántico ("necesito experiencia en salud digital").
 *
 * El detalle de habilidades de cada colaborador se muestra en una página
 * dedicada (/resource-manager/colaboradores/{perfilId}, tipo CV/carpeta
 * personal) en vez del modal "Ver perfil" anterior: ahí es donde viven las
 * constancias de respaldo (certificados_habilidad, un link por habilidad)
 * y el botón "Validar" de cada una. Es UNA sola plantilla reusada para
 * cualquier perfil, no una página por persona.
 */
@Controller
@RequestMapping("/resource-manager")
public class RmColaboradoresController {

    private final PerfilRepository perfilRepository;
    private final AsignacionRepository asignacionRepository;
    private final HabilidadRepository habilidadRepository;
    private final HabilidadService habilidadService;
    private final ShellModelBuilder shellModelBuilder;
    private final AuditoriaService auditoriaService;
    private final UsuarioService usuarioService;
    private final com.skillbridge.ai.service.ProyectoService proyectoService;
    private final com.skillbridge.ai.service.PerfilVistaService perfilVistaService;
    private final com.skillbridge.ai.service.ConfiguracionService configuracionService;
    private final com.skillbridge.ai.service.ResumenProfesionalIaService resumenProfesionalIaService;

    public RmColaboradoresController(PerfilRepository perfilRepository, AsignacionRepository asignacionRepository,
                                     HabilidadRepository habilidadRepository, HabilidadService habilidadService,
                                     ShellModelBuilder shellModelBuilder, AuditoriaService auditoriaService,
                                     UsuarioService usuarioService,
                                     com.skillbridge.ai.service.ProyectoService proyectoService,
                                     com.skillbridge.ai.service.PerfilVistaService perfilVistaService,
                                     com.skillbridge.ai.service.ConfiguracionService configuracionService,
                                     com.skillbridge.ai.service.ResumenProfesionalIaService resumenProfesionalIaService) {
        this.auditoriaService = auditoriaService;
        this.usuarioService = usuarioService;
        this.perfilRepository = perfilRepository;
        this.asignacionRepository = asignacionRepository;
        this.habilidadRepository = habilidadRepository;
        this.habilidadService = habilidadService;
        this.shellModelBuilder = shellModelBuilder;
        this.proyectoService = proyectoService;
        this.perfilVistaService = perfilVistaService;
        this.configuracionService = configuracionService;
        this.resumenProfesionalIaService = resumenProfesionalIaService;
    }

    @GetMapping("/colaboradores.html")
    public String colaboradores(@RequestParam(required = false) Long habilidadId,
                                @RequestParam(required = false) String estado,
                                HttpSession session, Model model) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);

        // Traemos TODOS los perfiles (no solo activos): el RM también
        // necesita ver a los inactivos para poder reactivarlos.
        // listarTodosConUsuario() carga el Usuario en la misma consulta
        // (JOIN FETCH) - con findAll() a secas, p.getUsuario() explota con
        // LazyInitializationException fuera de la transacción.
        // Modelo v7: el directorio del RM muestra solo MIEMBROS (las personas
        // que se pueden asignar a proyectos). Administradores y Resource
        // Managers no participan en proyectos, así que no aparecen aquí.
        List<Perfil> perfiles = perfilRepository.listarTodosConUsuario().stream()
                .filter(p -> p.getUsuario() != null && p.getUsuario().getRolOrganizacional() == null)
                .collect(Collectors.toList());

        int limite = configuracionService.limiteCarga();
        // Conteos por estado de todo el directorio (sin filtros): alimentan
        // los chips de estado de la barra de filtros.
        java.util.Map<String, Long> porEstado = perfiles.stream()
                .collect(Collectors.groupingBy(
                        p -> !p.getUsuario().isActivo() ? "cuenta_suspendida" : java.util.Objects.toString(p.getEstado(), "activo"),
                        Collectors.counting()));

        List<ColaboradorFila> filas = perfiles.stream()
                .map(p -> {
                    List<HabilidadPerfilFila> habilidades = habilidadService.habilidadesConValidacion(p.getId());

                    boolean cumpleFiltroHabilidad = habilidadId == null
                            || habilidades.stream().anyMatch(h -> h.getHabilidadId().equals(habilidadId));
                    if (!cumpleFiltroHabilidad) return null;
                    // "cuenta_suspendida" = el Admin suspendió la cuenta (solo él la reactiva);
                    // "inactivo" = el RM marcó el perfil como no disponible para proyectos.
                    String estadoVista = !p.getUsuario().isActivo() ? "cuenta_suspendida" : p.getEstado();
                    if (estado != null && !estado.isBlank() && !estado.equals(estadoVista)) return null;

                    Integer sumaCarga = asignacionRepository.sumarCargaActivaDePerfil(p.getId());
                    int ocupacion = sumaCarga != null ? sumaCarga : 0;

                    return new ColaboradorFila(p.getId(), p.getUsuario().getNombreCompleto(), p.getUsuario().getCorreo(),
                            p.getCargo(), ocupacion, estadoVista, habilidades,
                            p.getUsuario().tieneFotoPerfil(), p.getUsuario().getId()).conLimite(limite)
                            .conParticipacion(
                                    "activo".equals(estadoVista) ? usuarioService.proyectosQueLidera(p.getId()) : List.of(),
                                    "activo".equals(estadoVista) ? usuarioService.proyectosDondeColabora(p.getId()) : List.of());
                })
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(ColaboradorFila::getNombre))
                .collect(Collectors.toList());

        List<Habilidad> catalogoHabilidades = habilidadRepository.findAllConCategoriaOrderByNombre();

        shellModelBuilder.aplicar(model, sesion, "colaboradores.html", "Colaboradores",
                perfiles.size() + " miembros en el directorio del equipo");

        model.addAttribute("colaboradores", filas);
        model.addAttribute("catalogoHabilidades", catalogoHabilidades);
        model.addAttribute("habilidadIdSeleccionada", habilidadId);
        model.addAttribute("estadoSeleccionado", estado);
        model.addAttribute("limiteCarga", limite);
        model.addAttribute("totalMiembros", perfiles.size());
        model.addAttribute("totalDisponibles", porEstado.getOrDefault("activo", 0L));
        model.addAttribute("totalNoDisponibles", porEstado.getOrDefault("inactivo", 0L));
        model.addAttribute("totalSuspendidos", porEstado.getOrDefault("cuenta_suspendida", 0L));
        // Se usa en la plantilla para esconder el botón "Suspender" en la
        // propia fila del usuario logueado - nadie debería poder
        // auto-suspenderse (se quedaría sin acceso, sin nadie más que
        // pueda reactivarlo fácilmente).
        model.addAttribute("miPerfilId", sesion.getPerfilId());

        return "resource-manager/colaboradores";
    }

    /**
     * Vista completa del colaborador (tipo CV), reemplaza al modal "Ver
     * perfil" anterior. Es UNA sola plantilla reusada para cualquier
     * colaborador - el {perfilId} en la URL es lo único que cambia; el
     * Controller busca ESE perfil puntual y la plantilla se llena con sus
     * datos, sin necesitar una página por persona.
     */
    @GetMapping("/colaboradores/{perfilId}")
    public String detalle(@PathVariable Long perfilId, HttpSession session, Model model) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);

        Perfil p = perfilRepository.buscarConUsuario(perfilId)
                .orElseThrow(() -> new OperacionInvalidaException("El colaborador ya no existe."));
        // Modelo v7: Admin y RM no forman parte del directorio de colaboradores.
        if (p.getUsuario() != null && p.getUsuario().getRolOrganizacional() != null) {
            return "redirect:/resource-manager/colaboradores.html";
        }
        shellModelBuilder.aplicar(model, sesion, "colaboradores.html", p.getUsuario().getNombreCompleto(),
                "Perfil profesional");

        model.addAttribute("perfil", p);
        model.addAttribute("vista", perfilVistaService.armar(p));
        model.addAttribute("resumenIa", resumenProfesionalIaService.obtener(perfilId).orElse(null));
        model.addAttribute("resumenIaActualizando", resumenProfesionalIaService.estaActualizando(perfilId));
        model.addAttribute("analisisIa", resumenProfesionalIaService.analisisGuardados(perfilId));

        return "resource-manager/colaborador-detalle";
    }

    /**
     * Resumen previo con IA de la constancia de una habilidad (lo pide la
     * ventana "Validar" de la ficha). Si ya se analizó y la constancia no
     * cambió, devuelve el guardado sin volver a llamar a Claude.
     */
    @PostMapping("/colaboradores/{perfilId}/habilidades/{habilidadId}/analisis-ia")
    @org.springframework.web.bind.annotation.ResponseBody
    public org.springframework.http.ResponseEntity<java.util.Map<String, Object>> analisisIa(@PathVariable Long perfilId,
                                                                                           @PathVariable Long habilidadId) {
        try {
            return org.springframework.http.ResponseEntity.ok(java.util.Map.of("ok", true,
                    "analisis", resumenProfesionalIaService.analizarConstancia(perfilId, habilidadId)));
        } catch (OperacionInvalidaException ex) {
            return org.springframework.http.ResponseEntity.ok(java.util.Map.of("ok", false, "error", ex.getMessage()));
        }
    }

    /** Update real: valida la habilidad (RF02); el RM puede dejar el nivel declarado o ajustarlo. */
    @PostMapping("/colaboradores/{perfilId}/habilidades/{habilidadId}/validar")
    public String validarHabilidad(@PathVariable Long perfilId, @PathVariable Long habilidadId,
                                   @RequestParam(required = false) String nivel,
                                   HttpSession session, RedirectAttributes redirectAttributes) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        try {
            boolean cambioNivel = habilidadService.validarHabilidadDeColaborador(perfilId, habilidadId, sesion.getPerfilId(), nivel);
            // El Resumen profesional del colaborador se arma solo con lo validado: se actualiza en segundo plano.
            resumenProfesionalIaService.actualizarEnSegundoPlano(perfilId);
            redirectAttributes.addFlashAttribute("exito", cambioNivel
                    ? "Habilidad validada con nivel " + nivel.trim() + ". Su resumen profesional se está actualizando con IA."
                    : "Habilidad validada. Su resumen profesional se está actualizando con IA.");
        } catch (OperacionInvalidaException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        // Vuelve a la pagina de DETALLE (no a la lista): la accion de
        // validar ahora vive en la vista tipo CV, no en el modal viejo.
        return "redirect:/resource-manager/colaboradores/" + perfilId;
    }

    /** Update real: activar/desactivar cuenta (soft delete, igual que el resto del sistema). */
    @PostMapping("/colaboradores/{perfilId}/estado")
    public String cambiarEstado(@PathVariable Long perfilId, @RequestParam String nuevoEstado,
                                @RequestParam(required = false) String motivo,
                                HttpSession session, RedirectAttributes redirectAttributes) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);

        // Bloqueo de auto-suspensión: defensa en el backend, no solo en la
        // plantilla (que esconde el botón) - así alguien no puede saltarse
        // esto armando la petición a mano contra la URL directa.
        if (perfilId.equals(sesion.getPerfilId()) && "inactivo".equals(nuevoEstado)) {
            redirectAttributes.addFlashAttribute("error", "No puedes cambiar la disponibilidad de tu propio perfil.");
            return "redirect:/resource-manager/colaboradores.html";
        }

        if ("inactivo".equals(nuevoEstado) && AuditoriaService.limpiarMotivo(motivo) == null) {
            redirectAttributes.addFlashAttribute("error", "Indica el motivo: queda registrado en Auditoría.");
            return "redirect:/resource-manager/colaboradores.html";
        }
        // buscarConUsuario (join fetch): abajo se lee p.getUsuario() (rol,
        // nombre, correo) y con open-in-view=false la relacion LAZY ya no se
        // puede cargar fuera de la transaccion -> LazyInitializationException
        // (500 al pulsar "No disponible" / "Disponible").
        Perfil p = perfilRepository.buscarConUsuario(perfilId).orElse(null);
        if (p == null) {
            redirectAttributes.addFlashAttribute("error", "El colaborador ya no existe.");
            return "redirect:/resource-manager/colaboradores.html";
        }

        // El RM no tiene autoridad sobre cuentas Admin: Admin y RM "existen
        // por encima" de los proyectos (ver comentario de la tabla
        // asignaciones en el SQL). Igual que la auto-suspension, se valida
        // aqui en el backend y no solo escondiendo el boton en la plantilla.
        // Modelo v7: el RM solo gestiona la disponibilidad de MIEMBROS. Las
        // cuentas de Administradores y Resource Managers las gestiona el
        // Administrador en "Usuarios y roles".
        if (p.getUsuario() != null && p.getUsuario().getRolOrganizacional() != null) {
            redirectAttributes.addFlashAttribute("error",
                    "Ese perfil es de un " + Roles.etiqueta(p.getUsuario().getRolOrganizacional())
                            + ": no forma parte del directorio de colaboradores.");
            return "redirect:/resource-manager/colaboradores.html";
        }

        // Modelo v7 (fase 3): un perfil suspendido no puede seguir en
        // proyectos. Misma regla que al suspender la cuenta: si lidera,
        // bloqueado (primero "Cambiar PM"); si colabora, se finalizan sus
        // asignaciones (el modal de esta pantalla ya lo advierte).
        if ("inactivo".equals(nuevoEstado)) {
            try {
                usuarioService.salirDeProyectos(perfilId,
                        p.getUsuario() != null ? p.getUsuario().getNombreCompleto() : "El colaborador",
                        "marcarlo como no disponible", true, motivo, sesion.getUsuarioId());
            } catch (OperacionInvalidaException ex) {
                redirectAttributes.addFlashAttribute("error", ex.getMessage());
                return "redirect:/resource-manager/colaboradores.html";
            }
        }
        String anterior = p.getEstado();
        p.setEstado(nuevoEstado);
        perfilRepository.save(p);
        // Antes esta acción no dejaba rastro en Auditoría.
        auditoriaService.registrar(sesion.getUsuarioId(),
                "activo".equals(nuevoEstado) ? "COLABORADOR_REACTIVADO" : "COLABORADOR_SUSPENDIDO",
                "perfil", perfilId, AuditoriaService.json("estado", anterior), AuditoriaService.json("estado", nuevoEstado),
                AuditoriaService.conMotivo(p.getUsuario() != null ? p.getUsuario().getCorreo() : "Perfil " + perfilId, motivo));
        redirectAttributes.addFlashAttribute("exito",
                "activo".equals(nuevoEstado) ? "Perfil marcado como disponible." : "Perfil marcado como no disponible para proyectos.");
        return "redirect:/resource-manager/colaboradores.html";
    }
}