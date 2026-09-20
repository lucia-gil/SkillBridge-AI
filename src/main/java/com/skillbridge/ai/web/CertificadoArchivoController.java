package com.skillbridge.ai.web;

import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.model.CertificadoHabilidad;
import com.skillbridge.ai.repository.CertificadoHabilidadRepository;
import com.skillbridge.ai.util.Roles;
import com.skillbridge.ai.util.SesionKeys;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Sirve el contenido binario de un certificado subido como PDF real
 * (además del link externo - ver CertificadoHabilidad). Vive fuera de
 * /colaborador/** y /resource-manager/** porque lo consultan AMBOS roles
 * (el colaborador para ver su propia constancia, el Resource Manager -y
 * Project Manager/Administrador- para revisarla antes de validar la
 * habilidad) - mismo criterio que /cuenta/** y /notificaciones/** en
 * SesionInterceptor: solo exige sesión activa, no un rol específico.
 */
@Controller
public class CertificadoArchivoController {

    private final CertificadoHabilidadRepository certificadoHabilidadRepository;

    public CertificadoArchivoController(CertificadoHabilidadRepository certificadoHabilidadRepository) {
        this.certificadoHabilidadRepository = certificadoHabilidadRepository;
    }

    @GetMapping("/certificados/{id}/archivo")
    public ResponseEntity<byte[]> descargar(@PathVariable Long id, HttpSession session) {
        UsuarioSesion sesion = session != null ? (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO) : null;
        if (sesion == null) {
            return ResponseEntity.status(302).header(HttpHeaders.LOCATION, "/auth/login.html").build();
        }

        return certificadoHabilidadRepository.findById(id)
                .filter(CertificadoHabilidad::tieneArchivo)
                // Un colaborador solo puede abrir SU PROPIA constancia; el resto
                // de roles (RM, PM, Administrador) la revisan de cualquiera -
                // es justamente para eso que existe esta pantalla.
                .filter(c -> !Roles.COLABORADOR.equals(sesion.getRolEfectivo()) || c.getPerfilId().equals(sesion.getPerfilId()))
                .map(c -> {
                    MediaType mediaType;
                    try {
                        mediaType = MediaType.parseMediaType(
                                c.getTipoArchivo() != null ? c.getTipoArchivo() : "application/pdf");
                    } catch (Exception ex) {
                        mediaType = MediaType.APPLICATION_PDF;
                    }
                    return ResponseEntity.ok()
                            .contentType(mediaType)
                            .cacheControl(CacheControl.noCache())
                            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + c.getNombreArchivo() + "\"")
                            .body(c.getContenidoArchivo());
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
