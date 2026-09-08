package com.skillbridge.ai.service;

import com.skillbridge.ai.model.ConfiguracionGlobal;
import com.skillbridge.ai.repository.ConfiguracionGlobalRepository;
import com.skillbridge.ai.util.OperacionInvalidaException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * Panel de Configuración (administrador/configuracion.html) real, sobre la
 * tabla clave-valor "configuracion_global" ya sembrada por
 * skillbridge_db_v4.sql (13 parámetros). Cada campo del formulario original
 * se reescribió para apuntar 1 a 1 a una clave real en vez de un toggle
 * decorativo sin persistencia.
 */
@Service
public class ConfiguracionService {

    private final ConfiguracionGlobalRepository repository;
    private final AuditoriaService auditoriaService;

    public ConfiguracionService(ConfiguracionGlobalRepository repository, AuditoriaService auditoriaService) {
        this.repository = repository;
        this.auditoriaService = auditoriaService;
    }

    public Map<String, ConfiguracionGlobal> obtenerMapa() {
        Map<String, ConfiguracionGlobal> mapa = new HashMap<>();
        repository.findAll().forEach(c -> mapa.put(c.getClave(), c));
        return mapa;
    }

    public String valor(Map<String, ConfiguracionGlobal> mapa, String clave, String porDefecto) {
        ConfiguracionGlobal c = mapa.get(clave);
        return (c != null && c.getValor() != null) ? c.getValor() : porDefecto;
    }

    public boolean valorBooleano(Map<String, ConfiguracionGlobal> mapa, String clave, boolean porDefecto) {
        String v = valor(mapa, clave, null);
        return v != null ? Boolean.parseBoolean(v) : porDefecto;
    }

    @Transactional
    public void actualizar(String clave, String nuevoValor, Long actorUsuarioId, Long actorPerfilId) {
        ConfiguracionGlobal c = repository.findById(clave)
                .orElseThrow(() -> new OperacionInvalidaException("Parámetro de configuración desconocido: " + clave));
        String anterior = c.getValor();
        c.setValor(nuevoValor);
        c.setActualizadoPorId(actorPerfilId);
        repository.save(c);
        auditoriaService.registrar(actorUsuarioId, "CONFIGURACION_ACTUALIZADA", "configuracion_global", null,
                AuditoriaService.json(clave, anterior), AuditoriaService.json(clave, nuevoValor), clave);
    }
}
