package com.skillbridge.ai.controller;

import com.skillbridge.ai.dto.NotificacionFila;
import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.service.NotificacionService;
import com.skillbridge.ai.service.AuditoriaService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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

/** Bandeja personal y preferencias de notificaciones para los cuatro roles. */
@Controller
public class NotificacionesController {

    private final NotificacionService notificacionService;
    private final PreferenciaNotificacionService preferenciaNotificacionService;
    private final com.skillbridge.ai.service.ResumenSemanalService resumenSemanalService;
    private final ShellModelBuilder shellModelBuilder;
    private final AuditoriaService auditoriaService;

    public NotificacionesController(NotificacionService notificacionService,
                                    PreferenciaNotificacionService preferenciaNotificacionService,
                                    com.skillbridge.ai.service.ResumenSemanalService resumenSemanalService,
                                    ShellModelBuilder shellModelBuilder, AuditoriaService auditoriaService) {
        this.notificacionService = notificacionService;
        this.preferenciaNotificacionService = preferenciaNotificacionService;
        this.resumenSemanalService = resumenSemanalService;
        this.shellModelBuilder = shellModelBuilder;
        this.auditoriaService = auditoriaService;
    }

    @GetMapping({
            "/administrador/notificaciones.html",
            "/colaborador/notificaciones.html",
            "/resource-manager/notificaciones.html",
            "/project-manager/notificaciones.html"
    })
    public String notificaciones(HttpSession session, Model model) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        String volver = "/" + Roles.slug(sesion.getRolEfectivo()) + "/notificaciones.html";

        List<NotificacionFila> lista = notificacionService.listar(sesion);
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
        model.addAttribute("perfilDisponible", sesion.getPerfilId() != null);
        model.addAttribute("tiposPresentes", etiquetaPorTipo);
        model.addAttribute("preferencias", preferencias);
        model.addAttribute("tiposEvento", PreferenciaNotificacionService.TIPOS_EVENTO);
        model.addAttribute("volver", volver);
        if (Roles.ADMINISTRADOR.equals(sesion.getRolOrganizacional())
                && Roles.ADMINISTRADOR.equals(sesion.getRolEfectivo())) {
            model.addAttribute("actividadReciente", auditoriaService.buscar(null, null, "", "",
                    PageRequest.of(0, 4, Sort.by(Sort.Direction.DESC, "fecha", "id"))).getContent());
        }
        return Roles.slug(sesion.getRolEfectivo()) + "/notificaciones";
    }

    @PostMapping("/notificaciones/{id}/marcar-leida")
    public String marcarLeida(@PathVariable Long id, @RequestParam String volver, HttpSession session,
                              RedirectAttributes redirectAttributes) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        boolean actualizada = notificacionService.marcarLeida(id, sesion);
        String destino = rutaSegura(volver);
        if (actualizada && Roles.ADMINISTRADOR.equals(sesion.getRolOrganizacional())
                && "/administrador/notificaciones.html".equals(destino)) {
            redirectAttributes.addFlashAttribute("exito", "Aviso marcado como leído.");
            return "redirect:" + destino + "#aviso-" + id;
        }
        return "redirect:" + destino;
    }

    @PostMapping("/notificaciones/marcar-todas")
    public String marcarTodas(@RequestParam String volver, HttpSession session, RedirectAttributes redirectAttributes) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        int total = notificacionService.marcarTodasLeidas(sesion);
        redirectAttributes.addFlashAttribute("exito", total == 0 ? "Ya estás al día."
                : total == 1 ? "Se marcó 1 notificación como leída."
                : "Se marcaron " + total + " notificaciones como leídas.");
        return "redirect:" + rutaSegura(volver);
    }

    @PostMapping("/notificaciones/preferencias")
    public String actualizarPreferencias(@RequestParam Map<String, String> params, @RequestParam String volver, HttpSession session,
                                         RedirectAttributes redirectAttributes) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        if (sesion.getPerfilId() == null) {
            redirectAttributes.addFlashAttribute("error", "Vuelve a iniciar sesión para actualizar tu perfil.");
            return "redirect:" + rutaSegura(volver);
        }
        Map<String, Boolean> seleccion = new LinkedHashMap<>();
        for (String tipo : PreferenciaNotificacionService.TIPOS_EVENTO) {
            seleccion.put(tipo, params.containsKey(tipo));
        }
        preferenciaNotificacionService.actualizar(sesion.getPerfilId(), seleccion);
        redirectAttributes.addFlashAttribute("exito", "Preferencias de aviso actualizadas.");
        return "redirect:" + rutaSegura(volver);
    }

    /** "Enviarme el resumen ahora": manda el resumen semanal al correo de quien lo pide, para revisarlo. */
    @PostMapping("/notificaciones/resumen-semanal/probar")
    public String probarResumenSemanal(@RequestParam String volver, HttpSession session, RedirectAttributes redirectAttributes) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        if (sesion.getPerfilId() != null && resumenSemanalService.enviarA(sesion.getPerfilId())) {
            redirectAttributes.addFlashAttribute("exito", "Solicitamos el envío del resumen a tu correo.");
        } else {
            redirectAttributes.addFlashAttribute("error", "No se pudo armar tu resumen semanal.");
        }
        return "redirect:" + rutaSegura(volver);
    }

    private String rutaSegura(String volver) {
        if ("/administrador/notificaciones.html".equals(volver)
                || "/colaborador/notificaciones.html".equals(volver)
                || "/resource-manager/notificaciones.html".equals(volver)
                || "/project-manager/notificaciones.html".equals(volver)) {
            return volver;
        }
        return "/auth/login.html";
    }
}