package com.skillbridge.ai.controller;

import com.skillbridge.ai.dto.AsignacionCargaFila;
import com.skillbridge.ai.dto.ColaboradorFila;
import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.model.Asignacion;
import com.skillbridge.ai.repository.AsignacionRepository;
import com.skillbridge.ai.service.ProyectoService;
import com.skillbridge.ai.util.OperacionInvalidaException;
import com.skillbridge.ai.util.RecursoNoEncontradoException;
import com.skillbridge.ai.util.SesionKeys;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

/**
 * Carga del equipo del Resource Manager. La antigua pantalla "Ocupación del
 * equipo" se unió a Colaboradores (RmColaboradoresController): allí están
 * las tarjetas de capacidad y el modal "Ajustar carga" (RG-214).
 *
 * El modal usa el servicio REST de esta clase (jQuery/AJAX con SBAI.api),
 * así guardar no recarga la página: se actualiza solo la fila de la persona.
 *   PUT  /resource-manager/api/asignaciones/{id}/dedicacion  {cargaPorcentaje, motivo}
 *   POST /resource-manager/api/asignaciones/{id}/finalizar   {motivo}
 * Ambos responden la carga actualizada de la persona (ver CargaPersona).
 * Al estar bajo /resource-manager/**, solo los usa el Resource Manager.
 */
@Controller
@RequestMapping("/resource-manager")
public class RmOcupacionController {

    private final ProyectoService proyectoService;
    private final AsignacionRepository asignacionRepository;

    public RmOcupacionController(ProyectoService proyectoService, AsignacionRepository asignacionRepository) {
        this.proyectoService = proyectoService;
        this.asignacionRepository = asignacionRepository;
    }

    /**
     * "Ocupación del equipo" se unió a Colaboradores (capacidad, carga y
     * perfiles en una sola pantalla). Se conserva la dirección para que los
     * enlaces antiguos sigan funcionando.
     */
    @GetMapping("/ocupacion.html")
    public String ocupacion() {
        return "redirect:/resource-manager/colaboradores.html";
    }

    /** Formulario clásico (sin JavaScript): cambiar la dedicación y volver a Colaboradores. */
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
        return "redirect:/resource-manager/colaboradores.html";
    }

    // ───────────────────────── REST (JSON) ─────────────────────────

    public record DedicacionRequest(Integer cargaPorcentaje, String motivo) {}
    public record MotivoRequest(String motivo) {}

    /** Carga de una persona después del cambio: con esto el navegador repinta su fila y el modal. */
    public record CargaPersona(Long perfilId, int ocupacion, int limite, int cargaPct, String cargaClave,
                               String cargaLabel, String mensaje, List<AsignacionCargaFila> asignaciones) {}

    @PutMapping(value = "/api/asignaciones/{asignacionId}/dedicacion", produces = "application/json;charset=UTF-8")
    @ResponseBody
    public ResponseEntity<CargaPersona> ajustarDedicacionJson(@PathVariable Long asignacionId,
                                                              @RequestBody DedicacionRequest body, HttpSession session) {
        if (body == null || body.cargaPorcentaje() == null) throw new OperacionInvalidaException("Indica la nueva dedicación.");
        Long perfilId = perfilDe(asignacionId);
        proyectoService.ajustarDedicacion(asignacionId, body.cargaPorcentaje(), body.motivo(), usuarioId(session));
        return ResponseEntity.ok(cargaDe(perfilId, "Dedicación actualizada a " + body.cargaPorcentaje() + "%."));
    }

    @PostMapping(value = "/api/asignaciones/{asignacionId}/finalizar", produces = "application/json;charset=UTF-8")
    @ResponseBody
    public ResponseEntity<CargaPersona> finalizarJson(@PathVariable Long asignacionId,
                                                      @RequestBody(required = false) MotivoRequest body, HttpSession session) {
        Long perfilId = perfilDe(asignacionId);
        proyectoService.finalizarAsignacion(asignacionId, body == null ? null : body.motivo(), usuarioId(session));
        return ResponseEntity.ok(cargaDe(perfilId, "Asignación finalizada."));
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    @ResponseBody
    public ResponseEntity<Map<String, String>> noEncontrado(RecursoNoEncontradoException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(OperacionInvalidaException.class)
    @ResponseBody
    public ResponseEntity<Map<String, String>> invalida(OperacionInvalidaException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }

    private Long perfilDe(Long asignacionId) {
        return asignacionRepository.findById(asignacionId).map(Asignacion::getPerfilId)
                .orElseThrow(() -> new RecursoNoEncontradoException("La asignación ya no existe."));
    }

    private CargaPersona cargaDe(Long perfilId, String mensaje) {
        Integer suma = asignacionRepository.sumarCargaActivaDePerfil(perfilId);
        int ocupacion = suma != null ? suma : 0;
        int limite = proyectoService.limiteCargaConfigurado();
        String clave = ColaboradorFila.claveDeCarga(ocupacion, limite);
        int pct = limite <= 0 ? 0 : (int) Math.min(100, Math.round(ocupacion * 100.0 / limite));
        return new CargaPersona(perfilId, ocupacion, limite, pct, clave, ColaboradorFila.etiquetaDeCarga(clave), mensaje,
                proyectoService.asignacionesActivasDe(perfilId));
    }

    private static Long usuarioId(HttpSession session) {
        return ((UsuarioSesion) session.getAttribute(SesionKeys.USUARIO)).getUsuarioId();
    }
}
