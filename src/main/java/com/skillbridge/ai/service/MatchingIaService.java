package com.skillbridge.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillbridge.ai.dto.MatchingCandidato;
import com.skillbridge.ai.dto.MatchingHabilidad;
import com.skillbridge.ai.model.Habilidad;
import com.skillbridge.ai.model.Proyecto;
import com.skillbridge.ai.model.RecomendacionIaLog;
import com.skillbridge.ai.repository.AsignacionRepository;
import com.skillbridge.ai.repository.HabilidadRepository;
import com.skillbridge.ai.repository.ProyectoRepository;
import com.skillbridge.ai.repository.RecomendacionIaLogRepository;
import com.skillbridge.ai.util.OperacionInvalidaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI Talent Matching con IA (RF05), arquitectura "híbrida y explicable":
 *  1. (opcional) la IA convierte una petición en lenguaje natural en filtros
 *     (proyecto, habilidades, cantidad, dedicación); si la IA no responde,
 *     un intérprete por palabras clave hace lo mismo;
 *  2. Java calcula el ranking con los datos reales (TalentMatchingService),
 *     así la IA nunca inventa candidatos ni cambia el puntaje;
 *  3. la IA explica en lenguaje humano por qué recomienda a cada persona;
 *     si falla, se usa la explicación calculada en Java;
 *  4. cada recomendación queda en recomendaciones_ia_log (fue_asignado se
 *     marca al asignar a esa persona, ver ProyectoService.asignarColaborador).
 */
@Service
public class MatchingIaService {

    private static final Logger log = LoggerFactory.getLogger(MatchingIaService.class);
    public static final int DEDICACION_POR_DEFECTO = 50;
    private static final int NIVEL_POR_DEFECTO = 3;

    public record HabilidadIa(String nombre, boolean cumple, String requerido, String tiene) {}

    public record CandidatoIa(Long perfilId, String nombre, String iniciales, String cargo, int score,
                              int scoreHabilidades, int scoreExperiencia, int scoreDisponibilidad,
                              int experienciaAnios, int disponibilidad, int cargaActual, int cargaProyectada,
                              List<HabilidadIa> habilidades, String explicacion, boolean recomendado) {}

    public record Interpretacion(List<String> habilidades, Integer cantidad, Integer dedicacion, boolean porIa) {}

    public record Analisis(Long proyectoId, String proyecto, long vacantes, int dedicacion, String resumen,
                           boolean ia, String aviso, List<CandidatoIa> candidatos, Interpretacion interpretacion,
                           String brecha) {}

    /** Desde este % de coincidencia técnica una persona puede figurar como "Recomendada". */
    static final int HABILIDADES_MINIMAS_RECOMENDAR = 50;

    private record ProyectoOpcion(Long id, String nombre, long vacantes) {}

    private final TalentMatchingService talentMatchingService;
    private final AsistenteIaService asistenteIaService;
    private final ProyectoRepository proyectoRepository;
    private final AsignacionRepository asignacionRepository;
    private final HabilidadRepository habilidadRepository;
    private final RecomendacionIaLogRepository logRepository;
    private final ObjectMapper objectMapper;
    private final DemandaHabilidadesService demandaService;

    public MatchingIaService(TalentMatchingService talentMatchingService, AsistenteIaService asistenteIaService,
                             ProyectoRepository proyectoRepository, AsignacionRepository asignacionRepository,
                             HabilidadRepository habilidadRepository, RecomendacionIaLogRepository logRepository,
                             ObjectMapper objectMapper, DemandaHabilidadesService demandaService) {
        this.talentMatchingService = talentMatchingService;
        this.asistenteIaService = asistenteIaService;
        this.proyectoRepository = proyectoRepository;
        this.asignacionRepository = asignacionRepository;
        this.habilidadRepository = habilidadRepository;
        this.logRepository = logRepository;
        this.objectMapper = objectMapper;
        this.demandaService = demandaService;
    }

    private Map<Long, Integer> demandaRequisitos(Proyecto p) {
        return demandaService.requisitos(p, habilidadRepository.findAll());
    }

    // ───────────────────────── Analizar un proyecto ─────────────────────────

    /** "Analizar con IA" desde la vista del proyecto: ranking con las habilidades del proyecto + explicación. */
    @Transactional
    public Analisis analizar(Long proyectoId, Long solicitantePerfilId, int dedicacion) {
        return analizar(proyectoId, solicitantePerfilId, dedicacion, null, 1, 5, null, null);
    }

    // ───────────────────────── Petición en lenguaje natural ─────────────────────────

    /**
     * "Necesito 2 backend con Spring y Docker para Portal Andes, al 50%":
     * interpreta, calcula y explica. pmPerfilId != null limita los proyectos
     * a los que lidera ese PM; null (Resource Manager) permite todos.
     */
    @Transactional
    public Analisis consultar(String texto, Long solicitantePerfilId, Long pmPerfilId) {
        String peticion = Optional.ofNullable(texto).orElse("").trim();
        if (peticion.length() < 4) throw new OperacionInvalidaException("Escribe qué perfil necesitas, por ejemplo: \"2 backend con Spring Boot y Docker para Portal Andes al 50%\".");
        if (peticion.length() > 500) peticion = peticion.substring(0, 500);

        List<ProyectoOpcion> proyectos = proyectosDisponibles(pmPerfilId);
        if (proyectos.isEmpty()) throw new OperacionInvalidaException("No hay proyectos con vacantes abiertas para buscar talento.");
        List<Habilidad> catalogo = habilidadRepository.findAll();

        // 1) Intérprete por palabras clave (siempre funciona) …
        Long proyectoId = proyectoPorTexto(peticion, proyectos);
        Map<Long, Integer> requisitos = habilidadesPorTexto(peticion, catalogo);
        Integer dedicacion = dedicacionPorTexto(peticion);
        Integer cantidad = cantidadPorTexto(peticion);
        boolean porIa = false;

        // 2) … y la IA, que entiende sinónimos ("backend", "nube") y frases libres.
        AsistenteIaService.Resultado r = asistenteIaService.consultar(promptInterpretar(proyectos, catalogo), peticion, 400);
        JsonNode json = r.ok() ? leerJson(r.texto()) : null;
        if (json != null) {
            porIa = true;
            long id = json.path("proyectoId").asLong(0);
            if (id > 0 && proyectos.stream().anyMatch(p -> p.id() == id)) proyectoId = id;
            Map<String, Habilidad> porNombre = new HashMap<>();
            catalogo.forEach(h -> porNombre.put(normalizar(h.getNombre()), h));
            for (JsonNode h : json.path("habilidades")) {
                Habilidad hab = porNombre.get(normalizar(h.path("nombre").asText("")));
                if (hab != null) requisitos.merge(hab.getId(), nivelValido(h.path("nivel").asInt(NIVEL_POR_DEFECTO)), Math::max);
            }
            if (dedicacion == null && json.path("dedicacion").asInt(0) > 0) dedicacion = json.path("dedicacion").asInt();
            if (cantidad == null && json.path("cantidad").asInt(0) > 0) cantidad = json.path("cantidad").asInt();
        }

        if (proyectoId == null && proyectos.size() == 1) proyectoId = proyectos.get(0).id();
        if (proyectoId == null) {
            throw new OperacionInvalidaException("¿Para qué proyecto? Menciona uno de estos: "
                    + String.join(", ", proyectos.stream().map(ProyectoOpcion::nombre).toList()) + ".");
        }
        int ded = dedicacion != null && dedicacion >= 1 && dedicacion <= 100 ? dedicacion : DEDICACION_POR_DEFECTO;
        long vacantes = talentMatchingService.vacantesDe(proyectoId);
        int cant = (int) Math.max(1, Math.min(cantidad != null ? cantidad : 1, Math.max(1, vacantes)));
        int limite = Math.min(6, Math.max(4, cant + 2));

        Map<Long, String> nombres = new HashMap<>();
        catalogo.forEach(h -> nombres.put(h.getId(), h.getNombre()));
        Interpretacion interpretacion = new Interpretacion(
                requisitos.keySet().stream().map(nombres::get).sorted().toList(), cant, ded, porIa);
        return analizar(proyectoId, solicitantePerfilId, ded, requisitos, cant, limite, peticion, interpretacion);
    }

    /** Ejemplos de petición con proyectos y habilidades reales (chips del panel). */
    @Transactional(readOnly = true)
    public List<String> ejemplos(Long pmPerfilId) {
        List<ProyectoOpcion> proyectos = proyectosDisponibles(pmPerfilId);
        List<String> out = new ArrayList<>();
        if (proyectos.isEmpty()) return out;
        ProyectoOpcion p0 = proyectos.get(0);
        out.add("¿Quién puede sumarse a " + p0.nombre() + " al 50%?");
        Map<Long, Integer> req = proyectoRepository.findById(p0.id())
                .map(p -> demandaRequisitos(p)).orElse(Map.of());
        List<String> nombres = habilidadRepository.findAllById(req.keySet()).stream().map(Habilidad::getNombre).limit(2).toList();
        if (!nombres.isEmpty()) {
            out.add("Necesito " + (p0.vacantes() > 1 ? "2 personas" : "alguien") + " con " + String.join(" y ", nombres)
                    + " avanzado para " + p0.nombre());
        }
        if (proyectos.size() > 1) out.add("Busco un perfil con disponibilidad para " + proyectos.get(1).nombre() + " al 30%");
        return out;
    }

    /** Recomendaciones únicas (proyecto + persona) y cuántas terminaron en asignación. */
    @Transactional(readOnly = true)
    public long[] acierto() {
        return new long[]{logRepository.contarRecomendacionesUnicas(), logRepository.contarAciertos()};
    }

    // ───────────────────────── núcleo ─────────────────────────

    private Analisis analizar(Long proyectoId, Long solicitantePerfilId, int dedicacion, Map<Long, Integer> requisitos,
                              int cantidad, int limite, String peticion, Interpretacion interpretacion) {
        Proyecto p = proyectoRepository.findById(proyectoId).orElseThrow(() -> new com.skillbridge.ai.util.RecursoNoEncontradoException("El proyecto no existe."));
        List<MatchingCandidato> ranking = talentMatchingService.rankingParaIa(proyectoId, dedicacion, requisitos, limite);
        long vacantes = talentMatchingService.vacantesDe(proyectoId);
        if (ranking.isEmpty()) {
            return new Analisis(proyectoId, p.getNombre(), vacantes, dedicacion,
                    "No encontré personas con capacidad libre para " + dedicacion + "% de dedicación en \"" + p.getNombre() + "\".",
                    false, null, List.of(), interpretacion, null);
        }

        Map<Long, String> explicaciones = new HashMap<>();
        String resumen = null;
        String aviso = null;
        AsistenteIaService.Resultado r = asistenteIaService.consultar(PROMPT_EXPLICAR, contexto(p, ranking, dedicacion, peticion), 1400);
        JsonNode json = r.ok() ? leerJson(r.texto()) : null;
        if (json != null) {
            resumen = limpiar(json.path("resumen").asText(null), 400);
            for (JsonNode c : json.path("candidatos")) {
                String e = limpiar(c.path("explicacion").asText(null), 500);
                if (e != null) explicaciones.put(c.path("id").asLong(), e);
            }
        } else {
            aviso = r.ok() ? "La IA respondió en un formato inesperado; se muestran las explicaciones calculadas por el sistema."
                    : r.texto() + " Se muestran las explicaciones calculadas por el sistema.";
        }
        boolean ia = json != null && !explicaciones.isEmpty();
        if (resumen == null) resumen = resumenJava(p, ranking, dedicacion);

        // Brecha: se piden habilidades y nadie libre llega al mínimo. Se avisa y no se recomienda a nadie.
        boolean conRequisitos = ranking.stream().anyMatch(c -> !c.getHabilidades().isEmpty());
        String brecha = null;
        if (conRequisitos && ranking.stream().noneMatch(MatchingIaService::cubreHabilidades)) {
            List<String> faltan = ranking.get(0).getHabilidades().stream().map(MatchingHabilidad::nombre).distinct().limit(4).toList();
            brecha = "Nadie con capacidad libre cumple " + (faltan.isEmpty() ? "las habilidades pedidas" : String.join(", ", faltan))
                    + " al nivel pedido. Te muestro a las personas más cercanas por experiencia y disponibilidad: "
                    + "considera capacitarlas con un curso, bajar el nivel pedido o buscar fuera del equipo.";
        }
        int recomendados = 0;
        List<CandidatoIa> candidatos = new ArrayList<>();
        for (int i = 0; i < ranking.size(); i++) {
            MatchingCandidato c = ranking.get(i);
            String explicacion = explicaciones.getOrDefault(c.getPerfilId(), c.getExplicacion());
            candidatos.add(new CandidatoIa(c.getPerfilId(), c.getNombre(), c.getIniciales(), c.getCargo(), c.getScoreTotal(),
                    c.getScoreHabilidades(), c.getScoreExperiencia(), c.getScoreDisponibilidad(), c.getExperienciaAnios(),
                    c.getDisponibilidad(), c.getCargaActual(), c.getCargaProyectada(),
                    c.getHabilidades().stream().map(h -> new HabilidadIa(h.nombre(), h.nivelCandidato() >= h.nivelRequerido(),
                            h.requeridoLabel(), h.candidatoLabel())).toList(),
                    explicacion, recomendar(c, recomendados, cantidad)));
            if (recomendar(c, recomendados, cantidad)) recomendados++;
            if (solicitantePerfilId != null) {
                logRepository.save(new RecomendacionIaLog(proyectoId, c.getPerfilId(), solicitantePerfilId, c.getScoreTotal(), explicacion));
            }
        }
        return new Analisis(proyectoId, p.getNombre(), vacantes, dedicacion, resumen, ia, aviso, candidatos, interpretacion, brecha);
    }

    /** Cumple lo técnico: el proyecto no pide habilidades o la coincidencia llega al mínimo. */
    static boolean cubreHabilidades(MatchingCandidato c) {
        return c.getHabilidades().isEmpty() || c.getScoreHabilidades() >= HABILIDADES_MINIMAS_RECOMENDAR;
    }

    /** "Recomendado" solo para los primeros N que además cumplen lo técnico. */
    private static boolean recomendar(MatchingCandidato c, int yaRecomendados, int cantidad) {
        return yaRecomendados < cantidad && cubreHabilidades(c);
    }

    private static final String PROMPT_EXPLICAR =
            "Eres el motor de explicaciones de AI Talent Matching de SkillBridge AI. "
            + "Recibes un proyecto y candidatos YA ORDENADOS por un puntaje que calculó el sistema con datos reales "
            + "(habilidades, experiencia y disponibilidad). No cambies el orden ni los puntajes y no inventes datos. "
            + "Responde SOLO con JSON válido, sin texto adicional ni bloques de código, con esta forma: "
            + "{\"resumen\":\"...\",\"candidatos\":[{\"id\":123,\"explicacion\":\"...\"}]}. "
            + "\"resumen\": máximo 2 oraciones que comparen a los mejores y digan a quién conviene priorizar y por qué. "
            + "\"explicacion\": 1 o 2 oraciones (máximo 45 palabras) por candidato, en español, con sus fortalezas concretas "
            + "para ESTE proyecto (habilidades que cumple, experiencia, disponibilidad) y, si aplica, una advertencia "
            + "(habilidad con nivel menor al pedido, carga alta tras asignarlo). Si su coincidencia de habilidades es menor a 50, "
            + "dilo claramente y no lo presentes como buena opción técnica; si nadie llega a 50, el resumen debe decir que "
            + "hay una brecha de habilidades en el equipo. Sin saludos.";

    private String contexto(Proyecto p, List<MatchingCandidato> ranking, int dedicacion, String peticion) {
        StringBuilder sb = new StringBuilder();
        if (peticion != null) sb.append("Petición del usuario: ").append(peticion).append('\n');
        sb.append("Proyecto: ").append(p.getNombre()).append('\n');
        if (p.getDescripcion() != null) sb.append("Descripción: ").append(recortar(p.getDescripcion(), 500)).append('\n');
        sb.append("Dedicación pedida: ").append(dedicacion).append("%\n\nCandidatos (orden del sistema):\n");
        for (MatchingCandidato c : ranking) {
            sb.append("- id ").append(c.getPerfilId()).append(" · ").append(c.getNombre()).append(" (").append(c.getCargo()).append(")")
                    .append(" · puntaje ").append(c.getScoreTotal()).append(" (habilidades ").append(c.getScoreHabilidades())
                    .append(", experiencia ").append(c.getScoreExperiencia()).append(", disponibilidad ").append(c.getScoreDisponibilidad()).append(")")
                    .append(" · ").append(c.getExperienciaAnios()).append(" año(s) de experiencia · carga ").append(c.getCargaActual())
                    .append("% → ").append(c.getCargaProyectada()).append("%");
            if (!c.getHabilidades().isEmpty()) {
                sb.append(" · habilidades: ");
                List<String> hs = new ArrayList<>();
                for (MatchingHabilidad h : c.getHabilidades()) {
                    hs.add(h.nombre() + " pide " + h.requeridoLabel() + ", tiene " + h.candidatoLabel());
                }
                sb.append(String.join("; ", hs));
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private static String resumenJava(Proyecto p, List<MatchingCandidato> ranking, int dedicacion) {
        MatchingCandidato top = ranking.get(0);
        return top.getNombre() + " encabeza el ranking para \"" + p.getNombre() + "\" con " + top.getScoreTotal()
                + " puntos (habilidades " + top.getScoreHabilidades() + "%, disponibilidad " + top.getScoreDisponibilidad()
                + "%). Con " + dedicacion + "% de dedicación su carga quedaría en " + top.getCargaProyectada() + "%.";
    }

    // ───────────────────────── interpretación ─────────────────────────

    private List<ProyectoOpcion> proyectosDisponibles(Long pmPerfilId) {
        if (pmPerfilId != null) {
            return talentMatchingService.proyectosDelPm(pmPerfilId).stream()
                    .map(o -> new ProyectoOpcion(o.id(), o.nombre(), o.vacantes())).toList();
        }
        return proyectoRepository.findAll().stream()
                .filter(ProyectoService::admiteNuevosIntegrantes)
                .map(p -> new ProyectoOpcion(p.getId(), p.getNombre(), talentMatchingService.vacantesDe(p.getId())))
                .filter(o -> o.vacantes() > 0)
                .sorted(Comparator.comparing(ProyectoOpcion::nombre))
                .toList();
    }

    private static String promptInterpretar(List<ProyectoOpcion> proyectos, List<Habilidad> catalogo) {
        StringBuilder sb = new StringBuilder("Convierte la petición de un gestor de talento en filtros de búsqueda. ")
                .append("Proyectos con vacantes (id: nombre): ");
        proyectos.forEach(p -> sb.append(p.id()).append(": ").append(p.nombre()).append("; "));
        sb.append("Habilidades del catálogo: ");
        sb.append(String.join(", ", catalogo.stream().map(Habilidad::getNombre).sorted().toList()));
        sb.append(". Traduce roles a habilidades del catálogo (\"backend\" puede ser Java, Spring Boot o Node.js; ")
                .append("\"nube\" puede ser AWS, Docker o Kubernetes), usa SOLO nombres exactos del catálogo y no agregues habilidades ")
                .append("que la petición no pida. Niveles: 1-2 básico, 3 intermedio, 4 avanzado, 5 experto (3 si no se indica). ")
                .append("Responde SOLO con JSON válido, sin texto adicional: ")
                .append("{\"proyectoId\":id o null,\"habilidades\":[{\"nombre\":\"...\",\"nivel\":3}],\"cantidad\":número o null,\"dedicacion\":número o null}");
        return sb.toString();
    }

    private static Long proyectoPorTexto(String texto, List<ProyectoOpcion> proyectos) {
        String t = " " + normalizar(texto) + " ";
        ProyectoOpcion mejor = null;
        int mejorPuntos = 0;
        for (ProyectoOpcion p : proyectos) {
            String n = normalizar(p.nombre());
            if (t.contains(" " + n + " ") || t.contains(n)) return p.id();
            int puntos = 0;
            for (String palabra : n.split("\\s+")) {
                if (palabra.length() >= 4 && t.contains(" " + palabra + " ")) puntos++;
            }
            if (puntos > mejorPuntos) { mejorPuntos = puntos; mejor = p; }
        }
        return mejor != null ? mejor.id() : null;
    }

    private static Map<Long, Integer> habilidadesPorTexto(String texto, List<Habilidad> catalogo) {
        String t = " " + normalizar(texto).replaceAll("[^a-z0-9.#+ ]", " ") + " ";
        Map<Long, Integer> out = new LinkedHashMap<>();
        for (Habilidad h : catalogo) {
            String n = normalizar(h.getNombre());
            if (n.length() >= 2 && t.contains(" " + n + " ")) out.put(h.getId(), NIVEL_POR_DEFECTO);
        }
        return out;
    }

    private static Integer dedicacionPorTexto(String texto) {
        Matcher m = Pattern.compile("(\\d{1,3})\\s*%").matcher(texto);
        return m.find() ? Integer.parseInt(m.group(1)) : null;
    }

    private static final Map<String, Integer> NUMEROS = Map.of("un", 1, "una", 1, "uno", 1, "dos", 2, "tres", 3, "cuatro", 4, "cinco", 5);

    private static Integer cantidadPorTexto(String texto) {
        String t = normalizar(texto);
        Matcher m = Pattern.compile("\\b(\\d{1,2})\\b(?!\\s*%)").matcher(t);
        while (m.find()) {
            int n = Integer.parseInt(m.group(1));
            if (n >= 1 && n <= 10) return n;
        }
        for (String palabra : t.split("[^a-z]+")) {
            Integer n = NUMEROS.get(palabra);
            if (n != null && n > 1) return n;
        }
        return null;
    }

    // ───────────────────────── utilidades ─────────────────────────

    /** Lee el JSON aunque venga envuelto en ```json … ``` o con texto alrededor. */
    private JsonNode leerJson(String texto) {
        if (texto == null) return null;
        int a = texto.indexOf('{'), b = texto.lastIndexOf('}');
        if (a < 0 || b <= a) return null;
        try {
            return objectMapper.readTree(texto.substring(a, b + 1));
        } catch (Exception ex) {
            log.warn("AI Talent Matching: la IA no devolvió JSON válido: {}", recortar(texto, 300));
            return null;
        }
    }

    private static int nivelValido(int n) { return Math.max(1, Math.min(5, n)); }

    private static String limpiar(String s, int max) {
        if (s == null || s.isBlank() || "null".equals(s)) return null;
        return recortar(s.trim(), max);
    }

    private static String recortar(String s, int max) {
        return s.length() > max ? s.substring(0, max).trim() + "…" : s;
    }

    private static String normalizar(String v) {
        return Normalizer.normalize(Optional.ofNullable(v).orElse(""), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase().trim();
    }
}
