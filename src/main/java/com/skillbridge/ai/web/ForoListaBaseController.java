package com.skillbridge.ai.web;

import com.skillbridge.ai.dto.HiloFila;
import com.skillbridge.ai.dto.ProyectoOpcion;
import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.model.ForoPublicacion;
import com.skillbridge.ai.service.ForoService;
import com.skillbridge.ai.service.ShellModelBuilder;
import com.skillbridge.ai.util.OperacionInvalidaException;
import com.skillbridge.ai.util.Roles;
import com.skillbridge.ai.util.SesionKeys;
import jakarta.servlet.http.HttpSession;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * Lista de foros, compartida por Colaborador (ForosController) y Project
 * Manager (ForosPmController): ambos ven los hilos de los proyectos donde
 * tienen o tuvieron una asignación. Las diferencias de permisos (fijar,
 * moderar) las decide ForoService según el rol EN CADA PROYECTO, no según
 * la URL, así que la lógica vive una sola vez aquí.
 *
 * Las subclases solo fijan el prefijo de rutas con @RequestMapping e
 * implementan base() ("colaborador" o "project-manager").
 */
public abstract class ForoListaBaseController {

    protected final ForoService foroService;
    protected final ShellModelBuilder shellModelBuilder;

    protected ForoListaBaseController(ForoService foroService, ShellModelBuilder shellModelBuilder) {
        this.foroService = foroService;
        this.shellModelBuilder = shellModelBuilder;
    }

    /** "colaborador" o "project-manager": carpeta de la plantilla y prefijo de las rutas. */
    protected abstract String base();

    @GetMapping("/foros.html")
    public String foros(HttpSession session, Model model) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        Long perfilId = sesion.getPerfilId();

        List<HiloFila> hilos = foroService.listarHilosDeMisProyectos(perfilId);
        List<ProyectoOpcion> proyectos = foroService.misProyectosConHilos(perfilId);
        long totalRespuestas = hilos.stream().mapToLong(HiloFila::getNumRespuestas).sum();

        shellModelBuilder.aplicar(model, sesion, "foros.html", "Foros",
                "Centro de ayuda de tus proyectos · " + hilos.size() + (hilos.size() == 1 ? " hilo" : " hilos"));

        model.addAttribute("hilos", hilos);
        model.addAttribute("proyectos", proyectos);
        model.addAttribute("proyectosPublicar", foroService.proyectosDondePublicar(perfilId));
        model.addAttribute("topicos", foroService.topicosConConteo(hilos));
        model.addAttribute("totalRespuestas", totalRespuestas);
        model.addAttribute("numSinResponder", hilos.stream().filter(h -> h.getNumRespuestas() == 0).count());
        model.addAttribute("numResueltos", hilos.stream().filter(HiloFila::isTieneAceptada).count());
        model.addAttribute("numMios", hilos.stream().filter(HiloFila::isEsMio).count());
        model.addAttribute("baseUrl", "/" + base());
        model.addAttribute("maxArchivos", ForoService.MAX_ARCHIVOS_POR_MENSAJE);
        model.addAttribute("maxEnlaces", ForoService.MAX_ENLACES_POR_MENSAJE);
        return base() + "/foros";
    }

    @PostMapping("/foros/nuevo-hilo")
    public String nuevoHilo(@RequestParam Long proyectoId, @RequestParam String titulo, @RequestParam String contenido,
                            @RequestParam(value = "topico", required = false) String topico,
                            @RequestParam(value = "etiquetas", required = false) String etiquetas,
                            @RequestParam(value = "archivos", required = false) List<MultipartFile> archivos,
                            @RequestParam(value = "enlaces", required = false) List<String> enlaces,
                            HttpSession session, RedirectAttributes redirectAttributes) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        try {
            ForoPublicacion hilo = foroService.crearHilo(sesion.getPerfilId(), proyectoId, titulo, contenido,
                    topico, etiquetas, archivos, enlaces);
            redirectAttributes.addFlashAttribute("exito", "Tu hilo se publicó correctamente.");
            return "redirect:/" + base() + "/foro-hilo.html?id=" + hilo.getId();
        } catch (OperacionInvalidaException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/" + base() + "/foros.html";
        }
    }

    /** Fijar/desfijar: solo el PM activo del proyecto (lo verifica ForoService). */
    @PostMapping("/foros/{id}/fijar")
    public String fijar(@PathVariable Long id, @RequestParam boolean valor,
                        @RequestParam(value = "volver", required = false) String volver,
                        HttpSession session, RedirectAttributes redirectAttributes) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        boolean esAdmin = Roles.ADMINISTRADOR.equals(sesion.getRolEfectivo());
        try {
            foroService.establecerFijado(id, valor, sesion.getPerfilId(), esAdmin);
            redirectAttributes.addFlashAttribute("exito", valor ? "Hilo fijado: aparece primero en la lista." : "Hilo desfijado.");
        } catch (OperacionInvalidaException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "hilo".equals(volver)
                ? "redirect:/" + base() + "/foro-hilo.html?id=" + id
                : "redirect:/" + base() + "/foros.html";
    }
}
