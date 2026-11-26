package com.skillbridge.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.model.Habilidad;
import com.skillbridge.ai.model.Perfil;
import com.skillbridge.ai.model.PerfilHabilidad;
import com.skillbridge.ai.model.Proyecto;
import com.skillbridge.ai.repository.HabilidadRepository;
import com.skillbridge.ai.repository.PerfilHabilidadRepository;
import com.skillbridge.ai.repository.PerfilRepository;
import com.skillbridge.ai.util.OperacionInvalidaException;
import com.skillbridge.ai.util.Roles;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Buscador inteligente del topbar (Ctrl+K): "¿quién sabe Docker y está
 * libre?", "proyectos de nube atrasados". Misma arquitectura que AI Talent
 * Matching: la IA solo convierte la pregunta en filtros (JSON) y Java busca
 * en la base respetando lo que cada rol puede ver (BuscadorService). Si la
 * IA no responde, un intérprete por palabras clave arma los filtros.
 * Solo se llama a la IA cuando el usuario lo pide (Enter), para cuidar la cuota.
 */
@Service
public class BuscadorIaService {

    private static final int MAX = 8;
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Filtros que salen de la pregunta. */
    public record Filtros(String buscar, List<String> habilidades, Integer nivelMin, Integer libreMin,
                          boolean enBanca, List<String> estados, boolean atrasados, boolean conVacantes, String texto) {}

    public record Respuesta(String interpretacion, List<String> chips, boolean porIa, String aviso,
                            List<Map<String, Object>> resultados) {}

    /** Palabras comunes que la IA entiende y el plan B traduce a habilidades del catálogo. */
    private static final Map<String, List<String>> SINONIMOS = Map.of(
            "nube", List.of("AWS", "Docker", "Kubernetes", "Terraform"),
            "cloud", List.of("AWS", "Docker", "Kubernetes", "Terraform"),
            "devops", List.of("Docker", "Kubernetes", "Terraform", "AWS"),
            "frontend", List.of("React", "Angular", "JavaScript", "TypeScript"),
            "backend", List.of("Java", "Spring Boot", "Node.js", "Python"),
            "movil", List.of("Kotlin", "Flutter", "React Native"),
            "datos", List.of("Python", "Power BI", "SQL", "MySQL"),
            "diseno", List.of("Figma", "Adobe XD"));

    private final BuscadorService buscadorService;
    private final AsistenteIaService asistenteIaService;
    private final ProyectoService proyectoService;
    private final DemandaHabilidadesService demandaService;
    private final TalentMatchingService talentMatchingService;
    private final PerfilRepository perfilRepository;
    private final PerfilHabilidadRepository perfilHabilidadRepository;
    private final HabilidadRepository habilidadRepository;
    private final ObjectMapper objectMapper;
    private final BuscadorColaboradorService buscadorColaborador;

    public BuscadorIaService(BuscadorService buscadorService, AsistenteIaService asistenteIaService,
                             ProyectoService proyectoService, DemandaHabilidadesService demandaService,
                             TalentMatchingService talentMatchingService, PerfilRepository perfilRepository,
                             PerfilHabilidadRepository perfilHabilidadRepository, HabilidadRepository habilidadRepository,
                             ObjectMapper objectMapper, BuscadorColaboradorService buscadorColaborador) {
        this.buscadorService = buscadorService;
        this.asistenteIaService = asistenteIaService;
        this.proyectoService = proyectoService;
        this.demandaService = demandaService;
        this.talentMatchingService = talentMatchingService;
        this.perfilRepository = perfilRepository;
        this.perfilHabilidadRepository = perfilHabilidadRepository;
        this.habilidadRepository = habilidadRepository;
        this.objectMapper = objectMapper;
        this.buscadorColaborador = buscadorColaborador;
    }

    @Transactional(readOnly = true)
    public Respuesta preguntar(String pregunta, UsuarioSesion s) {
        String q = Optional.ofNullable(pregunta).orElse("").trim();
        if (q.length() < 3) throw new OperacionInvalidaException("Escribe una pregunta un poco más larga.");
        if (q.length() > 300) q = q.substring(0, 300);
        String rol = s.getRolEfectivo();
        boolean vePersonas = BuscadorService.vePersonas(rol);
        List<Habilidad> catalogo = habilidadRepository.findAll();

        // 1) Filtros: primero la IA; si no responde, palabras clave.
        Filtros f = null;
        String aviso = null;
        boolean colaborador = BuscadorColaboradorService.aplica(s);
        AsistenteIaService.Resultado r = asistenteIaService.consultar(prompt(catalogo, vePersonas, colaborador), q, 350);
        JsonNode json = r.ok() ? leerJson(r.texto()) : null;

        // Vista Colaborador: entregables, foros, eventos y cursos (lo de sus pantallas).
        if (colaborador) {
            String tipo = json != null && BuscadorColaboradorService.TIPOS.contains(json.path("buscar").asText()) ? json.path("buscar").asText() : null;
            boolean tipoPorIa = tipo != null;
            if (tipo == null) tipo = BuscadorColaboradorService.tipoPorPalabras(q);
            if (tipo != null) {
                List<String> estados = new ArrayList<>();
                if (tipoPorIa) {
                    for (JsonNode e : json.path("estadoEntrega")) {
                        if (BuscadorColaboradorService.ESTADOS_ENTREGA.contains(e.asText())) estados.add(e.asText());
                    }
                } else {
                    estados.addAll(BuscadorColaboradorService.estadosPorPalabras(q));
                }
                String texto = tipoPorIa && json.path("texto").isTextual() && !json.path("texto").asText().isBlank()
                        ? json.path("texto").asText().trim() : null;
                return buscadorColaborador.preguntar(tipo, estados, texto, tipoPorIa, s);
            }
        }

        if (json != null) f = desdeJson(json, catalogo);
        boolean porIa = f != null;
        if (f == null) {
            f = porPalabras(q, catalogo);
            if (!r.ok()) aviso = "La IA no respondió; interpreté tu pregunta por palabras clave.";
        }
        if ("personas".equals(f.buscar()) && !vePersonas) {
            f = new Filtros("proyectos", f.habilidades(), null, null, false, f.estados(), f.atrasados(), f.conVacantes(), f.texto());
            aviso = "Como Colaborador solo puedes buscar en tus propios proyectos.";
        }
        if (!"personas".equals(f.buscar()) && Roles.COLABORADOR.equals(rol) && aviso == null) {
            aviso = "Solo se muestran los proyectos en los que participas.";
        } else if (!"personas".equals(f.buscar()) && Roles.PROJECT_MANAGER.equals(rol) && aviso == null) {
            aviso = "Solo se muestran los proyectos que lideras.";
        }

        // Sin ningún filtro y sin pedir un listado ("holi", "asdf"): no se
        // devuelve a todo el mundo, se pide reformular.
        if (colaborador && sinFiltros(f) && !pideListado(q)) {
            // Sin filtros de proyecto: se busca la pregunta en todo lo del colaborador.
            List<Map<String, Object>> todo = buscadorColaborador.general(q, s);
            if (!todo.isEmpty()) {
                return new Respuesta("Encontré " + todo.size() + " resultado(s) en tus proyectos, entregables, foros, calendario y cursos.",
                        List.of("Todo lo tuyo"), porIa, "Solo se muestra lo de los proyectos en los que participas.", todo);
            }
        }
        if (sinFiltros(f) && !pideListado(q)) {
            return new Respuesta("No entendí qué buscas.", List.of(), porIa,
                    colaborador ? "Prueba con algo como «mis entregables por corregir», «reuniones de esta semana» o «cursos de Java»."
                            : "Prueba con algo como «¿quién sabe Docker y está libre?» o «proyectos atrasados».", List.of());
        }

        // 2) Java busca con datos reales.
        List<Map<String, Object>> resultados = "personas".equals(f.buscar()) ? personas(f, s, catalogo) : proyectos(f, s, catalogo);
        if (colaborador && resultados.isEmpty()) {
            // Ningún proyecto suyo coincide: se busca lo mismo en sus entregables, foros, calendario y cursos.
            List<Map<String, Object>> todo = buscadorColaborador.general(q, s);
            if (!todo.isEmpty()) {
                return new Respuesta("Ninguno de tus proyectos coincide, pero encontré " + todo.size() + " resultado(s) en tus entregables, foros, calendario y cursos.",
                        chips(f), porIa, "Solo se muestra lo de los proyectos en los que participas.", todo);
            }
        }
        return new Respuesta(interpretacion(f, resultados.size()), chips(f), porIa, aviso, resultados);
    }

    private static boolean sinFiltros(Filtros f) {
        return f.habilidades().isEmpty() && (f.nivelMin() == null || f.nivelMin() <= 1) && f.libreMin() == null
                && !f.enBanca() && f.estados().isEmpty() && !f.atrasados() && !f.conVacantes() && f.texto() == null;
    }

    /** La pregunta pide un listado general ("todas las personas", "mis proyectos", "muéstrame el equipo"). */
    static boolean pideListado(String pregunta) {
        String q = " " + norm(pregunta).replaceAll("[^a-z0-9 ]", " ") + " ";
        return Pattern.compile(" (persona|personas|gente|colaborador|colaboradores|equipo|equipos|quien|quienes|"
                + "proyecto|proyectos|todo|todos|todas|lista|listar|muestra|muestrame|mostrar|mis) ").matcher(q).find();
    }

    // ───────────────────────── Personas ─────────────────────────

    private List<Map<String, Object>> personas(Filtros f, UsuarioSesion s, List<Habilidad> catalogo) {
        if (!BuscadorService.vePersonas(s.getRolEfectivo())) return List.of();
        String slug = Roles.slug(s.getRolEfectivo());
        Map<String, Long> idPorNombre = catalogo.stream().collect(Collectors.toMap(h -> norm(h.getNombre()), Habilidad::getId, (a, b) -> a));
        Map<Long, String> nombrePorId = catalogo.stream().collect(Collectors.toMap(Habilidad::getId, Habilidad::getNombre));
        Set<Long> pedidas = f.habilidades().stream().map(h -> idPorNombre.get(norm(h))).filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
        int nivelMin = f.nivelMin() != null ? f.nivelMin() : 1;
        int limite = proyectoService.limiteCargaConfigurado();
        Set<Long> equipoPm = buscadorService.equipoDelPm(s);
        Map<Long, List<PerfilHabilidad>> habsPorPerfil = perfilHabilidadRepository.findAll().stream()
                .collect(Collectors.groupingBy(ph -> ph.getId().getPerfilId()));

        record Fila(Perfil p, int coinciden, int niveles, int libre, int carga, String detalle) {}
        List<Fila> filas = new ArrayList<>();
        for (Perfil p : buscadorService.personasVisibles(s)) {
            if (!ProyectoService.esMiembroAsignable(p)) continue;
            List<PerfilHabilidad> suyas = habsPorPerfil.getOrDefault(p.getId(), List.of());
            List<String> cumple = new ArrayList<>();
            int niveles = 0;
            for (PerfilHabilidad ph : suyas) {
                if (pedidas.contains(ph.getId().getHabilidadId()) && ph.getNivel() >= nivelMin) {
                    cumple.add(nombrePorId.get(ph.getId().getHabilidadId()) + " " + nivel(ph.getNivel()));
                    niveles += ph.getNivel();
                }
            }
            if (!pedidas.isEmpty() && cumple.isEmpty()) continue;
            int carga = proyectoService.cargaActivaDe(p.getId());
            int declarada = Optional.ofNullable(p.getDisponibilidadPorcentaje()).orElse(100);
            int libre = Math.max(0, Math.min(declarada, limite - carga));
            if (f.enBanca() && carga > 0) continue;
            if (f.libreMin() != null && libre < f.libreMin()) continue;
            if (f.texto() != null && !norm(p.getUsuario().getNombreCompleto() + " " + p.getCargo()).contains(norm(f.texto()))) continue;
            String detalle = Optional.ofNullable(p.getCargo()).orElse("Colaborador") + " · libre " + libre + "%"
                    + (cumple.isEmpty() ? "" : " · " + String.join(", ", cumple));
            filas.add(new Fila(p, cumple.size(), niveles, libre, carga, detalle));
        }
        return filas.stream()
                .sorted(Comparator.comparingInt(Fila::coinciden).reversed().thenComparing(Comparator.comparingInt(Fila::niveles).reversed())
                        .thenComparing(Comparator.comparingInt(Fila::libre).reversed())
                        .thenComparing(x -> x.p().getUsuario().getNombreCompleto()))
                .limit(MAX)
                .map(x -> BuscadorService.item("Personas", x.coinciden() == pedidas.size() && !pedidas.isEmpty() ? "icon-user-check" : "icon-user",
                        x.p().getUsuario().getNombreCompleto(), x.detalle(),
                        BuscadorService.destinoColaborador(slug, x.p().getId(), x.p().getUsuario().getNombreCompleto(), equipoPm)))
                .toList();
    }

    // ───────────────────────── Proyectos ─────────────────────────

    private List<Map<String, Object>> proyectos(Filtros f, UsuarioSesion s, List<Habilidad> catalogo) {
        String slug = Roles.slug(s.getRolEfectivo());
        Set<Long> gestionados = new LinkedHashSet<>();
        List<Proyecto> visibles = buscadorService.proyectosVisibles(s, gestionados);
        Map<String, Long> idPorNombre = catalogo.stream().collect(Collectors.toMap(h -> norm(h.getNombre()), Habilidad::getId, (a, b) -> a));
        Set<Long> pedidas = f.habilidades().stream().map(h -> idPorNombre.get(norm(h))).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> nombrePorId = catalogo.stream().collect(Collectors.toMap(Habilidad::getId, Habilidad::getNombre));
        LocalDate hoy = LocalDate.now();

        List<Map<String, Object>> out = new ArrayList<>();
        for (Proyecto p : visibles) {
            if (!f.estados().isEmpty() && !f.estados().contains(p.getEstado())) continue;
            boolean atrasado = p.getFechaFinEstimada() != null && p.getFechaFinEstimada().isBefore(hoy)
                    && !"completado".equals(p.getEstado()) && !"cancelado".equals(p.getEstado());
            if (f.atrasados() && !atrasado) continue;
            long vacantes = talentMatchingService.vacantesDe(p.getId());
            if (f.conVacantes() && (vacantes < 1 || !ProyectoService.admiteNuevosIntegrantes(p))) continue;
            List<String> coinciden = List.of();
            if (!pedidas.isEmpty()) {
                coinciden = demandaService.requisitos(p, catalogo).keySet().stream().filter(pedidas::contains).map(nombrePorId::get).toList();
                if (coinciden.isEmpty()) continue;
            }
            if (f.texto() != null && !norm(p.getNombre() + " " + p.getDescripcion()).contains(norm(f.texto()))) continue;
            String detalle = BuscadorService.etiquetaEstado(p.getEstado())
                    + (atrasado ? " · atrasado (fin " + p.getFechaFinEstimada().format(FECHA) + ")"
                    : p.getFechaFinEstimada() != null ? " · fin " + p.getFechaFinEstimada().format(FECHA) : "")
                    + (vacantes > 0 && ProyectoService.admiteNuevosIntegrantes(p) ? " · " + vacantes + " vacante(s)" : "")
                    + (coinciden.isEmpty() ? "" : " · " + String.join(", ", coinciden));
            out.add(BuscadorService.item("Proyectos", atrasado ? "icon-alert-triangle" : "icon-folder", p.getNombre(), detalle,
                    BuscadorService.destinoProyecto(slug, p, gestionados.contains(p.getId()))));
            if (out.size() >= MAX) break;
        }
        return out;
    }

    // ───────────────────────── Interpretación ─────────────────────────

    private static String prompt(List<Habilidad> catalogo, boolean vePersonas, boolean colaborador) {
        return "Convierte la pregunta de un usuario de SkillBridge AI (gestión de talento y proyectos) en filtros de búsqueda. "
                + "Habilidades del catálogo: " + catalogo.stream().map(Habilidad::getNombre).sorted().collect(Collectors.joining(", ")) + ". "
                + "Traduce términos generales a habilidades del catálogo (\"nube\" → AWS, Docker, Kubernetes; \"frontend\" → React, Angular; "
                + "\"backend\" → Java, Spring Boot, Node.js) y usa SOLO nombres exactos del catálogo. "
                + (vePersonas ? "" : "El usuario solo puede buscar proyectos. ")
                + "Responde SOLO con JSON válido, sin texto adicional, con esta forma: "
                + "{\"buscar\":\"personas\" o \"proyectos\",\"habilidades\":[\"...\"],\"nivelMin\":1-5 o null,"
                + "\"libreMin\":porcentaje de capacidad libre mínimo o null (\"libre\"/\"disponible\" sin cifra = 20),"
                + "\"enBanca\":true si pide personas sin ningún proyecto,"
                + "\"estados\":[valores de planificacion, activo, en_pausa, completado, cancelado],"
                + "\"atrasados\":true si pide proyectos atrasados o vencidos,\"conVacantes\":true si pide proyectos con vacantes,"
                + "\"texto\":palabra libre a buscar en nombres o null}."
                + (colaborador ? " El usuario es un Colaborador: además de \"proyectos\", \"buscar\" puede ser \"entregables\", \"foros\", "
                + "\"eventos\" (calendario, reuniones, hitos) o \"cursos\". Si busca entregables agrega \"estadoEntrega\": "
                + "[valores de pendiente, vencido, por_revisar, requiere_ajustes, rehacer, completado] (\"por corregir\" = requiere_ajustes y rehacer; "
                + "\"pendientes\" = pendiente y vencido). En \"texto\" pon solo el tema o nombre concreto que busca (por ejemplo el nombre de un "
                + "proyecto o \"docker\"), o null si pide un listado general." : "");
    }

    private Filtros desdeJson(JsonNode j, List<Habilidad> catalogo) {
        if (j == null) return null;
        Set<String> validas = catalogo.stream().map(h -> norm(h.getNombre())).collect(Collectors.toSet());
        Map<String, String> original = catalogo.stream().collect(Collectors.toMap(h -> norm(h.getNombre()), Habilidad::getNombre, (a, b) -> a));
        List<String> habs = new ArrayList<>();
        for (JsonNode h : j.path("habilidades")) {
            String n = norm(h.asText(""));
            if (validas.contains(n)) habs.add(original.get(n));
        }
        List<String> estados = new ArrayList<>();
        for (JsonNode e : j.path("estados")) {
            String v = e.asText("");
            if (Set.of("planificacion", "activo", "en_pausa", "completado", "cancelado").contains(v)) estados.add(v);
        }
        String buscar = "personas".equals(j.path("buscar").asText()) ? "personas" : "proyectos";
        String texto = j.path("texto").isTextual() && !j.path("texto").asText().isBlank() ? j.path("texto").asText().trim() : null;
        return new Filtros(buscar, habs, entero(j.path("nivelMin"), 1, 5), entero(j.path("libreMin"), 0, 100),
                j.path("enBanca").asBoolean(false), estados, j.path("atrasados").asBoolean(false), j.path("conVacantes").asBoolean(false), texto);
    }

    /** Plan B sin IA. */
    static Filtros porPalabras(String pregunta, List<Habilidad> catalogo) {
        String q = " " + norm(pregunta).replaceAll("[^a-z0-9.#+% ]", " ") + " ";
        boolean fuerte = Pattern.compile(" (quien|quienes|alguien|persona|personas|colaborador|colaboradores|gente) ").matcher(q).find();
        boolean debil = Pattern.compile(" (sabe|saben|disponible|disponibles|libre|libres|banca|equipo) ").matcher(q).find();
        boolean personas = fuerte || (debil && !q.contains(" proyecto"));
        Set<String> habs = new LinkedHashSet<>();
        Map<String, String> original = new HashMap<>();
        catalogo.forEach(h -> original.put(norm(h.getNombre()), h.getNombre()));
        for (String n : original.keySet()) if (q.contains(" " + n + " ")) habs.add(original.get(n));
        SINONIMOS.forEach((palabra, lista) -> {
            if (q.contains(" " + palabra + " ")) lista.forEach(h -> { if (original.containsKey(norm(h))) habs.add(original.get(norm(h))); });
        });
        Integer libre = null;
        Matcher m = Pattern.compile("(\\d{1,3})\\s*%").matcher(q);
        if (m.find()) libre = Math.min(100, Integer.parseInt(m.group(1)));
        else if (q.matches(".* (libre|libres|disponible|disponibles|capacidad) .*")) libre = 20;
        List<String> estados = new ArrayList<>();
        if (q.contains(" activo")) estados.add("activo");
        if (q.contains(" pausa")) estados.add("en_pausa");
        if (q.contains(" planificacion")) estados.add("planificacion");
        if (q.contains(" completado") || q.contains(" terminado")) estados.add("completado");
        if (q.contains(" cancelado")) estados.add("cancelado");
        Integer nivel = q.contains(" experto") ? Integer.valueOf(5) : q.contains(" avanzado") ? Integer.valueOf(4) : q.contains(" intermedio") ? Integer.valueOf(3) : null;
        return new Filtros(personas ? "personas" : "proyectos", new ArrayList<>(habs), nivel, libre, q.contains(" banca") || q.contains(" sin proyecto"),
                estados, q.matches(".* (atrasad|vencid|retrasad)[a-z]* .*"), q.contains(" vacante"), null);
    }

    private static String interpretacion(Filtros f, int n) {
        String que = "personas".equals(f.buscar()) ? (n == 1 ? "persona" : "personas") : (n == 1 ? "proyecto" : "proyectos");
        return (n == 0 ? "No encontré " : "Encontré " + n + " ") + que + (n == 0 ? " con esos filtros." : ".");
    }

    private static List<String> chips(Filtros f) {
        List<String> c = new ArrayList<>();
        c.add("personas".equals(f.buscar()) ? "Personas" : "Proyectos");
        c.addAll(f.habilidades());
        if (f.nivelMin() != null && f.nivelMin() > 1) c.add("nivel " + nivel(f.nivelMin()) + " o más");
        if (f.enBanca()) c.add("en la banca");
        if (f.libreMin() != null) c.add("≥ " + f.libreMin() + "% libre");
        f.estados().forEach(e -> c.add(BuscadorService.etiquetaEstado(e)));
        if (f.atrasados()) c.add("atrasados");
        if (f.conVacantes()) c.add("con vacantes");
        if (f.texto() != null) c.add("“" + f.texto() + "”");
        return c;
    }

    private JsonNode leerJson(String texto) {
        if (texto == null) return null;
        int a = texto.indexOf('{'), b = texto.lastIndexOf('}');
        if (a < 0 || b <= a) return null;
        try { return objectMapper.readTree(texto.substring(a, b + 1)); } catch (Exception e) { return null; }
    }

    private static Integer entero(JsonNode n, int min, int max) {
        if (n == null || !n.canConvertToInt() || n.isNull()) return null;
        return Math.max(min, Math.min(max, n.asInt()));
    }

    private static String nivel(int n) {
        return switch (n) { case 1, 2 -> "básico"; case 3 -> "intermedio"; case 4 -> "avanzado"; case 5 -> "experto"; default -> ""; };
    }

    private static String norm(String v) {
        return BuscadorService.normalizar(v);
    }
}
