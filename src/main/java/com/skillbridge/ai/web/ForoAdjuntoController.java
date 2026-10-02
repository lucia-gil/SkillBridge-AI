package com.skillbridge.ai.web;

import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.model.ForoAdjunto;
import com.skillbridge.ai.service.ForoService;
import com.skillbridge.ai.util.OperacionInvalidaException;
import com.skillbridge.ai.util.Roles;
import com.skillbridge.ai.util.SesionKeys;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Sirve los adjuntos del foro (mismo patrón que EntregaArchivoController).
 * Solo los ve quien participa en el proyecto del hilo (lo verifica
 * ForoService.obtenerAdjunto). Por defecto se muestra en el navegador
 * (imágenes y PDF se ven directo); con ?descargar=true se descarga.
 *
 * Seguridad: el tipo MIME se fijó al subir según la extensión (no se aceptan
 * SVG ni HTML), y además se envía nosniff + CSP sandbox para que un archivo
 * nunca pueda ejecutar scripts en el dominio de la aplicación.
 */
@Controller
public class ForoAdjuntoController {

    private final ForoService foroService;

    public ForoAdjuntoController(ForoService foroService) {
        this.foroService = foroService;
    }

    @GetMapping("/foros/adjuntos/{id}")
    public ResponseEntity<byte[]> ver(@PathVariable Long id,
                                      @RequestParam(value = "descargar", defaultValue = "false") boolean descargar,
                                      HttpSession session) {
        UsuarioSesion sesion = session != null ? (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO) : null;
        if (sesion == null) {
            return ResponseEntity.status(302).header(HttpHeaders.LOCATION, "/auth/login.html").build();
        }
        ForoAdjunto adjunto;
        try {
            adjunto = foroService.obtenerAdjunto(id, sesion.getPerfilId(), Roles.ADMINISTRADOR.equals(sesion.getRolEfectivo()));
        } catch (OperacionInvalidaException ex) {
            return ResponseEntity.notFound().build();
        }

        // Enlaces (o archivos guardados en almacenamiento externo): se redirige a la URL.
        if (adjunto.getContenido() == null) {
            return adjunto.getUrl() != null
                    ? ResponseEntity.status(302).header(HttpHeaders.LOCATION, adjunto.getUrl()).build()
                    : ResponseEntity.notFound().build();
        }

        MediaType tipo;
        try {
            tipo = MediaType.parseMediaType(adjunto.getMimeTipo() != null ? adjunto.getMimeTipo() : "application/octet-stream");
        } catch (Exception e) {
            tipo = MediaType.APPLICATION_OCTET_STREAM;
        }
        ContentDisposition disposicion = (descargar ? ContentDisposition.attachment() : ContentDisposition.inline())
                .filename(adjunto.getNombre(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(tipo)
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePrivate())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposicion.toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "sandbox")
                .body(adjunto.getContenido());
    }
}
