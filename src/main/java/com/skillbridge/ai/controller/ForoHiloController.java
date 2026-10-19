package com.skillbridge.ai.controller;

import com.skillbridge.ai.service.ForoService;
import com.skillbridge.ai.service.ShellModelBuilder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Detalle de un hilo del Colaborador (colaborador/foro-hilo.html).
 * Toda la lógica vive en ForoHiloBaseController (compartida entre Colaborador y
 * Project Manager); esta clase solo fija el prefijo de rutas "/colaborador".
 */
@Controller
@RequestMapping("/colaborador")
public class ForoHiloController extends ForoHiloBaseController {

    public ForoHiloController(ForoService foroService, ShellModelBuilder shellModelBuilder) {
        super(foroService, shellModelBuilder);
    }

    @Override
    protected String base() {
        return "colaborador";
    }
}
