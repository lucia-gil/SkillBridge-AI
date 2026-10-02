package com.skillbridge.ai.service;

import com.skillbridge.ai.model.ForoTopico;
import com.skillbridge.ai.model.Habilidad;
import com.skillbridge.ai.repository.ForoTopicoRepository;
import com.skillbridge.ai.repository.HabilidadRepository;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Categorización automática de hilos del foro.
 *
 * 1) Tópico: cada fila de foro_topicos trae sus palabras_clave; se cuenta
 *    cuántas aparecen como palabra/frase completa en el hilo (el título pesa
 *    3, el mensaje 1) y gana el tópico con más puntaje. Sin coincidencias,
 *    cae en "general".
 * 2) Etiquetas: se detectan las habilidades del catálogo (Java, MySQL,
 *    Docker...) que se mencionan en el hilo, así las etiquetas hablan el
 *    mismo idioma que los perfiles y el AI Talent Matching.
 *
 * Es un clasificador determinístico por palabras clave (sin LLM: el proyecto
 * no tiene uno integrado). Está aislado en este servicio a propósito: si más
 * adelante se conecta un modelo de IA, basta con reemplazar clasificar()
 * sin tocar ForoService, controladores ni vistas. Las mismas palabras clave
 * viajan a la vista para que la búsqueda inteligente use el mismo criterio.
 */
@Service
public class ForoClasificadorService {

    public static final String TOPICO_GENERAL = "general";
    private static final int MAX_ETIQUETAS = 6;
    private static final int MAX_LARGO_ETIQUETA = 30;

    private final ForoTopicoRepository topicoRepository;
    private final HabilidadRepository habilidadRepository;

    public ForoClasificadorService(ForoTopicoRepository topicoRepository, HabilidadRepository habilidadRepository) {
        this.topicoRepository = topicoRepository;
        this.habilidadRepository = habilidadRepository;
    }

    /** Minúsculas, sin tildes, solo letras/números/+/#, espacios simples. */
    public static String normalizar(String texto) {
        if (texto == null) return "";
        String sinTildes = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return sinTildes.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9+#ñ]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /** Devuelve el tópico más probable para el hilo; "general" si no hay coincidencias. */
    public ForoTopico clasificar(String titulo, String contenido) {
        List<ForoTopico> topicos = topicoRepository.findAllByOrderByOrdenAscNombreAsc();
        String t = " " + normalizar(titulo) + " ";
        String c = " " + normalizar(contenido) + " ";

        ForoTopico mejor = null;
        int mejorPuntaje = 0;
        ForoTopico general = null;
        for (ForoTopico topico : topicos) {
            if (TOPICO_GENERAL.equals(topico.getCodigo())) {
                general = topico;
                continue;
            }
            int puntaje = 0;
            for (String palabra : palabrasClave(topico)) {
                String frase = " " + palabra + " ";
                puntaje += 3 * contar(t, frase) + contar(c, frase);
            }
            if (puntaje > mejorPuntaje) {
                mejorPuntaje = puntaje;
                mejor = topico;
            }
        }
        return mejor != null ? mejor : general;
    }

    /** Habilidades del catálogo mencionadas en el hilo (nombres tal como están en el catálogo). */
    public List<String> sugerirEtiquetas(String titulo, String contenido) {
        String texto = " " + normalizar(titulo) + " " + normalizar(contenido) + " ";
        List<String> etiquetas = new ArrayList<>();
        for (Habilidad h : habilidadRepository.findAll()) {
            String nombre = normalizar(h.getNombre());
            if (!nombre.isEmpty() && texto.contains(" " + nombre + " ")) {
                etiquetas.add(h.getNombre());
                if (etiquetas.size() >= MAX_ETIQUETAS) break;
            }
        }
        return etiquetas;
    }

    public static List<String> palabrasClave(ForoTopico topico) {
        List<String> lista = new ArrayList<>();
        if (topico.getPalabrasClave() == null) return lista;
        for (String p : topico.getPalabrasClave().split(",")) {
            String n = normalizar(p);
            if (!n.isEmpty()) lista.add(n);
        }
        return lista;
    }

    private static int contar(String texto, String frase) {
        int n = 0;
        int desde = 0;
        while ((desde = texto.indexOf(frase, desde)) >= 0) {
            n++;
            desde += frase.length() - 1; // la frase empieza y termina en espacio: se solapan en 1
        }
        return n;
    }

    // ───────────── Etiquetas: texto del formulario <-> JSON de la columna ─────────────

    /** "MySQL, spring boot, mysql" -> ["MySQL", "spring boot"] (sin duplicados, máx. 6, máx. 30 caracteres). */
    public static List<String> parsearEtiquetasTexto(String csv) {
        Map<String, String> unicas = new LinkedHashMap<>();
        if (csv == null) return new ArrayList<>();
        for (String parte : csv.split(",")) {
            String limpia = parte.trim().replaceAll("\\s+", " ");
            if (limpia.isEmpty()) continue;
            if (limpia.length() > MAX_LARGO_ETIQUETA) limpia = limpia.substring(0, MAX_LARGO_ETIQUETA).trim();
            unicas.putIfAbsent(normalizar(limpia), limpia);
            if (unicas.size() >= MAX_ETIQUETAS) break;
        }
        return new ArrayList<>(unicas.values());
    }

    /** Lee la columna JSON etiquetas (arreglo de strings). Tolera NULL y JSON mal formado. */
    public static List<String> leerEtiquetasJson(String json) {
        List<String> lista = new ArrayList<>();
        if (json == null) return lista;
        String s = json.trim();
        if (!s.startsWith("[")) return lista;
        StringBuilder actual = null;
        boolean escape = false;
        for (int i = 1; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (actual == null) {
                if (ch == '"') actual = new StringBuilder();
                continue;
            }
            if (escape) {
                actual.append(ch == 'n' ? ' ' : ch);
                escape = false;
            } else if (ch == '\\') {
                escape = true;
            } else if (ch == '"') {
                String valor = actual.toString().trim();
                if (!valor.isEmpty()) lista.add(valor);
                actual = null;
            } else {
                actual.append(ch);
            }
        }
        return lista;
    }

    /** Escribe la lista como arreglo JSON válido para la columna etiquetas. */
    public static String escribirEtiquetasJson(List<String> etiquetas) {
        if (etiquetas == null || etiquetas.isEmpty()) return null;
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < etiquetas.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append('"').append(etiquetas.get(i).replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
        }
        return sb.append(']').toString();
    }
}
