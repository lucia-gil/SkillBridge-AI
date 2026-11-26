package com.skillbridge.ai.service;

import com.skillbridge.ai.dto.CursoVista;
import com.skillbridge.ai.dto.EntregaFila;
import com.skillbridge.ai.dto.EntregableFila;
import com.skillbridge.ai.dto.EventoFila;
import com.skillbridge.ai.dto.HiloFila;
import com.skillbridge.ai.dto.NavItem;
import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.util.Roles;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Buscador del topbar para la vista Colaborador: además de proyectos y
 * habilidades (BuscadorService), encuentra lo que el colaborador ve en sus
 * propias pantallas: páginas del menú, sus entregables (con el estado de su
 * entrega), los hilos de foro, los eventos del calendario y los cursos.
 *
 * El alcance es exactamente el de esas pantallas, porque reutiliza los mismos
 * métodos que usan sus controllers (misEntregables, listarHilosDeMisProyectos,
 * listar del calendario y catalogoPublicado). No ve datos de otros roles ni de
 * proyectos donde no participa.
 */
@Service
public class BuscadorColaboradorService {

    private static final int MAX_PREDICTIVO = 5;
    private static final int MAX_IA = 8;
    private static final String BASE = "/colaborador/";

    /** Tipos que el buscador con IA del colaborador sabe listar. */
    public static final Set<String> TIPOS = Set.of("entregables", "foros", "eventos", "cursos");

    /**
     * Estado de MI entrega en un entregable, en un solo código:
     * pendiente (sin entregar, abierto) | vencido (sin entregar, cerrado) |
     * por_revisar | requiere_ajustes | rehacer | completado | cancelado.
     */
    public static final Set<String> ESTADOS_ENTREGA =
            Set.of("pendiente", "vencido", "por_revisar", "requiere_ajustes", "rehacer", "completado");

    private final EntregableService entregableService;
    private final ForoService foroService;
    private final EventoService eventoService;
    private final CursoService cursoService;
    private final NavService navService;
    private final BuscadorService buscadorService;

    public BuscadorColaboradorService(EntregableService entregableService, ForoService foroService,
                                      EventoService eventoService, CursoService cursoService, NavService navService,
                                      BuscadorService buscadorService) {
        this.entregableService = entregableService;
        this.foroService = foroService;
        this.eventoService = eventoService;
        this.cursoService = cursoService;
        this.navService = navService;
        this.buscadorService = buscadorService;
    }

    public static boolean aplica(UsuarioSesion s) {
        return s != null && Roles.COLABORADOR.equals(s.getRolEfectivo()) && s.getPerfilId() != null;
    }

    // ───────────────────────── Búsqueda predictiva (mientras escribe) ─────────────────────────

    /** Páginas del menú del colaborador que coinciden ("entreg" → Entregables). */
    public List<Map<String, Object>> paginas(String consulta, UsuarioSesion s) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (!aplica(s)) return out;
        String q = norm(consulta);
        if (q.length() < 2) return out;
        List<NavItem> items = new ArrayList<>(navService.itemsPara("colaborador"));
        items.add(new NavItem("Notificaciones", "icon-bell", "notificaciones.html"));
        items.add(new NavItem("Mi cuenta", "icon-user", "mi-cuenta.html"));
        items.add(new NavItem("Asistente IA", "icon-sparkles", "asistente-ia.html"));
        Set<String> vistos = new LinkedHashSet<>();
        for (NavItem n : items) {
            if (!vistos.add(n.getHref())) continue;
            String clave = norm(n.getLabel() + " " + n.getHref().replace(".html", "").replace('-', ' '));
            if (clave.contains(q)) {
                out.add(BuscadorService.item("Ir a", n.getIcon(), n.getLabel(), "Página de tu menú", BASE + n.getHref()));
            }
            if (out.size() >= 3) break;
        }
        return out;
    }

    /** Entregables, foros, eventos y cursos que coinciden con lo escrito. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> contenido(String consulta, UsuarioSesion s) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (!aplica(s)) return out;
        String q = norm(consulta);
        if (q.length() < 2) return out;
        out.addAll(entregables(e -> norm(e.getTitulo() + " " + e.getProyectoNombre() + " " + etiqueta(estadoEntrega(e))).contains(q),
                s, MAX_PREDICTIVO));
        out.addAll(foros(h -> norm(h.getTitulo() + " " + h.getProyectoNombre() + " " + nz(h.getTopicoNombre())).contains(q),
                s, MAX_PREDICTIVO));
        out.addAll(eventos(e -> norm(e.getTitulo() + " " + e.getProyectoNombre() + " " + nz(e.getTipoNombre())).contains(q),
                s, MAX_PREDICTIVO, false));
        out.addAll(cursos(c -> norm(c.titulo() + " " + nz(c.habilidad()) + " " + nz(c.plataformaNombre())).contains(q),
                MAX_PREDICTIVO));
        return out;
    }

    // ───────────────────────── Búsqueda con IA (Enter) ─────────────────────────

    /** Resultado de una pregunta del colaborador sobre sus entregables, foros, eventos o cursos. */
    @Transactional(readOnly = true)
    public BuscadorIaService.Respuesta preguntar(String tipo, List<String> estados, String texto, boolean porIa,
                                                 UsuarioSesion s) {
        String t = texto != null && !texto.isBlank() ? norm(texto) : null;
        List<String> chips = new ArrayList<>();
        List<Map<String, Object>> res;
        String nombre;
        switch (tipo) {
            case "entregables" -> {
                chips.add("Mis entregables");
                estados.forEach(e -> chips.add(etiqueta(e)));
                res = entregables(e -> (estados.isEmpty() || estados.contains(estadoEntrega(e)))
                        && (t == null || norm(e.getTitulo() + " " + e.getProyectoNombre() + " " + nz(e.getDescripcion())).contains(t)), s, MAX_IA);
                nombre = res.size() == 1 ? "entregable" : "entregables";
            }
            case "foros" -> {
                chips.add("Foros de mis proyectos");
                res = foros(h -> t == null || norm(h.getTitulo() + " " + h.getProyectoNombre() + " " + nz(h.getTopicoNombre())
                        + " " + nz(h.getTextoBusqueda())).contains(t), s, MAX_IA);
                nombre = res.size() == 1 ? "hilo" : "hilos";
            }
            case "eventos" -> {
                chips.add("Mi calendario");
                boolean soloProximos = t == null;
                if (soloProximos) chips.add("próximos");
                res = eventos(e -> t == null || norm(e.getTitulo() + " " + e.getProyectoNombre() + " " + nz(e.getTipoNombre())
                        + " " + nz(e.getDescripcion())).contains(t), s, MAX_IA, soloProximos);
                nombre = res.size() == 1 ? "evento" : "eventos";
            }
            default -> {
                chips.add("Cursos");
                res = cursos(c -> t == null || norm(c.titulo() + " " + nz(c.descripcion()) + " " + nz(c.habilidad())
                        + " " + nz(c.categoria()) + " " + nz(c.plataformaNombre())).contains(t), MAX_IA);
                nombre = res.size() == 1 ? "curso" : "cursos";
            }
        }
        if (texto != null && !texto.isBlank()) chips.add("“" + texto.trim() + "”");
        String interpretacion = res.isEmpty() ? "No encontré " + nombre + " con esos filtros." : "Encontré " + res.size() + " " + nombre + ".";
        String aviso = "cursos".equals(tipo) ? null : "Solo se muestra lo de los proyectos en los que participas.";
        return new BuscadorIaService.Respuesta(interpretacion, chips, porIa, aviso, res);
    }

    /** Búsqueda general en todo lo del colaborador (cuando la pregunta no dice qué tipo busca). */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> general(String pregunta, UsuarioSesion s) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (!aplica(s)) return out;
        List<String> palabras = palabrasClave(pregunta);
        if (palabras.isEmpty()) return out;
        Predicate<String> coincide = texto -> {
            String n = norm(texto);
            return palabras.stream().allMatch(n::contains);
        };
        for (com.skillbridge.ai.model.Proyecto p : buscadorService.proyectosVisibles(s, new LinkedHashSet<>())) {
            if (out.size() >= 3) break;
            if (coincide.test(p.getNombre() + " " + nz(p.getTecnologias()))) {
                out.add(BuscadorService.item("Proyectos", "icon-folder", p.getNombre(), BuscadorService.etiquetaEstado(p.getEstado()),
                        BuscadorService.destinoProyecto("colaborador", p, false)));
            }
        }
        out.addAll(entregables(e -> coincide.test(e.getTitulo() + " " + e.getProyectoNombre()), s, 4));
        out.addAll(foros(h -> coincide.test(h.getTitulo() + " " + h.getProyectoNombre() + " " + nz(h.getTopicoNombre())
                + " " + nz(h.getTextoBusqueda())), s, 4));
        out.addAll(eventos(e -> coincide.test(e.getTitulo() + " " + e.getProyectoNombre() + " " + nz(e.getDescripcion())), s, 4, false));
        out.addAll(cursos(c -> coincide.test(c.titulo() + " " + nz(c.habilidad()) + " " + nz(c.categoria())), 4));
        return out;
    }

    // ───────────────────────── Interpretación por palabras (sin IA) ─────────────────────────

    /** Qué tipo de cosa pregunta, o null si no lo dice. */
    public static String tipoPorPalabras(String pregunta) {
        String q = " " + norm(pregunta).replaceAll("[^a-z0-9 ]", " ") + " ";
        if (Pattern.compile(" (entreg|tarea)").matcher(q).find()) return "entregables";
        if (Pattern.compile(" (foro|hilo|publicacion)").matcher(q).find()) return "foros";
        if (Pattern.compile(" (evento|reunion|calendario|agenda|hito)").matcher(q).find()) return "eventos";
        if (Pattern.compile(" (curso|capacitacion|formacion|aprender )").matcher(q).find()) return "cursos";
        return null;
    }

    /** Estados de entrega que menciona la pregunta. */
    public static List<String> estadosPorPalabras(String pregunta) {
        String q = " " + norm(pregunta).replaceAll("[^a-z0-9 ]", " ") + " ";
        Set<String> e = new LinkedHashSet<>();
        if (q.matches(".* (complet|terminad|acabad|aprobad|cerrad|finalizad)[a-z]* .*")) e.add("completado");
        if (q.contains(" por revisar ") || q.contains(" en revision ") || q.matches(".* (esperando|sin revisar).*")) e.add("por_revisar");
        if (q.matches(".* ajuste[s]? .*")) e.add("requiere_ajustes");
        if (q.contains(" rehacer ")) e.add("rehacer");
        if (q.matches(".* (corregir|correccion|correcciones|observad)[a-z]* .*")) { e.add("requiere_ajustes"); e.add("rehacer"); }
        if (q.matches(".* (vencid|atrasad|retrasad)[a-z]* .*")) e.add("vencido");
        if (q.matches(".* (pendiente|pendientes|falta|faltan|por entregar|sin entregar|no entregu)[a-z]* .*")) { e.add("pendiente"); e.add("vencido"); }
        if (q.matches(".* (ya )?entregad[oa]s? .*") && e.isEmpty()) {
            e.add("por_revisar"); e.add("requiere_ajustes"); e.add("rehacer"); e.add("completado");
        }
        return new ArrayList<>(e);
    }

    /** Palabras con contenido de la pregunta (sin artículos ni verbos de búsqueda). */
    static List<String> palabrasClave(String pregunta) {
        Set<String> vacias = Set.of("el", "la", "los", "las", "un", "una", "unos", "unas", "de", "del", "en", "y", "o", "a",
                "al", "con", "por", "para", "que", "qué", "mi", "mis", "me", "tengo", "hay", "sobre", "busca", "buscar",
                "muestra", "muestrame", "dame", "quiero", "ver", "donde", "cual", "cuales", "todo", "todos", "algo", "es", "esta");
        List<String> out = new ArrayList<>();
        for (String p : norm(pregunta).replaceAll("[^a-z0-9.#+ ]", " ").split("\\s+")) {
            if (p.length() >= 3 && !vacias.contains(p)) out.add(p);
        }
        return out;
    }

    // ───────────────────────── Fuentes (mismo alcance que sus pantallas) ─────────────────────────

    private List<Map<String, Object>> entregables(Predicate<EntregableFila> filtro, UsuarioSesion s, int max) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (EntregableFila e : entregableService.misEntregables(s.getPerfilId())) {
            if (out.size() >= max) break;
            if (!filtro.test(e)) continue;
            String estado = estadoEntrega(e);
            out.add(BuscadorService.item("Entregables", icono(estado), e.getTitulo(),
                    e.getProyectoNombre() + " · " + etiqueta(estado) + " · cierra " + e.getFechaCierreLabel(),
                    BASE + "entregables/" + e.getId() + ".html"));
        }
        return out;
    }

    private List<Map<String, Object>> foros(Predicate<HiloFila> filtro, UsuarioSesion s, int max) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (HiloFila h : foroService.listarHilosDeMisProyectos(s.getPerfilId())) {
            if (out.size() >= max) break;
            if (!filtro.test(h)) continue;
            out.add(BuscadorService.item("Foros", h.isTieneAceptada() ? "icon-check-circle" : "icon-message", h.getTitulo(),
                    h.getProyectoNombre() + " · " + h.getNumRespuestas() + " respuesta(s)" + (h.isTieneAceptada() ? " · con solución" : ""),
                    BASE + "foro-hilo.html?id=" + h.getId()));
        }
        return out;
    }

    private List<Map<String, Object>> eventos(Predicate<EventoFila> filtro, UsuarioSesion s, int max, boolean soloProximos) {
        List<Map<String, Object>> out = new ArrayList<>();
        String ahora = LocalDateTime.now().withSecond(0).withNano(0).toString();
        for (EventoFila e : eventoService.listar(s.getPerfilId(), false, s.getRolOrganizacional())) {
            if (out.size() >= max) break;
            if (soloProximos && (e.getFechaInicioIso() == null || e.getFechaInicioIso().compareTo(ahora) < 0)) continue;
            if (!filtro.test(e)) continue;
            out.add(BuscadorService.item("Calendario", "icon-calendar", e.getTitulo(),
                    nz(e.getTipoNombre()) + " · " + e.getProyectoNombre() + " · " + e.getFechaLabel() + " " + nz(e.getHoraLabel()),
                    BASE + "calendario.html?evento=" + e.getId()));
        }
        return out;
    }

    private List<Map<String, Object>> cursos(Predicate<CursoVista> filtro, int max) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (CursoVista c : cursoService.catalogoPublicado()) {
            if (out.size() >= max) break;
            if (!filtro.test(c)) continue;
            String detalle = String.join(" · ", List.of(nz(c.plataformaNombre()), nz(c.habilidad()), nz(c.nivel())).stream()
                    .filter(x -> !x.isBlank()).toList());
            out.add(BuscadorService.item("Cursos", "icon-graduation", c.titulo(), detalle,
                    BASE + "cursos.html?buscar=" + java.net.URLEncoder.encode(c.titulo(), java.nio.charset.StandardCharsets.UTF_8)));
        }
        return out;
    }

    // ───────────────────────── Utilidades ─────────────────────────

    static String estadoEntrega(EntregableFila e) {
        EntregaFila mia = e.getMiEntrega();
        if (mia == null) {
            if ("cancelado".equals(e.getEstadoCrudo())) return "cancelado";
            return e.isVencido() ? "vencido" : "pendiente";
        }
        return switch (mia.getEstadoCrudo()) {
            case "completado", "requiere_ajustes", "rehacer" -> mia.getEstadoCrudo();
            default -> "por_revisar";
        };
    }

    static String etiqueta(String estado) {
        return switch (estado) {
            case "pendiente" -> "Sin entregar";
            case "vencido" -> "Vencido sin entregar";
            case "por_revisar" -> "Por revisar";
            case "requiere_ajustes" -> "Requiere ajustes";
            case "rehacer" -> "Rehacer";
            case "completado" -> "Completado";
            case "cancelado" -> "Cancelado";
            default -> estado;
        };
    }

    private static String icono(String estado) {
        return switch (estado) {
            case "completado" -> "icon-check-circle";
            case "requiere_ajustes", "rehacer", "vencido" -> "icon-alert-triangle";
            default -> "icon-file-text";
        };
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String norm(String s) {
        return BuscadorService.normalizar(s);
    }
}
