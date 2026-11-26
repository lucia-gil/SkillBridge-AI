package com.skillbridge.ai.controller;

import com.skillbridge.ai.security.PoliticaContenidoFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Recibe los informes de infracción de la Content-Security-Policy que envía
 * el navegador (report-uri) y los deja en el log, para revisarlos mientras la
 * política está en modo informe. Público y sin CSRF (lo llama el navegador,
 * no un formulario); el cuerpo se recorta para no llenar el log.
 */
@RestController
public class InformeCspController {

    private static final Logger log = LoggerFactory.getLogger(InformeCspController.class);
    private static final int MAX_CARACTERES = 2000;

    @PostMapping(value = PoliticaContenidoFilter.RUTA_INFORME,
            consumes = {"application/csp-report", "application/json", "application/reports+json"})
    public ResponseEntity<Void> informe(@RequestBody(required = false) String cuerpo) {
        String texto = cuerpo == null ? "" : cuerpo.replaceAll("[\\r\\n]+", " ");
        if (texto.length() > MAX_CARACTERES) texto = texto.substring(0, MAX_CARACTERES) + "…";
        log.warn("CSP: infracción informada por el navegador: {}", texto);
        return ResponseEntity.noContent().build();
    }
}
