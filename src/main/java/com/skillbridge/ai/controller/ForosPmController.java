package com.skillbridge.ai.controller;

import com.skillbridge.ai.service.ForoService;
import com.skillbridge.ai.service.ShellModelBuilder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Foros del Project Manager (project-manager/foros.html).
 * Toda la lógica vive en ForoListaBaseController (compartida entre Colaborador y
 * Project Manager); esta clase solo fija el prefijo de rutas "/project-manager".
 */
@Controller
@RequestMapping("/project-manager")
public class ForosPmController extends ForoListaBaseController {

    public ForosPmController(ForoService foroService, ShellModelBuilder shellModelBuilder) {
        super(foroService, shellModelBuilder);
    }

    @Override
    protected String base() {
        return "project-manager";
    }
}
