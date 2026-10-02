package com.skillbridge.ai.service;

import com.skillbridge.ai.dto.TecnologiaOpcion;
import com.skillbridge.ai.model.Habilidad;
import com.skillbridge.ai.repository.HabilidadRepository;
import com.skillbridge.ai.util.OperacionInvalidaException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Stack tecnológico de los proyectos a partir del Catálogo de habilidades
 * que mantiene el Administrador (tabla habilidades + categorias_habilidad).
 *
 * Antes las tecnologías eran texto libre separado por comas, así que se
 * guardaba cualquier cosa ("my sql", "pyton"). Ahora:
 *  - la vista recibe el catálogo (catalogo()) para sugerir y autocompletar;
 *  - al guardar, normalizar() acepta solo tecnologías del catálogo y las
 *    guarda con su nombre oficial ("my sql" -> "MySQL"), sin duplicados.
 *
 * Las tecnologías que un proyecto YA tenía y no están en el catálogo
 * (datos anteriores a este cambio, ej. "Firebase") se respetan al editar:
 * se conservan tal cual mientras nadie las quite, para no romper proyectos
 * existentes. Si se quiere usar una nueva, el Admin la agrega al catálogo.
 */
@Service
public class TecnologiaCatalogoService {

    public static final int MAX_TECNOLOGIAS = 12;

    private final HabilidadRepository habilidadRepository;

    public TecnologiaCatalogoService(HabilidadRepository habilidadRepository) {
        this.habilidadRepository = habilidadRepository;
    }

    /** Catálogo para la vista, ordenado por nombre, con su categoría. */
    @Transactional(readOnly = true)
    public List<TecnologiaOpcion> catalogo() {
        return habilidadRepository.findAllConCategoriaOrderByNombre().stream()
                .map(h -> new TecnologiaOpcion(h.getNombre(),
                        h.getCategoria() != null ? h.getCategoria().getNombre() : "Otras"))
                .collect(Collectors.toList());
    }

    /**
     * Valida y normaliza el stack enviado desde el formulario.
     *
     * @param entrada    nombres tal como llegaron (ya separados).
     * @param existentes tecnologías que el proyecto ya tenía (edición) y se
     *                   pueden conservar aunque no estén en el catálogo.
     * @return nombres oficiales, sin duplicados, en el orden elegido.
     * @throws OperacionInvalidaException si alguna no está en el catálogo
     *         (con sugerencia "¿Quisiste decir...?") o si se excede el máximo.
     */
    @Transactional(readOnly = true)
    public List<String> normalizar(List<String> entrada, Collection<String> existentes) {
        if (entrada == null || entrada.isEmpty()) return List.of();

        Map<String, String> oficiales = new LinkedHashMap<>();
        for (Habilidad h : habilidadRepository.findAll()) {
            oficiales.putIfAbsent(clave(h.getNombre()), h.getNombre());
        }
        Map<String, String> heredadas = new LinkedHashMap<>();
        if (existentes != null) {
            for (String e : existentes) {
                if (e != null && !e.isBlank()) heredadas.putIfAbsent(clave(e), e.trim());
            }
        }

        Map<String, String> resultado = new LinkedHashMap<>();
        for (String crudo : entrada) {
            if (crudo == null || crudo.isBlank()) continue;
            String k = clave(crudo);
            String nombre = oficiales.get(k);
            if (nombre == null) nombre = heredadas.get(k);
            if (nombre == null) {
                String sugerencia = sugerir(k, oficiales);
                throw new OperacionInvalidaException("\"" + crudo.trim() + "\" no está en el catálogo de tecnologías."
                        + (sugerencia != null ? " ¿Quisiste decir " + sugerencia + "?" : "")
                        + " Si es una tecnología nueva, pide al Administrador que la agregue al catálogo.");
            }
            resultado.putIfAbsent(clave(nombre), nombre);
        }
        if (resultado.size() > MAX_TECNOLOGIAS) {
            throw new OperacionInvalidaException("Puedes indicar hasta " + MAX_TECNOLOGIAS + " tecnologías por proyecto.");
        }
        return new ArrayList<>(resultado.values());
    }

    /** "My SQL", "mysql", "MySQL " -> "mysql"; "Node.js" -> "nodejs"; "C#" -> "c#". */
    static String clave(String texto) {
        if (texto == null) return "";
        String sinTildes = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return sinTildes.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9+#]", "");
    }

    /** Nombre del catálogo más parecido (distancia de edición ≤ 2), o null. */
    private static String sugerir(String clave, Map<String, String> oficiales) {
        if (clave.length() < 3) return null;
        String mejor = null;
        int mejorDistancia = Integer.MAX_VALUE;
        for (Map.Entry<String, String> e : oficiales.entrySet()) {
            // Palabras cortas toleran 1 error; las de 6+ letras, 2 ("pyton" -> Python, "kubernets" -> Kubernetes).
            int tope = Math.min(clave.length(), e.getKey().length()) >= 6 ? 2 : 1;
            int d = distancia(clave, e.getKey());
            if (d <= tope && d < mejorDistancia) {
                mejorDistancia = d;
                mejor = e.getValue();
            }
        }
        return mejor;
    }

    private static int distancia(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int costo = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(prev[j] + 1, cur[j - 1] + 1), prev[j - 1] + costo);
            }
            int[] tmp = prev; prev = cur; cur = tmp;
        }
        return prev[b.length()];
    }
}
