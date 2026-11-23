package com.skillbridge.ai.controller;

import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.model.Usuario;
import com.skillbridge.ai.repository.UsuarioRepository;
import com.skillbridge.ai.service.AuditoriaService;
import com.skillbridge.ai.service.ConsultaPeruDniService;
import com.skillbridge.ai.service.IdentidadDniService;
import com.skillbridge.ai.service.UsuarioService;
import com.skillbridge.ai.util.OperacionInvalidaException;
import com.skillbridge.ai.util.Roles;
import com.skillbridge.ai.util.SesionKeys;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Controller
public class IdentidadDniController {

    private static final String USUARIOS = "redirect:/administrador/usuarios.html";
    private static final String LIMITE = "Demasiadas consultas. Vuelve a intentarlo en unos minutos.";
    private static final long VENTANA = Duration.ofMinutes(10).toMillis();
    private static final int MAX_CONSULTAS = 30;

    private final IdentidadDniService identidad;
    private final UsuarioService usuarios;
    private final UsuarioRepository usuarioRepository;
    private final AuditoriaService auditoria;
    private final Map<Long, Ventana> consultas = new ConcurrentHashMap<>();

    private record Ventana(long inicio, int intentos) {}

    public IdentidadDniController(IdentidadDniService identidad, UsuarioService usuarios,
                                  UsuarioRepository usuarioRepository, AuditoriaService auditoria) {
        this.identidad = identidad;
        this.usuarios = usuarios;
        this.usuarioRepository = usuarioRepository;
        this.auditoria = auditoria;
    }

    @PostMapping("/administrador/usuarios/dni/consultar")
    @ResponseBody
    public ResponseEntity<?> consultar(@RequestParam String dni, HttpSession session) {
        UsuarioSesion admin = actor(session);
        if (!permitir(admin.getUsuarioId())) return errorConsulta(429, LIMITE);
        if (!identidad.disponible()) return errorConsulta(503, IdentidadDniService.ERROR_DISPONIBILIDAD);
        try {
            var resultado = identidad.consultar(dni);
            auditoria.registrar(admin.getUsuarioId(), "IDENTIDAD_DNI_CONSULTADO", "consulta_dni", null,
                    null, null, "Consulta administrativa individual al servicio de DNI.");
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(resultado);
        } catch (ConsultaPeruDniService.ConsultaNoDisponible ex) {
            return errorConsulta(ex.limite() ? 429 : 503, ex.getMessage());
        } catch (OperacionInvalidaException ex) {
            return errorConsulta(422, ex.getMessage());
        }
    }

    @PostMapping("/administrador/usuarios/{id}/identidad")
    public String vincularCuenta(@PathVariable Long id, @RequestParam String dni, HttpSession session,
                                 RedirectAttributes flash) {
        UsuarioSesion admin = actor(session);
        try {
            exigirCupo(admin.getUsuarioId());
            identidad.vincularCuenta(buscarUsuario(id), dni, admin.getUsuarioId());
            flash.addFlashAttribute("exito", "DNI vinculado y comprobado con Consulta Perú.");
        } catch (OperacionInvalidaException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return USUARIOS + "#usuarios-registrados";
    }

    @PostMapping("/administrador/usuarios/correos-autorizados/{id}/identidad")
    public String vincularInvitacion(@PathVariable Long id, @RequestParam String dni, HttpSession session,
                                      RedirectAttributes flash) {
        UsuarioSesion admin = actor(session);
        try {
            exigirCupo(admin.getUsuarioId());
            usuarios.vincularDniInvitacion(id, dni, admin.getUsuarioId());
            flash.addFlashAttribute("exito", "DNI asociado a la invitación pendiente.");
        } catch (OperacionInvalidaException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return USUARIOS + "#invitaciones";
    }

    @PostMapping("/cuenta/identidad/revalidar")
    public String revalidar(@RequestParam String dni, HttpSession session, RedirectAttributes flash) {
        UsuarioSesion sesion = actor(session);
        try {
            exigirCupo(sesion.getUsuarioId());
            identidad.revalidar(buscarUsuario(sesion.getUsuarioId()), dni);
            flash.addFlashAttribute("dniExito", "Se actualizó la comprobación de tu identidad.");
        } catch (OperacionInvalidaException ex) {
            flash.addFlashAttribute("dniError", ex.getMessage());
        }
        return "redirect:/" + Roles.slug(sesion.getRolEfectivo()) + "/mi-cuenta.html#identidad-dni";
    }

    private ResponseEntity<Map<String, String>> errorConsulta(int codigo, String mensaje) {
        return ResponseEntity.status(codigo).cacheControl(CacheControl.noStore())
                .body(Map.of("mensaje", mensaje));
    }

    private UsuarioSesion actor(HttpSession session) {
        return (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
    }

    private Usuario buscarUsuario(Long id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new OperacionInvalidaException("Esa cuenta ya no existe."));
    }

    private void exigirCupo(Long id) {
        if (!permitir(id)) throw new OperacionInvalidaException(LIMITE);
    }

    private boolean permitir(Long id) {
        long ahora = System.currentTimeMillis();
        Ventana uso = consultas.compute(id, (usuario, previa) -> previa == null || ahora - previa.inicio() >= VENTANA
                ? new Ventana(ahora, 1) : new Ventana(previa.inicio(), Math.min(MAX_CONSULTAS + 1, previa.intentos() + 1)));
        if (consultas.size() > 1000) consultas.entrySet().removeIf(e -> ahora - e.getValue().inicio() >= VENTANA);
        return uso.intentos() <= MAX_CONSULTAS;
    }
}
