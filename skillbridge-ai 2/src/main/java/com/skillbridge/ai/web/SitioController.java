package com.skillbridge.ai.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Sirve lo que del frontend de SkillBridge AI sigue siendo la demo con
 * datos simulados (mock-data.js): Project Manager y Resource Manager
 * completos (fuera de alcance de esta entrega), y el Asistente IA de
 * Administrador y Colaborador (excluido explícitamente aunque esos dos
 * roles sí se hicieron reales - ver README, "Alcance").
 *
 * Administrador y Colaborador YA NO pasan por aquí para el resto de sus
 * páginas: cada una tiene su propio @Controller con datos y persistencia
 * reales (AdminInicioController, AdminProyectosController,
 * AdminReportesController, AdminAuditoriaController,
 * AdminConfiguracionController, ColaboradorInicioController,
 * ColaboradorPerfilController, ColaboradorProyectosController,
 * ForosController, ForoHiloController, CuentaController y
 * NotificacionesController, estos dos últimos compartidos entre ambos
 * roles). Mapear esas mismas rutas también aquí produciría un error de
 * "Ambiguous mapping" al arrancar Spring, por eso se retiraron de las
 * listas de abajo.
 *
 * Las páginas que sí sirve este controlador siguen usando su shell JS
 * original (static/js/shell.js, SBAI.shell.init), que decide qué
 * sidebar/topbar mostrar leyendo el rol activo de sessionStorage. Como el
 * login real de esta entrega NO pasa por ese mecanismo (usa HttpSession en
 * el servidor), cada plantilla de aquí recibe "rolSlug" y lo escribe a
 * sessionStorage con un script mínimo antes de que corra shell.js, para
 * que la sesión del navegador quede sincronizada con la sesión real del
 * servidor y esas páginas sigan funcionando exactamente igual que antes.
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

    // Único mock que le queda a Administrador: el chatbot de IA está fuera
    // de alcance por pedido explícito, aunque el resto del rol es real.
    @GetMapping("/administrador/asistente-ia.html")
    public String administrador(HttpServletRequest request, Model model) {
        model.addAttribute("rolSlug", "administrador");
        return vista(request);
    }

    // Ídem para Colaborador.
    @GetMapping("/colaborador/asistente-ia.html")
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
