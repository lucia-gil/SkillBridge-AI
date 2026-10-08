package com.skillbridge.ai.web;

import com.skillbridge.ai.model.ArchivoProyecto;
import com.skillbridge.ai.repository.ArchivoProyectoRepository;
import com.skillbridge.ai.util.OperacionInvalidaException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;

@Controller
public class ArchivoProyectoController {

    private final ArchivoProyectoRepository archivoProyectoRepository;

    public ArchivoProyectoController(ArchivoProyectoRepository archivoProyectoRepository) {
        this.archivoProyectoRepository = archivoProyectoRepository;
    }

    @PostMapping("/project-manager/proyectos/{proyectoId}/archivos")
    public String subir(@PathVariable Long proyectoId,
                        @RequestParam("archivo") MultipartFile archivo,
                        RedirectAttributes redirectAttributes) {
        try {
            if (archivo.isEmpty()) {
                throw new OperacionInvalidaException("Selecciona un archivo.");
            }
            if (archivo.getSize() > 10 * 1024 * 1024) {
                throw new OperacionInvalidaException("El archivo no puede superar 10 MB.");
            }
            if (archivoProyectoRepository.countByProyectoId(proyectoId) >= 10) {
                throw new OperacionInvalidaException("Máximo 10 archivos por proyecto.");
            }
            ArchivoProyecto ap = new ArchivoProyecto();
            ap.setProyectoId(proyectoId);
            ap.setNombre(archivo.getOriginalFilename());
            ap.setTipo(archivo.getContentType());
            ap.setDatos(archivo.getBytes());
            archivoProyectoRepository.save(ap);
            redirectAttributes.addFlashAttribute("exito", "Archivo \"" + archivo.getOriginalFilename() + "\" subido.");
        } catch (OperacionInvalidaException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        } catch (IOException ex) {
            redirectAttributes.addFlashAttribute("error", "Error al leer el archivo.");
        }
        return "redirect:/project-manager/proyectos.html";
    }

    @GetMapping("/archivos-proyecto/{id}")
    @ResponseBody
    public ResponseEntity<byte[]> descargar(@PathVariable Long id) {
        ArchivoProyecto ap = archivoProyectoRepository.findById(id)
                .orElseThrow(() -> new OperacionInvalidaException("El archivo ya no existe."));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + ap.getNombre() + "\"")
                .contentType(MediaType.parseMediaType(ap.getTipo() != null ? ap.getTipo() : "application/octet-stream"))
                .body(ap.getDatos());
    }

    @PostMapping("/project-manager/proyectos/{proyectoId}/archivos/{id}/eliminar")
    public String eliminar(@PathVariable Long proyectoId, @PathVariable Long id,
                           RedirectAttributes redirectAttributes) {
        archivoProyectoRepository.deleteById(id);
        redirectAttributes.addFlashAttribute("exito", "Archivo eliminado.");
        return "redirect:/project-manager/proyectos.html";
    }
}
