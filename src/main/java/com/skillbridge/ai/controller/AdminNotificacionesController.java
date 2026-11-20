package com.skillbridge.ai.controller;

import com.skillbridge.ai.dto.NotificacionFila;
import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.service.NotificacionService;
import com.skillbridge.ai.util.Roles;
import com.skillbridge.ai.util.SesionKeys;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/administrador/notificaciones")
@PreAuthorize("hasAuthority('administrador')")
public class AdminNotificacionesController {

    private final NotificacionService notificaciones;

    public AdminNotificacionesController(NotificacionService notificaciones) {
        this.notificaciones = notificaciones;
    }

    @GetMapping("/recientes")
    public ResponseEntity<Campana> recientes(HttpSession session) {
        UsuarioSesion actor = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        if (actor == null || !Roles.ADMINISTRADOR.equals(actor.getRolOrganizacional())) {
            return ResponseEntity.status(403).build();
        }

        long sinLeer = notificaciones.contarNoLeidas(actor);
        List<Aviso> avisos = notificaciones.recientes(actor).stream().map(Aviso::de).toList();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Campana(sinLeer, avisos));
    }

    public record Campana(long sinLeer, List<Aviso> avisos) {}

    public record Aviso(String id, String titulo, String fecha, boolean leida, String icono, String tono) {
        static Aviso de(NotificacionFila aviso) {
            return new Aviso(aviso.getId().toString(), aviso.getTitulo(), aviso.getFechaLabel(),
                    aviso.isLeida(), aviso.getIconId(), aviso.getIconClass());
        }
    }
}
