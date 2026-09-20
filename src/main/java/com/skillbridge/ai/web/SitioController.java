package com.skillbridge.ai.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Sirve lo que del frontend de SkillBridge AI sigue siendo la demo con
 * datos simulados (mock-data.js): las paginas de Project Manager y Resource
 * Manager que aun no se hicieron reales, mas el Asistente IA de
 * Administrador y Colaborador (excluido explicitamente).
 *
 * Administrador y Colaborador ya no pasan por aqui para el resto de sus
 * paginas: cada una tiene su propio @Controller con datos y persistencia
 * reales. DESDE ESTA ENTREGA, el Project Manager tambien tiene reales sus
 * CRUD de Proyectos, Asignaciones, Foros, Calendario, Reportes y AI Talent
 * Matching (ProyectosPmController, AsignacionesPmController,
 * ForosPmController, ForoHiloPmController, CalendarioController,
 * ReportesPmController, TalentMatchingController), y el Resource Manager
 * ya tiene reales su Ocupacion, Asignaciones, Colaboradores y su resolucion
 * de AI Talent Matching (RmOcupacionController, RmAsignacionesController,
 * RmColaboradoresController, TalentMatchingController), por eso esas rutas
 * se retiraron de la lista de abajo: mapearlas tambien aqui produciria un
 * error de "Ambiguous mapping" al arrancar Spring.
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

    // Vista separada a la que llega el enlace enviado por correo. Sin
    // backend de tokens real todavia (esta demo no envia correos), así que
    // no valida ningun parametro; solo sirve la plantilla.
    @GetMapping("/auth/restablecer-password.html")
    public String restablecerPassword() {
        return "auth/restablecer-password";
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
    // proyectos/asignaciones/foros/foro-hilo/calendario/reportes/
    // ai-talent-matching YA tienen controlador real. mi-cuenta.html tambien
    // es real (CuentaController) por eso se retiro de esta lista: dejarla
    // mapeada aqui tambien produce el error de "Ambiguous mapping".
    @GetMapping({
            "/project-manager/proyecto-detalle.html",
            "/project-manager/asistente-ia.html",
            "/project-manager/notificaciones.html"
    })
    public String projectManager(HttpServletRequest request, Model model) {
        model.addAttribute("rolSlug", "project-manager");
        return vista(request);
    }

    // Resource Manager: ocupacion/asignaciones/colaboradores/ai-talent-matching/
    // inicio/reportes YA tienen controlador real (RmOcupacionController,
    // RmAsignacionesController, RmColaboradoresController,
    // TalentMatchingController, RmInicioController, RmReportesController).
    // mi-cuenta.html tambien es real (CuentaController), por eso se retiro
    // de esta lista.
    @GetMapping({
            "/resource-manager/notificaciones.html",
            "/resource-manager/asistente-ia.html"
    })
    public String resourceManager(HttpServletRequest request, Model model) {
        model.addAttribute("rolSlug", "resource-manager");
        return vista(request);
    }
}