package com.skillbridge.ai.web;

import com.skillbridge.ai.service.ForoService;
import com.skillbridge.ai.service.ShellModelBuilder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Detalle de un hilo del Project Manager (project-manager/foro-hilo.html).
 * Toda la lógica vive en ForoHiloBaseController (compartida entre Colaborador y
 * Project Manager); esta clase solo fija el prefijo de rutas "/project-manager".
 */
@Controller
@RequestMapping("/project-manager")
public class ForoHiloPmController extends ForoHiloBaseController {

    public ForoHiloPmController(ForoService foroService, ShellModelBuilder shellModelBuilder) {
        super(foroService, shellModelBuilder);
    }

    @Override
    protected String base() {
        return "project-manager";
    }
}
