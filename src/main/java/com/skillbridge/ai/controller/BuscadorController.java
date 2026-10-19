package com.skillbridge.ai.controller;

import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.service.BuscadorService;
import com.skillbridge.ai.util.SesionKeys;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Endpoint JSON del buscador predictivo del topbar. Vive bajo /cuenta/**
 * a proposito: ese prefijo ya esta protegido por SesionInterceptor para
 * los 4 roles (exige sesion activa), asi que no hace falta tocar WebConfig.
 */
@RestController
public class BuscadorController {

    private final BuscadorService buscadorService;

    public BuscadorController(BuscadorService buscadorService) {
        this.buscadorService = buscadorService;
    }

    @GetMapping("/cuenta/buscar")
    public List<Map<String, Object>> buscar(@RequestParam(name = "q", defaultValue = "") String q, HttpSession session) {
        UsuarioSesion sesion = (UsuarioSesion) session.getAttribute(SesionKeys.USUARIO);
        return buscadorService.buscar(q, sesion);
    }
}
