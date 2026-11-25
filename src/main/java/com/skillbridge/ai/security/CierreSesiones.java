package com.skillbridge.ai.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Cierra todas las sesiones abiertas de un usuario (en todos sus
 * navegadores). Las sesiones viven en MySQL (Spring Session JDBC) indexadas
 * por el nombre del principal, que es el correo (UsuarioSesion.getName()).
 */
@Component
public class CierreSesiones {

    private static final Logger log = LoggerFactory.getLogger(CierreSesiones.class);

    private final ObjectProvider<FindByIndexNameSessionRepository<? extends Session>> sesiones;

    public CierreSesiones(ObjectProvider<FindByIndexNameSessionRepository<? extends Session>> sesiones) {
        this.sesiones = sesiones;
    }

    /** Devuelve cuántas sesiones se cerraron. */
    public int cerrarTodas(String correo) {
        FindByIndexNameSessionRepository<? extends Session> repo = sesiones.getIfAvailable();
        if (repo == null || correo == null || correo.isBlank()) return 0;
        try {
            Map<String, ? extends Session> abiertas = repo.findByPrincipalName(correo);
            abiertas.keySet().forEach(repo::deleteById);
            return abiertas.size();
        } catch (RuntimeException e) {
            log.warn("No se pudieron cerrar las sesiones abiertas de un usuario: {}", e.getMessage());
            return 0;
        }
    }
}
