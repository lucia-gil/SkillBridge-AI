package com.skillbridge.ai.controller;

import com.skillbridge.ai.dto.PaginaHabilidadesResponse;
import com.skillbridge.ai.dto.HabilidadFila;
import com.skillbridge.ai.service.HabilidadService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API interna paginada para autocompletado y tabla del catálogo administrativo. */
@RestController
@RequestMapping(value = "/administrador/api/habilidades", produces = "application/json;charset=UTF-8")
@PreAuthorize("hasAuthority('administrador')")
public class AdminHabilidadesApiController {

    private final HabilidadService habilidadService;

    public AdminHabilidadesApiController(HabilidadService habilidadService) {
        this.habilidadService = habilidadService;
    }

    @GetMapping
    public ResponseEntity<PaginaHabilidadesResponse> buscar(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(required = false) Long categoriaId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        if (categoriaId != null && categoriaId <= 0) return ResponseEntity.badRequest().build();
        int paginaSegura = Math.max(0, page);
        int tamanoSeguro = Math.min(50, Math.max(1, size));
        Page<HabilidadFila> resultado = habilidadService.buscar(q, categoriaId,
                PageRequest.of(paginaSegura, tamanoSeguro, Sort.by("nombre").ascending()));
        return ResponseEntity.ok(new PaginaHabilidadesResponse(resultado.getContent(), resultado.getNumber(), resultado.getSize(),
                resultado.getTotalElements(), resultado.getTotalPages(), resultado.isFirst(), resultado.isLast()));
    }
}
