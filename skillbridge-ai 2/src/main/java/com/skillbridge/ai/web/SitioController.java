package com.skillbridge.ai.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Sirve el resto del frontend de SkillBridge AI que NO forma parte de los
 * 2 CRUD ni de login/registro pedidos en esta entrega (inicio, proyectos,
 * reportes, foros, asistente IA, etc. de los 4 roles). Estas paginas
 * siguen siendo la demo con datos simulados (mock-data.js) que ya existia
 * -no se les agrego persistencia real-, pero ahora se sirven via Spring
 * MVC/Thymeleaf en vez de como archivos estaticos, con las rutas de
 * CSS/JS/iconos ya corregidas (ver corrida de correccion global sobre
 * "../../static/").
 *
 * Cada pagina sigue usando su shell JS original (static/js/shell.js,
 * SBAI.shell.init), que decide que sidebar/topbar mostrar leyendo el rol
 * activo de sessionStorage. Como el login real de esta entrega NO pasa por
 * ese mecanismo (usa HttpSession en el servidor), cada plantilla de aqui
 * recibe "rolSlug" y lo escribe a sessionStorage con un script minimo
 * antes de que corra shell.js, para que la sesion del navegador quede
 * sincronizada con la sesion real del servidor y esas paginas sigan
 * funcionando exactamente igual que antes.
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

    @GetMapping({
            "/administrador/inicio.html",
            "/administrador/proyectos.html",
            "/administrador/reportes.html",
            "/administrador/auditoria.html",
            "/administrador/configuracion.html",
            "/administrador/mi-cuenta.html",
            "/administrador/notificaciones.html",
            "/administrador/asistente-ia.html"
    })
    public String administrador(HttpServletRequest request, Model model) {
        model.addAttribute("rolSlug", "administrador");
        return vista(request);
    }

    @GetMapping({
            "/colaborador/inicio.html",
            "/colaborador/perfil.html",
            "/colaborador/proyectos.html",
            "/colaborador/foros.html",
            "/colaborador/foro-hilo.html",
            "/colaborador/notificaciones.html",
            "/colaborador/mi-cuenta.html",
            "/colaborador/asistente-ia.html"
    })
    public String colaborador(HttpServletRequest request, Model model) {
        model.addAttribute("rolSlug", "colaborador");
        return vista(request);
    }

    @GetMapping({
            "/project-manager/inicio.html",
            "/project-manager/proyectos.html",
            "/project-manager/proyecto-detalle.html",
            "/project-manager/asignaciones.html",
            "/project-manager/ai-talent-matching.html",
            "/project-manager/foros.html",
            "/project-manager/foro-hilo.html",
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
