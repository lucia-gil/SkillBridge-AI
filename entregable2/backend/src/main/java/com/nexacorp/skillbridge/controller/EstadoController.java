package com.nexacorp.skillbridge.controller;

import com.nexacorp.skillbridge.repository.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Endpoint de verificación para este entregable: si /api/estado responde con
 * los conteos reales, la app efectivamente está leyendo desde la base de
 * datos desplegada en la nube (no datos hardcodeados). Complementa a
 * /actuator/health, que expone el estado de la conexión JDBC.
 */
@RestController
public class EstadoController {

    private final UsuarioRepository usuarioRepository;
    private final PerfilRepository perfilRepository;
    private final HabilidadRepository habilidadRepository;
    private final ProyectoRepository proyectoRepository;
    private final ConfiguracionGlobalRepository configuracionGlobalRepository;

    public EstadoController(UsuarioRepository usuarioRepository,
                             PerfilRepository perfilRepository,
                             HabilidadRepository habilidadRepository,
                             ProyectoRepository proyectoRepository,
                             ConfiguracionGlobalRepository configuracionGlobalRepository) {
        this.usuarioRepository = usuarioRepository;
        this.perfilRepository = perfilRepository;
        this.habilidadRepository = habilidadRepository;
        this.proyectoRepository = proyectoRepository;
        this.configuracionGlobalRepository = configuracionGlobalRepository;
    }

    @GetMapping("/api/estado")
    public Map<String, Object> estado() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("app", "SkillBridge AI");
        body.put("entregable", "2 - Base de Datos + Spring en Cloud");
        body.put("usuarios", usuarioRepository.count());
        body.put("perfiles", perfilRepository.count());
        body.put("habilidades", habilidadRepository.count());
        body.put("proyectos", proyectoRepository.count());
        body.put("parametrosConfiguracion", configuracionGlobalRepository.count());
        body.put("mensaje", "Conexión a la base de datos en la nube OK");
        return body;
    }
}
