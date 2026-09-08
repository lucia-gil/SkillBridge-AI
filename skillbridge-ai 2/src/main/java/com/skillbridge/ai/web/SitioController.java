package com.skillbridge.ai.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Sirve lo que del frontend de SkillBridge AI sigue siendo la demo con
 * datos simulados (mock-data.js): Resource Manager completo (fuera de
 * alcance de esta entrega) y las paginas de Project Manager que aun no se
 * hicieron reales, mas el Asistente IA de Administrador y Colaborador
 * (excluido explicitamente).
 *
 * Administrador y Colaborador ya no pasan por aqui para el resto de sus
 * paginas: cada una tiene su propio @Controller con datos y persistencia
 * reales. DESDE ESTA ENTREGA, el Project Manager tambien tiene reales sus
 * CRUD de Proyectos, Asignaciones y Foros (ProyectosPmController,
 * AsignacionesPmController, ForosPmController, ForoHiloPmController), por
 * eso esas rutas se retiraron de la lista de abajo: mapearlas tambien aqui
 * produciria un error de "Ambiguous mapping" al arrancar Spring.
 *
 * Las paginas que si sirve este controlador siguen usando su shell JS
 * original (static/js/shell.js), que decide que sidebar/topbar mostrar
 * leyendo el rol activo de sessionStorage. Como el login real usa
 * HttpSession en el servidor, cada plantilla recibe "rolSlug" y lo escribe
 * a sessionStorage antes de que corra shell.js.
 */
@Controller
public class SitioController {

    private String vista(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.startsWith("/")) path = path.substring(1);
        if (path.endsWith(".html")) path = path.substring(0, path.length() - 5);
        return path;
    }

    @GetMapping("/auth/recuperar-password.html")
    public String recuperarPassword() {
        return "auth/recuperar-password";
    }

    // Unico mock que le queda a Administrador: el chatbot de IA esta fuera
    // de alcance por pedido explicito, aunque el resto del rol es real.
    @GetMapping("/administrador/asistente-ia.html")
    public String administrador(HttpServletRequest request, Model model) {
        model.addAttribute("rolSlug", "administrador");
        return vista(request);
    }

    // Idem para Colaborador.
    @GetMapping("/colaborador/asistente-ia.html")
    public String colaborador(HttpServletRequest request, Model model) {
        model.addAttribute("rolSlug", "colaborador");
        return vista(request);
    }

    // Project Manager: solo quedan como mock las paginas aun no migradas.
    // proyectos/asignaciones/foros/foro-hilo YA tienen controlador real.
    @GetMapping({
            "/project-manager/proyecto-detalle.html",
            "/project-manager/ai-talent-matching.html",
            "/project-manager/asistente-ia.html",
            "/project-manager/reportes.html",
            "/project-manager/mi-cuenta.html",
            "/project-manager/notificaciones.html"
    })
    public String projectManager(HttpServletRequest request, Model model) {
        model.addAttribute("rolSlug", "project-manager");
        return vista(request);
    }

    @GetMapping({
            "/resource-manager/inicio.html",
            "/resource-manager/ocupacion.html",
            "/resource-manager/asignaciones.html",
            "/resource-manager/colaboradores.html",
            "/resource-manager/ai-talent-matching.html",
            "/resource-manager/reportes.html",
            "/resource-manager/mi-cuenta.html",
            "/resource-manager/notificaciones.html",
            "/resource-manager/asistente-ia.html"
    })
    public String resourceManager(HttpServletRequest request, Model model) {
        model.addAttribute("rolSlug", "resource-manager");
        return vista(request);
    }
}
