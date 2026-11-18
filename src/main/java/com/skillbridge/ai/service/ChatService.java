package com.skillbridge.ai.service;

import com.skillbridge.ai.dto.InicialesUtil;
import com.skillbridge.ai.model.Asignacion;
import com.skillbridge.ai.model.ChatLectura;
import com.skillbridge.ai.model.ChatMensaje;
import com.skillbridge.ai.model.ChatSala;
import com.skillbridge.ai.model.Perfil;
import com.skillbridge.ai.model.Proyecto;
import com.skillbridge.ai.repository.AsignacionRepository;
import com.skillbridge.ai.repository.ChatLecturaRepository;
import com.skillbridge.ai.repository.ChatMensajeRepository;
import com.skillbridge.ai.repository.ChatSalaRepository;
import com.skillbridge.ai.repository.PerfilRepository;
import com.skillbridge.ai.repository.ProyectoRepository;
import com.skillbridge.ai.util.OperacionInvalidaException;
import com.skillbridge.ai.util.Roles;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Chat de proyectos (RF07): una sala por proyecto, solo para sus integrantes
 * activos (PM y colaboradores). Guarda el historial con autor, fecha y hora.
 * Lo consume ChatApiController (REST/JSON) desde js/chat.js con jQuery/AJAX.
 */
@Service
public class ChatService {

    public static final int MAX_CARACTERES = 2000;
    private static final int PAGINA = 40;
    private static final Set<String> PROYECTO_CERRADO = Set.of("completado", "cancelado");
    private static final Locale ES = new Locale("es", "PE");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DIA = DateTimeFormatter.ofPattern("d MMM", ES);
    private static final DateTimeFormatter DIA_ANIO = DateTimeFormatter.ofPattern("d MMM yyyy", ES);
    private static final DateTimeFormatter COMPLETA = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", ES);

    /** Una sala en la lista de la izquierda. */
    public record SalaVista(Long id, Long proyectoId, String proyecto, String estadoProyecto, boolean soloLectura,
                            String miRol, int miembros, Long ultimoId, String ultimoAutor, String ultimoTexto,
                            String ultimoCuando, String ultimoIso, long noLeidos) {}

    /** Un mensaje de la conversación. */
    public record MensajeVista(Long id, Long autorId, String autor, String iniciales, String cargo, Long usuarioId,
                               boolean tieneFoto, String contenido, String iso, String hora, String dia,
                               String fechaCompleta, boolean propio) {}

    /** Integrante de la sala (cabecera de la conversación). */
    public record MiembroVista(Long perfilId, String nombre, String iniciales, String cargo, String rol,
                               Long usuarioId, boolean tieneFoto) {}

    private final ChatSalaRepository salaRepository;
    private final ChatMensajeRepository mensajeRepository;
    private final ChatLecturaRepository lecturaRepository;
    private final AsignacionRepository asignacionRepository;
    private final ProyectoRepository proyectoRepository;
    private final AsistenteIaService asistenteIaService;
    private final PerfilRepository perfilRepository;

    public ChatService(ChatSalaRepository salaRepository, ChatMensajeRepository mensajeRepository,
                       ChatLecturaRepository lecturaRepository, AsignacionRepository asignacionRepository,
                       ProyectoRepository proyectoRepository, AsistenteIaService asistenteIaService,
                       PerfilRepository perfilRepository) {
        this.salaRepository = salaRepository;
        this.mensajeRepository = mensajeRepository;
        this.lecturaRepository = lecturaRepository;
        this.asignacionRepository = asignacionRepository;
        this.proyectoRepository = proyectoRepository;
        this.asistenteIaService = asistenteIaService;
        this.perfilRepository = perfilRepository;
    }

    /** Excepción para "no eres integrante de este proyecto" (el controlador la responde con 403). */
    public static class AccesoDenegado extends RuntimeException {
        public AccesoDenegado() { super("Solo los integrantes del proyecto pueden ver este chat."); }
    }

    // ─────────────────────────── salas ───────────────────────────

    /** Salas de los proyectos donde la persona tiene una asignación activa, con la actividad más reciente primero. */
    @Transactional
    public List<SalaVista> salas(Long perfilId) {
        if (perfilId == null) return List.of();
        Map<Long, Asignacion> porProyecto = new LinkedHashMap<>();
        for (Asignacion a : asignacionRepository.listarPorPerfilYEstado(perfilId, "activa")) {
            // Si lidera y además colabora en el mismo proyecto, se muestra como PM.
            Asignacion previa = porProyecto.get(a.getProyectoId());
            if (previa == null || Roles.PROJECT_MANAGER.equals(a.getRolEnProyecto())) porProyecto.put(a.getProyectoId(), a);
        }
        List<SalaVista> salas = new ArrayList<>();
        for (Asignacion a : porProyecto.values()) {
            Proyecto p = a.getProyecto();
            ChatSala sala = salaDe(p.getId());
            List<ChatMensaje> ultimo = mensajeRepository.anteriores(sala.getId(), Long.MAX_VALUE, PageRequest.of(0, 1));
            ChatMensaje m = ultimo.isEmpty() ? null : ultimo.get(0);
            salas.add(new SalaVista(sala.getId(), p.getId(), p.getNombre(), p.getEstado(), PROYECTO_CERRADO.contains(p.getEstado()),
                    Roles.PROJECT_MANAGER.equals(a.getRolEnProyecto()) ? "Project Manager" : "Colaborador",
                    asignacionRepository.listarEquipoDeProyecto(p.getId(), "activa").size(),
                    m == null ? null : m.getId(),
                    m == null ? null : (m.getAutorId().equals(perfilId) ? "Tú" : primerNombre(m.getAutor())),
                    m == null ? null : recortar(m.getContenido(), 90),
                    m == null ? null : cuandoCorto(m.getFechaEnvio()),
                    m == null ? null : m.getFechaEnvio().toString(),
                    noLeidos(sala.getId(), perfilId)));
        }
        salas.sort(Comparator.comparing((SalaVista s) -> s.ultimoIso() == null ? "" : s.ultimoIso()).reversed()
                .thenComparing(SalaVista::proyecto));
        return salas;
    }

    @Transactional(readOnly = true)
    public List<MiembroVista> miembros(Long salaId, Long perfilId) {
        ChatSala sala = exigirAcceso(salaId, perfilId);
        return asignacionRepository.listarEquipoDeProyecto(sala.getProyectoId(), "activa").stream()
                .map(a -> {
                    Perfil p = a.getPerfil();
                    return new MiembroVista(p.getId(), p.getUsuario().getNombreCompleto(), InicialesUtil.de(p.getUsuario().getNombreCompleto()),
                            p.getCargo(), Roles.PROJECT_MANAGER.equals(a.getRolEnProyecto()) ? "PM" : "Colaborador",
                            p.getUsuarioId(), p.getUsuario().tieneFotoPerfil());
                })
                .toList();
    }

    // ─────────────────────────── mensajes ───────────────────────────

    /**
     * Mensajes de una sala. Sin parámetros: los últimos 40. Con "despues": los
     * nuevos (para el sondeo cada pocos segundos). Con "antes": los 40 previos
     * (botón "Ver mensajes anteriores").
     */
    @Transactional(readOnly = true)
    public List<MensajeVista> mensajes(Long salaId, Long perfilId, Long despues, Long antes) {
        exigirAcceso(salaId, perfilId);
        List<ChatMensaje> lista;
        if (despues != null) {
            lista = mensajeRepository.posteriores(salaId, despues, PageRequest.of(0, 200));
        } else {
            lista = new ArrayList<>(mensajeRepository.anteriores(salaId, antes == null ? Long.MAX_VALUE : antes, PageRequest.of(0, PAGINA)));
            Collections.reverse(lista);
        }
        return lista.stream().map(m -> vista(m, perfilId)).toList();
    }

    @Transactional
    public MensajeVista enviar(Long salaId, Long perfilId, String contenido) {
        ChatSala sala = exigirAcceso(salaId, perfilId);
        Proyecto p = proyectoRepository.findById(sala.getProyectoId()).orElse(null);
        if (p != null && PROYECTO_CERRADO.contains(p.getEstado()))
            throw new OperacionInvalidaException("El proyecto está " + p.getEstado() + ": su chat quedó en solo lectura.");
        String texto = contenido == null ? "" : contenido.strip();
        if (texto.isEmpty()) throw new OperacionInvalidaException("Escribe un mensaje antes de enviar.");
        if (texto.length() > MAX_CARACTERES)
            throw new OperacionInvalidaException("El mensaje es demasiado largo (máximo " + MAX_CARACTERES + " caracteres).");
        ChatMensaje m = mensajeRepository.save(new ChatMensaje(salaId, perfilId, texto));
        marcarLeido(salaId, perfilId, m.getId());   // lo propio ya cuenta como leído
        // El mensaje recién guardado no trae cargada la relación "autor" (y
        // Hibernate lo reutiliza tal cual en consultas de esta transacción):
        // se arma la respuesta con el perfil leído aparte.
        Perfil autor = perfilRepository.findById(perfilId).orElseThrow();
        return vista(m, autor, perfilId);
    }

    @Transactional
    public void marcarLeido(Long salaId, Long perfilId, Long ultimoId) {
        exigirAcceso(salaId, perfilId);
        if (ultimoId == null) return;
        ChatLectura l = lecturaRepository.findById(new ChatLectura.Id(salaId, perfilId))
                .orElseGet(() -> new ChatLectura(salaId, perfilId, 0L));
        if (ultimoId > l.getUltimoMensajeId()) {
            l.setUltimoMensajeId(ultimoId);
            lecturaRepository.save(l);
        }
    }

    /** Total de mensajes sin leer en todas sus salas (contador del sidebar). */
    @Transactional(readOnly = true)
    public long totalNoLeidos(Long perfilId) {
        if (perfilId == null) return 0;
        long total = 0;
        for (Asignacion a : asignacionRepository.listarPorPerfilYEstado(perfilId, "activa")) {
            var sala = salaRepository.findFirstByProyectoIdOrderByIdAsc(a.getProyectoId());
            if (sala.isPresent()) total += noLeidos(sala.get().getId(), perfilId);
        }
        return total;
    }

    /** Resumen con IA de los últimos mensajes (no se guarda). */
    @Transactional(readOnly = true)
    public String resumir(Long salaId, Long perfilId) {
        ChatSala sala = exigirAcceso(salaId, perfilId);
        List<ChatMensaje> lista = new ArrayList<>(mensajeRepository.anteriores(salaId, Long.MAX_VALUE, PageRequest.of(0, 80)));
        if (lista.size() < 2) throw new OperacionInvalidaException("Todavía hay muy pocos mensajes para resumir.");
        Collections.reverse(lista);
        StringBuilder texto = new StringBuilder();
        for (ChatMensaje m : lista) {
            texto.append(m.getFechaEnvio().format(COMPLETA)).append(" · ")
                    .append(m.getAutor().getUsuario().getNombreCompleto()).append(": ")
                    .append(m.getContenido().replace('\n', ' ')).append('\n');
        }
        String proyecto = proyectoRepository.findById(sala.getProyectoId()).map(Proyecto::getNombre).orElse("Proyecto");
        return asistenteIaService.resumirConversacion(proyecto, texto.toString());
    }

    // ─────────────────────────── helpers ───────────────────────────

    /** La sala del proyecto; si todavía no existe se crea ("General"). */
    private ChatSala salaDe(Long proyectoId) {
        return salaRepository.findFirstByProyectoIdOrderByIdAsc(proyectoId)
                .orElseGet(() -> salaRepository.save(new ChatSala(proyectoId, "General")));
    }

    private ChatSala exigirAcceso(Long salaId, Long perfilId) {
        ChatSala sala = salaRepository.findById(salaId).orElseThrow(() -> new com.skillbridge.ai.util.RecursoNoEncontradoException("Esa sala ya no existe."));
        if (perfilId == null || !asignacionRepository.existsByProyectoIdAndPerfilIdAndEstado(sala.getProyectoId(), perfilId, "activa"))
            throw new AccesoDenegado();
        return sala;
    }

    private long noLeidos(Long salaId, Long perfilId) {
        long ultimoLeido = lecturaRepository.findById(new ChatLectura.Id(salaId, perfilId))
                .map(ChatLectura::getUltimoMensajeId).orElse(0L);
        return mensajeRepository.countBySalaIdAndIdGreaterThanAndAutorIdNot(salaId, ultimoLeido, perfilId);
    }

    private MensajeVista vista(ChatMensaje m, Long perfilId) {
        return vista(m, m.getAutor(), perfilId);
    }

    private MensajeVista vista(ChatMensaje m, Perfil a, Long perfilId) {
        String nombre = a.getUsuario().getNombreCompleto();
        LocalDateTime f = m.getFechaEnvio();
        return new MensajeVista(m.getId(), m.getAutorId(), nombre, InicialesUtil.de(nombre), a.getCargo(), a.getUsuarioId(),
                a.getUsuario().tieneFotoPerfil(), m.getContenido(), f.toString(), f.format(HORA), dia(f.toLocalDate()),
                f.format(COMPLETA), m.getAutorId().equals(perfilId));
    }

    private static String dia(LocalDate d) {
        LocalDate hoy = LocalDate.now();
        if (d.equals(hoy)) return "Hoy";
        if (d.equals(hoy.minusDays(1))) return "Ayer";
        return d.getYear() == hoy.getYear() ? d.format(DIA) : d.format(DIA_ANIO);
    }

    private static String cuandoCorto(LocalDateTime f) {
        LocalDate d = f.toLocalDate(), hoy = LocalDate.now();
        if (d.equals(hoy)) return f.format(HORA);
        if (d.equals(hoy.minusDays(1))) return "Ayer";
        return d.getYear() == hoy.getYear() ? d.format(DIA) : d.format(DIA_ANIO);
    }

    private static String primerNombre(Perfil p) {
        String n = p.getUsuario().getNombreCompleto();
        return n == null ? "" : n.trim().split("\\s+")[0];
    }

    private static String recortar(String s, int max) {
        String t = s == null ? "" : s.replaceAll("\\s+", " ").trim();
        return t.length() <= max ? t : t.substring(0, max - 1) + "…";
    }
}
