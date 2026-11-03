package com.skillbridge.ai.controller;

import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.model.ArchivoProyecto;
import com.skillbridge.ai.repository.ArchivoProyectoRepository;
import com.skillbridge.ai.repository.AsignacionRepository;
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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Archivos de contexto de un proyecto (tabla archivos_proyecto), gestionados
 * desde project-manager/proyectos.html.
 *
 * Permisos:
 *  - subir / eliminar: solo el Project Manager ACTIVO de ese proyecto (y al
 *    eliminar, el archivo tiene que pertenecer al proyecto de la URL).
 *  - ver / descargar: cualquier integrante activo del proyecto (PM o
 *    colaborador), Administrador o Resource Manager.
 *
 * La descarga sigue el mismo patrón que EntregaArchivoController: solo PDF e
 * imágenes se abren en el navegador, el resto se descarga, y siempre con
 * nosniff + CSP sandbox para que un HTML subido no se ejecute en nuestro
 * dominio.
 */
@Controller
public class ArchivoProyectoController {

    private static final Set<String> ABRIBLES_EN_NAVEGADOR =
            Set.of("application/pdf", "image/png", "image/jpeg", "image/webp", "image/gif");

    /**
     * Debe ser <= spring.servlet.multipart.max-file-size (application.properties);
     * si el archivo supera ese límite, Spring lo rechaza antes de llegar aquí
     * (ver SubidaArchivosAdvice).
     */
    private static final long TAMANO_MAXIMO = 5L * 1024 * 1024;
    private static final int MAXIMO_POR_PROYECTO = 10;

    private final ArchivoProyectoRepository archivoProyectoRepository;
    private final AsignacionRepository asignacionRepository;

    public ArchivoProyectoController(ArchivoProyectoRepository archivoProyectoRepository,
                                     AsignacionRepository asignacionRepository) {
        this.archivoProyectoRepository = archivoProyectoRepository;
        this.asignacionRepository = asignacionRepository;
    }

    @PostMapping("/project-manager/proyectos/{proyectoId}/archivos")
    public String subir(@PathVariable Long proyectoId,
                        @RequestParam("archivo") MultipartFile archivo,
                        HttpSession session,
                        RedirectAttributes redirectAttributes) {
        try {
            exigirPmDelProyecto(sesion(session), proyectoId);
            if (archivo.isEmpty()) {
                throw new OperacionInvalidaException("Selecciona un archivo.");
            }
            if (archivo.getSize() > TAMANO_MAXIMO) {
                throw new OperacionInvalidaException("El archivo no puede superar 5 MB.");
            }
            if (archivoProyectoRepository.countByProyectoId(proyectoId) >= MAXIMO_POR_PROYECTO) {
                throw new OperacionInvalidaException("Máximo " + MAXIMO_POR_PROYECTO + " archivos por proyecto.");
            }
            String nombre = nombreSeguro(archivo.getOriginalFilename());
            ArchivoProyecto ap = new ArchivoProyecto();
            ap.setProyectoId(proyectoId);
            ap.setNombre(nombre);
            ap.setTipo(archivo.getContentType());
            ap.setDatos(archivo.getBytes());
            archivoProyectoRepository.save(ap);
            redirectAttributes.addFlashAttribute("exito", "Archivo subido.");
        } catch (OperacionInvalidaException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        } catch (IOException ex) {
            redirectAttributes.addFlashAttribute("error", "Error al leer el archivo.");
        }
        return "redirect:/project-manager/proyectos.html";
    }

    @GetMapping("/archivos-proyecto/{id}")
    @ResponseBody
    public ResponseEntity<byte[]> descargar(@PathVariable Long id, HttpSession session) {
        UsuarioSesion sesion = sesion(session);
        if (sesion == null) {
            return ResponseEntity.status(302).header(HttpHeaders.LOCATION, "/auth/login.html").build();
        }
        return archivoProyectoRepository.findById(id)
                .filter(ap -> puedeVer(ap, sesion))
                .map(ap -> {
                    MediaType mediaType;
                    try {
                        mediaType = MediaType.parseMediaType(
                                ap.getTipo() != null ? ap.getTipo() : "application/octet-stream");
                    } catch (Exception ex) {
                        mediaType = MediaType.APPLICATION_OCTET_STREAM;
                    }
                    boolean enNavegador = ABRIBLES_EN_NAVEGADOR.contains(mediaType.getType() + "/" + mediaType.getSubtype());
                    ContentDisposition disposicion = (enNavegador ? ContentDisposition.inline() : ContentDisposition.attachment())
                            .filename(ap.getNombre() != null ? ap.getNombre() : "archivo", StandardCharsets.UTF_8)
                            .build();
                    return ResponseEntity.ok()
                            .contentType(enNavegador ? mediaType : MediaType.APPLICATION_OCTET_STREAM)
                            .cacheControl(CacheControl.noCache())
                            .header(HttpHeaders.CONTENT_DISPOSITION, disposicion.toString())
                            .header("X-Content-Type-Options", "nosniff")
                            .header("Content-Security-Policy", "sandbox")
                            .body(ap.getDatos());
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/project-manager/proyectos/{proyectoId}/archivos/{id}/eliminar")
    public String eliminar(@PathVariable Long proyectoId, @PathVariable Long id,
                           HttpSession session,
                           RedirectAttributes redirectAttributes) {
        try {
            exigirPmDelProyecto(sesion(session), proyectoId);
            ArchivoProyecto ap = archivoProyectoRepository.findById(id)
                    .filter(a -> proyectoId.equals(a.getProyectoId()))
                    .orElseThrow(() -> new OperacionInvalidaException("El archivo ya no existe."));
            archivoProyectoRepository.delete(ap);
            redirectAttributes.addFlashAttribute("exito", "Archivo eliminado.");
        } catch (OperacionInvalidaException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/project-manager/proyectos.html";
    }

    // ─────────────────────────── helpers ───────────────────────────

    private static UsuarioSesion sesion(HttpSession session) {
        return session != null ? (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO) : null;
    }

    private void exigirPmDelProyecto(UsuarioSesion sesion, Long proyectoId) {
        if (sesion == null || sesion.getPerfilId() == null
                || !asignacionRepository.existsByProyectoIdAndPerfilIdAndRolEnProyectoAndEstado(
                        proyectoId, sesion.getPerfilId(), Roles.PROJECT_MANAGER, "activa")) {
            throw new OperacionInvalidaException("Ese proyecto no esta bajo tu gestion.");
        }
    }

    private boolean puedeVer(ArchivoProyecto ap, UsuarioSesion sesion) {
        String rolOrg = sesion.getRolOrganizacional();
        if (Roles.ADMINISTRADOR.equals(rolOrg) || Roles.RESOURCE_MANAGER.equals(rolOrg)) return true;
        if (sesion.getPerfilId() == null) return false;
        return asignacionRepository.existsByProyectoIdAndPerfilIdAndRolEnProyectoAndEstado(
                        ap.getProyectoId(), sesion.getPerfilId(), Roles.PROJECT_MANAGER, "activa")
                || asignacionRepository.existsByProyectoIdAndPerfilIdAndRolEnProyectoAndEstado(
                        ap.getProyectoId(), sesion.getPerfilId(), Roles.COLABORADOR, "activa");
    }

    /** Quita rutas ("C:\\x\\a.pdf" -> "a.pdf"), caracteres de control y comillas; máx. 255. */
    private static String nombreSeguro(String original) {
        String n = original == null ? "" : original;
        n = n.substring(Math.max(n.lastIndexOf('/'), n.lastIndexOf('\\')) + 1);
        n = n.replaceAll("[\\p{Cntrl}\"<>]", "").trim();
        if (n.isEmpty()) n = "archivo";
        return n.length() > 255 ? n.substring(n.length() - 255) : n;
    }
}
