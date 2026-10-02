package com.skillbridge.ai.web;

import com.skillbridge.ai.service.ForoService;
import com.skillbridge.ai.service.ShellModelBuilder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Foros del Colaborador (colaborador/foros.html).
 * Toda la lógica vive en ForoListaBaseController (compartida entre Colaborador y
 * Project Manager); esta clase solo fija el prefijo de rutas "/colaborador".
 */
@Controller
@RequestMapping("/colaborador")
public class ForosController extends ForoListaBaseController {

    public ForosController(ForoService foroService, ShellModelBuilder shellModelBuilder) {
        super(foroService, shellModelBuilder);
    }

    @Override
    protected String base() {
        return "colaborador";
    }
}
