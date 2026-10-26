package com.skillbridge.ai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillbridge.ai.model.Habilidad;
import com.skillbridge.ai.model.Proyecto;
import com.skillbridge.ai.model.ProyectoHabilidadRequerida;
import com.skillbridge.ai.repository.ProyectoHabilidadRequeridaRepository;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Regla única de "qué habilidades pide un proyecto", compartida por el AI
 * Talent Matching, el Catálogo de habilidades y los reportes del Resource
 * Manager, para que las tres pantallas cuenten lo mismo.
 *
 * <p>Fuente, en orden de prioridad:</p>
 * <ol>
 *   <li>{@code proyecto_habilidad_requerida}, con el nivel pedido.</li>
 *   <li>Si el proyecto no tiene filas ahí (es lo normal en proyectos creados
 *       desde la aplicación: el formulario del PM solo guarda la lista de
 *       tecnologías), se usa {@code proyectos.tecnologias} (JSON) cruzado por
 *       nombre con el catálogo, sin distinguir mayúsculas ni tildes, con
 *       nivel {@link #NIVEL_POR_DEFECTO}.</li>
 * </ol>
 */
@Service
public class DemandaHabilidadesService {

    /** Nivel que se asume cuando la habilidad sale de la lista de tecnologías. */
    public static final int NIVEL_POR_DEFECTO = 3;

    private final ProyectoHabilidadRequeridaRepository requeridaRepository;
    private final ObjectMapper objectMapper;

    public DemandaHabilidadesService(ProyectoHabilidadRequeridaRepository requeridaRepository, ObjectMapper objectMapper) {
        this.requeridaRepository = requeridaRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Habilidades que pide un proyecto.
     *
     * @param proyecto proyecto a evaluar
     * @param catalogo habilidades del catálogo (para cruzar las tecnologías por nombre)
     * @return habilidadId → nivel requerido (1-5); vacío si no pide ninguna
     */
    public Map<Long, Integer> requisitos(Proyecto proyecto, Collection<Habilidad> catalogo) {
        Map<Long, Integer> r = requeridaRepository.findById_ProyectoId(proyecto.getId()).stream()
                .collect(Collectors.toMap(x -> x.getId().getHabilidadId(), ProyectoHabilidadRequerida::getNivelRequerido,
                        Math::max, LinkedHashMap::new));
        return r.isEmpty() ? desdeTecnologias(proyecto, catalogo) : r;
    }

    /**
     * Igual que {@link #requisitos(Proyecto, Collection)} para varios proyectos,
     * leyendo {@code proyecto_habilidad_requerida} una sola vez.
     *
     * @return proyectoId → (habilidadId → nivel requerido)
     */
    public Map<Long, Map<Long, Integer>> requisitosPorProyecto(Collection<Proyecto> proyectos, Collection<Habilidad> catalogo) {
        Map<Long, Map<Long, Integer>> tabla = new HashMap<>();
        for (ProyectoHabilidadRequerida fila : requeridaRepository.findAll()) {
            tabla.computeIfAbsent(fila.getId().getProyectoId(), k -> new LinkedHashMap<>())
                    .merge(fila.getId().getHabilidadId(), fila.getNivelRequerido(), Math::max);
        }
        Map<Long, Map<Long, Integer>> out = new LinkedHashMap<>();
        for (Proyecto p : proyectos) {
            Map<Long, Integer> r = tabla.get(p.getId());
            out.put(p.getId(), r != null && !r.isEmpty() ? r : desdeTecnologias(p, catalogo));
        }
        return out;
    }

    /**
     * Cuántos de estos proyectos piden cada habilidad.
     *
     * @return habilidadId → cantidad de proyectos (solo habilidades pedidas al menos una vez)
     */
    public Map<Long, Long> proyectosPorHabilidad(Collection<Proyecto> proyectos, Collection<Habilidad> catalogo) {
        Map<Long, Long> conteo = new HashMap<>();
        requisitosPorProyecto(proyectos, catalogo).values()
                .forEach(req -> req.keySet().forEach(h -> conteo.merge(h, 1L, Long::sum)));
        return conteo;
    }

    private Map<Long, Integer> desdeTecnologias(Proyecto p, Collection<Habilidad> catalogo) {
        Map<Long, Integer> r = new LinkedHashMap<>();
        if (p.getTecnologias() == null || p.getTecnologias().isBlank()) return r;
        try {
            List<String> tecnologias = objectMapper.readValue(p.getTecnologias(), new TypeReference<List<String>>() {});
            for (String tecnologia : tecnologias) {
                String t = normalizar(tecnologia);
                catalogo.stream().filter(h -> normalizar(h.getNombre()).equals(t)).findFirst()
                        .ifPresent(h -> r.put(h.getId(), NIVEL_POR_DEFECTO));
            }
        } catch (Exception ignored) {
            // JSON mal formado: el proyecto se trata como si no pidiera habilidades.
        }
        return r;
    }

    static String normalizar(String v) {
        return Normalizer.normalize(Optional.ofNullable(v).orElse(""), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase().trim();
    }
}
