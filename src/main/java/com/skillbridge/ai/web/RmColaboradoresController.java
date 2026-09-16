package com.skillbridge.ai.web;

import com.skillbridge.ai.dto.ColaboradorFila;
import com.skillbridge.ai.dto.HabilidadPerfilFila;
import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.model.Habilidad;
import com.skillbridge.ai.model.Perfil;
import com.skillbridge.ai.repository.AsignacionRepository;
import com.skillbridge.ai.repository.HabilidadRepository;
import com.skillbridge.ai.repository.PerfilRepository;
import com.skillbridge.ai.service.HabilidadService;
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
 * El detalle de habilidades de cada colaborador viaja YA CARGADO dentro de
 * cada ColaboradorFila (no hay un endpoint AJAX aparte para el modal "Ver
 * perfil"): con el volumen de colaboradores esperado en un proyecto de
 * curso, es más simple construir el modal en el navegador a partir de los
 * datos que ya llegaron con la página, igual que hace resource-manager/
 * asignaciones.html con su lista de PERFILES.
 */
@Controller
@RequestMapping("/resource-manager")
public class RmColaboradoresController {

    private final PerfilRepository perfilRepository;
    private final AsignacionRepository asignacionRepository;
    private final HabilidadRepository habilidadRepository;
    private final HabilidadService habilidadService;
    private final ShellModelBuilder shellModelBuilder;

    public RmColaboradoresController(PerfilRepository perfilRepository, AsignacionRepository asignacionRepository,
                                     HabilidadRepository habilidadRepository, HabilidadService habilidadService,
                                     ShellModelBuilder shellModelBuilder) {
        this.perfilRepository = perfilRepository;
        this.asignacionRepository = asignacionRepository;
        this.habilidadRepository = habilidadRepository;
        this.habilidadService = habilidadService;
        this.shellModelBuilder = shellModelBuilder;
    }

    @GetMapping("/colaboradores.html")
    public String colaboradores(@RequestParam(required = false) Long habilidadId,
                                @RequestParam(required = false) String estado,
                                HttpSession session, Model model) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);

        // Traemos TODOS los perfiles (no solo activos): el RM también
        // necesita ver a los inactivos para poder reactivarlos.
        List<Perfil> perfiles = perfilRepository.findAll();

        List<ColaboradorFila> filas = perfiles.stream()
                .map(p -> {
                    List<HabilidadPerfilFila> habilidades = habilidadService.habilidadesConValidacion(p.getId());

                    boolean cumpleFiltroHabilidad = habilidadId == null
                            || habilidades.stream().anyMatch(h -> h.getHabilidadId().equals(habilidadId));
                    if (!cumpleFiltroHabilidad) return null;
                    if (estado != null && !estado.isBlank() && !estado.equals(p.getEstado())) return null;

                    Integer sumaCarga = asignacionRepository.sumarCargaActivaDePerfil(p.getId());
                    int ocupacion = sumaCarga != null ? sumaCarga : 0;

                    return new ColaboradorFila(p.getId(), p.getUsuario().getNombreCompleto(), p.getUsuario().getCorreo(),
                            p.getCargo(), ocupacion, p.getEstado(), habilidades);
                })
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(ColaboradorFila::getNombre))
                .collect(Collectors.toList());

        List<Habilidad> catalogoHabilidades = habilidadRepository.findAllConCategoriaOrderByNombre();

        shellModelBuilder.aplicar(model, sesion, "colaboradores.html", "Colaboradores",
                filas.size() + " colaboradores en la plataforma");

        model.addAttribute("colaboradores", filas);
        model.addAttribute("catalogoHabilidades", catalogoHabilidades);
        model.addAttribute("habilidadIdSeleccionada", habilidadId);
        model.addAttribute("estadoSeleccionado", estado);

        return "resource-manager/colaboradores";
    }

    /** Update real: aprueba el nivel que el colaborador se autodeclaró (RF02). */
    @PostMapping("/colaboradores/{perfilId}/habilidades/{habilidadId}/validar")
    public String validarHabilidad(@PathVariable Long perfilId, @PathVariable Long habilidadId,
                                   HttpSession session, RedirectAttributes redirectAttributes) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        try {
            habilidadService.validarHabilidadDeColaborador(perfilId, habilidadId, sesion.getPerfilId());
            redirectAttributes.addFlashAttribute("exito", "Habilidad validada.");
        } catch (OperacionInvalidaException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/resource-manager/colaboradores.html";
    }

    /** Update real: activar/desactivar cuenta (soft delete, igual que el resto del sistema). */
    @PostMapping("/colaboradores/{perfilId}/estado")
    public String cambiarEstado(@PathVariable Long perfilId, @RequestParam String nuevoEstado,
                                RedirectAttributes redirectAttributes) {
        Perfil p = perfilRepository.findById(perfilId)
                .orElseThrow(() -> new OperacionInvalidaException("El colaborador ya no existe."));
        p.setEstado(nuevoEstado);
        perfilRepository.save(p);
        redirectAttributes.addFlashAttribute("exito",
                "activo".equals(nuevoEstado) ? "Colaborador reactivado." : "Colaborador suspendido.");
        return "redirect:/resource-manager/colaboradores.html";
    }
}