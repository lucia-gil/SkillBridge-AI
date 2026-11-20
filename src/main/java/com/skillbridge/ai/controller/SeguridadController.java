package com.skillbridge.ai.controller;

import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.security.PortalFilter;
import com.skillbridge.ai.service.*;
import com.skillbridge.ai.util.SesionKeys;
import jakarta.servlet.http.HttpSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.UUID;

@Controller
@RequestMapping("/administrador")
public class SeguridadController {
    private final EmergenciaService emergencia;
    private final IncidenteService incidentes;
    private final JdbcTemplate jdbc;

    public SeguridadController(EmergenciaService emergencia, IncidenteService incidentes, JdbcTemplate jdbc) {
        this.emergencia = emergencia;
        this.incidentes = incidentes;
        this.jdbc = jdbc;
    }

    @GetMapping("/seguridad.html")
    public String consola(@RequestParam(defaultValue="") String estado, @RequestParam(defaultValue="") String regla,
                          @RequestParam(required=false) Long id, @RequestParam(defaultValue="9223372036854775807") long antes,
                          HttpSession session, Model model) {
        var portal = emergencia.estado();
        if (portal.activo()) id = portal.incidente();
        model.addAttribute("portal", portal);
        model.addAttribute("reglas", IncidenteService.Regla.values());
        model.addAttribute("estado", estado);
        model.addAttribute("regla", regla);
        model.addAttribute("lista", portal.activo() ? java.util.List.of() : incidentes.listar(estado, regla, antes));
        if (id != null) {
            model.addAttribute("detalle", jdbc.queryForList("SELECT * FROM seguridad_incidente WHERE id=?", id));
            model.addAttribute("avisos", jdbc.queryForList("SELECT perfil,estado FROM seguridad_aviso WHERE incidente=?", id));
            model.addAttribute("acciones", jdbc.queryForList("SELECT * FROM seguridad_historial WHERE incidente=? ORDER BY id DESC", id));
            String token = UUID.randomUUID().toString();
            String operacion = portal.activo() ? "restablecer" : "activar";
            session.setAttribute("seguridad.vence", System.currentTimeMillis() + 300_000L);
            session.setAttribute("seguridad.desafio", token + "/" + id + "/" + operacion + "/" + portal.version());
            model.addAttribute("token", token);
            model.addAttribute("operacion", operacion);
        }
        return "administrador/seguridad";
    }

    @PostMapping("/seguridad/manual")
    public String manual(HttpSession session) {
        return "redirect:" + rutaIncidente(incidentes.abrirManual(actor(session)));
    }

    @PostMapping("/seguridad/confirmar")
    public String confirmar(@RequestParam long id, HttpSession session) {
        incidentes.confirmar(id, actor(session));
        return "redirect:" + rutaIncidente(id);
    }

    @PostMapping("/seguridad/{operacion:activar|restablecer}")
    public String cambiar(@PathVariable String operacion, @RequestParam long id, @RequestParam long version,
                          @RequestParam String token, @RequestParam String motivo, @RequestParam String password,
                          @RequestParam String codigo, HttpSession session, RedirectAttributes flash) {
        Object reto = session.getAttribute("seguridad.desafio");
        session.removeAttribute("seguridad.desafio");
        Object vence = session.getAttribute("seguridad.vence");
        session.removeAttribute("seguridad.vence");
        boolean desafioValido = vence instanceof Long fin && fin >= System.currentTimeMillis()
                && (token + "/" + id + "/" + operacion + "/" + version).equals(reto);
        if (!desafioValido) {
            incidentes.historial(null, actor(session), "Desafío de sesión rechazado", operacion);
        } else if (emergencia.cambiar(actor(session), operacion.equals("activar"), version, id, motivo, password, codigo)) {
            session.invalidate();
            return "redirect:/auth/login.html";
        }
        flash.addFlashAttribute("error", "No se autorizó el cambio. Revisa tus credenciales y usa un código nuevo.");
        return "redirect:" + rutaIncidente(id);
    }

    private static String rutaIncidente(long id) { return PortalFilter.CONSOLA + "?id=" + id; }
    private static Long actor(HttpSession s) { return ((UsuarioSesion) s.getAttribute(SesionKeys.USUARIO)).getUsuarioId(); }
}
