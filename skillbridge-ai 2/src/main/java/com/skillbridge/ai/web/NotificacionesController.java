package com.skillbridge.ai.web;

import com.skillbridge.ai.dto.NotificacionFila;
import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.service.NotificacionService;
import com.skillbridge.ai.service.PreferenciaNotificacionService;
import com.skillbridge.ai.service.ShellModelBuilder;
import com.skillbridge.ai.util.Roles;
import com.skillbridge.ai.util.SesionKeys;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Centro de notificaciones (administrador/notificaciones.html y
 * colaborador/notificaciones.html): un único controlador, real sobre
 * "notificaciones"/"preferencias_notificacion". Las pestañas de tipo se
 * generan a partir de los tipos que realmente aparecen en las
 * notificaciones del perfil (esta entrega solo genera "asignacion" y
 * "foro_respuesta" como efecto de acciones reales, ver DataSeeder), en vez
 * de una lista fija de categorías que el mockup inventaba por rol.
 */
@Controller
public class NotificacionesController {

    private final NotificacionService notificacionService;
    private final PreferenciaNotificacionService preferenciaNotificacionService;
    private final ShellModelBuilder shellModelBuilder;

    public NotificacionesController(NotificacionService notificacionService,
                                     PreferenciaNotificacionService preferenciaNotificacionService,
                                     ShellModelBuilder shellModelBuilder) {
        this.notificacionService = notificacionService;
        this.preferenciaNotificacionService = preferenciaNotificacionService;
        this.shellModelBuilder = shellModelBuilder;
    }

    @GetMapping({"/administrador/notificaciones.html", "/colaborador/notificaciones.html"})
    public String notificaciones(HttpSession session, Model model) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        String volver = "/" + Roles.slug(sesion.getRolEfectivo()) + "/notificaciones.html";

        List<NotificacionFila> lista = notificacionService.listar(sesion.getPerfilId());
        long noLeidas = lista.stream().filter(n -> !n.isLeida()).count();

        Set<String> tiposPresentes = new LinkedHashSet<>();
        Map<String, String> etiquetaPorTipo = new LinkedHashMap<>();
        for (NotificacionFila n : lista) {
            if (tiposPresentes.add(n.getTipoCodigo())) {
                etiquetaPorTipo.put(n.getTipoCodigo(), n.getTipoLabel());
            }
        }

        Map<String, Boolean> preferencias = preferenciaNotificacionService.obtener(sesion.getPerfilId());

        shellModelBuilder.aplicar(model, sesion, "notificaciones.html", "Centro de notificaciones",
                noLeidas + " sin leer · " + lista.size() + " en total");

        model.addAttribute("notificaciones", lista);
        model.addAttribute("noLeidas", noLeidas);
        model.addAttribute("tiposPresentes", etiquetaPorTipo);
        model.addAttribute("preferencias", preferencias);
        model.addAttribute("tiposEvento", PreferenciaNotificacionService.TIPOS_EVENTO);
        model.addAttribute("volver", volver);
        return Roles.slug(sesion.getRolEfectivo()) + "/notificaciones";
    }

    @PostMapping("/notificaciones/{id}/marcar-leida")
    public String marcarLeida(@PathVariable Long id, @RequestParam String volver, HttpSession session) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        notificacionService.marcarLeida(id, sesion.getPerfilId());
        return "redirect:" + rutaSegura(volver);
    }

    @PostMapping("/notificaciones/marcar-todas")
    public String marcarTodas(@RequestParam String volver, HttpSession session, RedirectAttributes redirectAttributes) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        notificacionService.marcarTodasLeidas(sesion.getPerfilId());
        redirectAttributes.addFlashAttribute("exito", "Todas las notificaciones se marcaron como leídas.");
        return "redirect:" + rutaSegura(volver);
    }

    @PostMapping("/notificaciones/preferencias")
    public String actualizarPreferencias(@RequestParam Map<String, String> params, @RequestParam String volver, HttpSession session,
                                          RedirectAttributes redirectAttributes) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        Map<String, Boolean> seleccion = new LinkedHashMap<>();
        for (String tipo : PreferenciaNotificacionService.TIPOS_EVENTO) {
            seleccion.put(tipo, params.containsKey(tipo));
        }
        preferenciaNotificacionService.actualizar(sesion.getPerfilId(), seleccion);
        redirectAttributes.addFlashAttribute("exito", "Preferencias de aviso actualizadas.");
        return "redirect:" + rutaSegura(volver);
    }

    private String rutaSegura(String volver) {
        if ("/administrador/notificaciones.html".equals(volver) || "/colaborador/notificaciones.html".equals(volver)) {
            return volver;
        }
        return "/auth/login.html";
    }
}
