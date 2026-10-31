package com.skillbridge.ai.controller;

import com.skillbridge.ai.dto.HiloDetalle;
import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.service.ForoService;
import com.skillbridge.ai.service.ShellModelBuilder;
import com.skillbridge.ai.util.OperacionInvalidaException;
import com.skillbridge.ai.util.Roles;
import com.skillbridge.ai.util.SesionKeys;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

/**
 * Detalle de un hilo, compartido por Colaborador (ForoHiloController) y
 * Project Manager (ForoHiloPmController). Quién puede marcar solución,
 * eliminar mensajes ajenos o fijar lo decide ForoService según el rol del
 * usuario EN ESE proyecto (PM activo = moderador); antes el controlador del
 * PM pasaba "es admin = true" para cualquier hilo, incluso de proyectos
 * donde esa persona solo era colaboradora.
 */
public abstract class ForoHiloBaseController {

    protected final ForoService foroService;
    protected final ShellModelBuilder shellModelBuilder;

    protected ForoHiloBaseController(ForoService foroService, ShellModelBuilder shellModelBuilder) {
        this.foroService = foroService;
        this.shellModelBuilder = shellModelBuilder;
    }

    /** "colaborador" o "project-manager". */
    protected abstract String base();

    private static UsuarioSesion sesion(HttpSession session) {
        return (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
    }

    private static boolean esAdmin(UsuarioSesion sesion) {
        return Roles.ADMINISTRADOR.equals(sesion.getRolEfectivo());
    }

    private String alHilo(Long hiloId) {
        return "redirect:/" + base() + "/foro-hilo.html?id=" + hiloId;
    }

    @GetMapping("/foro-hilo.html")
    public String hilo(@RequestParam Long id, HttpSession session, Model model, RedirectAttributes redirectAttributes) {
        UsuarioSesion sesion = sesion(session);
        HiloDetalle detalle;
        try {
            detalle = foroService.obtenerDetalle(id, sesion.getPerfilId(), esAdmin(sesion));
        } catch (OperacionInvalidaException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/" + base() + "/foros.html";
        }

        shellModelBuilder.aplicar(model, sesion, "foros.html", detalle.getTitulo(),
                "Foros › " + detalle.getProyectoNombre());
        model.addAttribute("hilo", detalle);
        model.addAttribute("topicos", foroService.topicos());
        model.addAttribute("baseUrl", "/" + base());
        model.addAttribute("maxArchivos", ForoService.MAX_ARCHIVOS_POR_MENSAJE);
        model.addAttribute("maxEnlaces", ForoService.MAX_ENLACES_POR_MENSAJE);
        return base() + "/foro-hilo";
    }

    @PostMapping("/foro-hilo/{id}/responder")
    public String responder(@PathVariable Long id,
                            @RequestParam(value = "contenido", required = false) String contenido,
                            @RequestParam(value = "respuestaA", required = false) Long respuestaA,
                            @RequestParam(value = "archivos", required = false) List<MultipartFile> archivos,
                            @RequestParam(value = "enlaces", required = false) List<String> enlaces,
                            HttpSession session, RedirectAttributes redirectAttributes) {
        UsuarioSesion sesion = sesion(session);
        try {
            Long nuevaId = foroService.responder(id, sesion.getPerfilId(), contenido, respuestaA, archivos, enlaces);
            redirectAttributes.addFlashAttribute("exito", "Tu respuesta se publicó en el hilo.");
            return alHilo(id) + "#m-" + nuevaId;
        } catch (OperacionInvalidaException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            // Se devuelve el borrador para no perder lo escrito.
            redirectAttributes.addFlashAttribute("borrador", contenido);
            return alHilo(id) + "#responder";
        }
    }

    @PostMapping("/foro-hilo/{hiloId}/respuestas/{respuestaId}/marcar-solucion")
    public String marcarSolucion(@PathVariable Long hiloId, @PathVariable Long respuestaId,
                                 HttpSession session, RedirectAttributes redirectAttributes) {
        UsuarioSesion sesion = sesion(session);
        try {
            foroService.marcarSolucion(respuestaId, sesion.getPerfilId(), esAdmin(sesion));
            redirectAttributes.addFlashAttribute("exito", "Respuesta marcada como solución del hilo.");
        } catch (OperacionInvalidaException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return alHilo(hiloId) + "#m-" + respuestaId;
    }

    @PostMapping("/foro-hilo/{id}/editar")
    public String editarHilo(@PathVariable Long id, @RequestParam String titulo, @RequestParam String contenido,
                             @RequestParam(value = "topico", required = false) String topico,
                             @RequestParam(value = "etiquetas", required = false) String etiquetas,
                             HttpSession session, RedirectAttributes redirectAttributes) {
        UsuarioSesion sesion = sesion(session);
        try {
            foroService.editarHilo(id, sesion.getPerfilId(), titulo, contenido, topico, etiquetas);
            redirectAttributes.addFlashAttribute("exito", "El hilo se actualizó.");
        } catch (OperacionInvalidaException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return alHilo(id);
    }

    @PostMapping("/foro-hilo/{id}/eliminar")
    public String eliminarHilo(@PathVariable Long id, @RequestParam(required = false) String motivo,
                               HttpSession session, RedirectAttributes redirectAttributes) {
        UsuarioSesion sesion = sesion(session);
        try {
            foroService.eliminarHilo(id, sesion.getPerfilId(), esAdmin(sesion), motivo);
            redirectAttributes.addFlashAttribute("exito", "El hilo se eliminó.");
        } catch (OperacionInvalidaException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return alHilo(id);
        }
        return "redirect:/" + base() + "/foros.html";
    }

    @PostMapping("/foro-hilo/{hiloId}/respuestas/{respuestaId}/editar")
    public String editarRespuesta(@PathVariable Long hiloId, @PathVariable Long respuestaId, @RequestParam String contenido,
                                  HttpSession session, RedirectAttributes redirectAttributes) {
        UsuarioSesion sesion = sesion(session);
        try {
            foroService.editarRespuesta(respuestaId, sesion.getPerfilId(), contenido);
            redirectAttributes.addFlashAttribute("exito", "La respuesta se actualizó.");
        } catch (OperacionInvalidaException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return alHilo(hiloId) + "#m-" + respuestaId;
    }

    @PostMapping("/foro-hilo/{hiloId}/respuestas/{respuestaId}/eliminar")
    public String eliminarRespuesta(@PathVariable Long hiloId, @PathVariable Long respuestaId,
                                    @RequestParam(required = false) String motivo,
                                    HttpSession session, RedirectAttributes redirectAttributes) {
        UsuarioSesion sesion = sesion(session);
        try {
            foroService.eliminarRespuesta(respuestaId, sesion.getPerfilId(), esAdmin(sesion), motivo);
            redirectAttributes.addFlashAttribute("exito", "La respuesta se eliminó.");
        } catch (OperacionInvalidaException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return alHilo(hiloId);
    }

    /**
     * "Me fue útil" sin recargar la página (fetch desde foro-hilo.html).
     * Responde {destacado: bool, total: n} o {error: "..."} con 400.
     */
    @PostMapping("/foro-hilo/mensajes/{mensajeId}/destacar")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> destacar(@PathVariable Long mensajeId, HttpSession session) {
        UsuarioSesion sesion = sesion(session);
        try {
            boolean destacado = foroService.alternarDestacado(mensajeId, sesion.getPerfilId(), esAdmin(sesion));
            return ResponseEntity.ok(Map.of("destacado", destacado, "total", foroService.numDestacados(mensajeId)));
        } catch (OperacionInvalidaException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    /**
     * Genera (o regenera) el resumen con IA del hilo y lo deja guardado en
     * BD (foro_publicaciones.resumen_ia) - ver ForoService.generarResumenIa
     * y AsistenteIaService.resumirHilo. Responde {resumen: "..."} o
     * {error: "..."} con 400.
     */
    @PostMapping("/foro-hilo/{id}/resumen-ia")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> resumenIa(@PathVariable Long id, HttpSession session) {
        UsuarioSesion sesion = sesion(session);
        try {
            String resumen = foroService.generarResumenIa(id, sesion.getPerfilId(), esAdmin(sesion));
            return ResponseEntity.ok(Map.of("resumen", resumen));
        } catch (OperacionInvalidaException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }
}
