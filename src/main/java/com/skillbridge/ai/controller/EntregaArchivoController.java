package com.skillbridge.ai.controller;

import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.model.Entrega;
import com.skillbridge.ai.repository.AsignacionRepository;
import com.skillbridge.ai.repository.EntregableRepository;
import com.skillbridge.ai.repository.EntregaRepository;
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

import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Sirve el archivo binario de una entrega (mismo patrón que
 * CertificadoArchivoController). El dueño de la entrega SIEMPRE puede
 * verla; cualquier otro rol solo si gestiona el proyecto (PM activo o
 * Administrador) - un colaborador de OTRO proyecto no puede husmear.
 */
@Controller
public class EntregaArchivoController {

    private static final Set<String> ABRIBLES_EN_NAVEGADOR =
            Set.of("application/pdf", "image/png", "image/jpeg", "image/webp");

    private final EntregaRepository entregaRepository;
    private final EntregableRepository entregableRepository;
    private final AsignacionRepository asignacionRepository;

    public EntregaArchivoController(EntregaRepository entregaRepository, EntregableRepository entregableRepository,
                                    AsignacionRepository asignacionRepository) {
        this.entregaRepository = entregaRepository;
        this.entregableRepository = entregableRepository;
        this.asignacionRepository = asignacionRepository;
    }

    @GetMapping("/entregas/{id}/archivo")
    public ResponseEntity<byte[]> descargar(@PathVariable Long id, HttpSession session) {
        UsuarioSesion sesion = session != null ? (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO) : null;
        if (sesion == null) {
            return ResponseEntity.status(302).header(HttpHeaders.LOCATION, "/auth/login.html").build();
        }

        return entregaRepository.findById(id)
                .filter(Entrega::tieneArchivo)
                .filter(en -> puedeVer(en, sesion))
                .map(en -> {
                    MediaType mediaType;
                    try {
                        mediaType = MediaType.parseMediaType(
                                en.getArchivoTipo() != null ? en.getArchivoTipo() : "application/octet-stream");
                    } catch (Exception ex) {
                        mediaType = MediaType.APPLICATION_OCTET_STREAM;
                    }
                    // Solo PDF e imágenes se abren en el navegador; todo lo demás se
                    // descarga. nosniff + CSP sandbox: aunque alguien suba un HTML
                    // disfrazado, el navegador no lo ejecuta en nuestro dominio.
                    boolean enNavegador = ABRIBLES_EN_NAVEGADOR.contains(mediaType.getType() + "/" + mediaType.getSubtype());
                    ContentDisposition disposicion = (enNavegador ? ContentDisposition.inline() : ContentDisposition.attachment())
                            .filename(en.getArchivoNombre() != null ? en.getArchivoNombre() : "entrega", StandardCharsets.UTF_8)
                            .build();
                    return ResponseEntity.ok()
                            .contentType(mediaType)
                            .cacheControl(CacheControl.noCache())
                            .header(HttpHeaders.CONTENT_DISPOSITION, disposicion.toString())
                            .header("X-Content-Type-Options", "nosniff")
                            .header("Content-Security-Policy", "sandbox")
                            .body(en.getArchivoContenido());
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private boolean puedeVer(Entrega en, UsuarioSesion sesion) {
        if (en.getPerfilId().equals(sesion.getPerfilId())) return true;
        if (Roles.ADMINISTRADOR.equals(sesion.getRolEfectivo())) return true;
        return entregableRepository.findById(en.getEntregableId())
                .map(e -> asignacionRepository.existsByProyectoIdAndPerfilIdAndRolEnProyectoAndEstado(
                        e.getProyectoId(), sesion.getPerfilId(), Roles.PROJECT_MANAGER, "activa"))
                .orElse(false);
    }
}
