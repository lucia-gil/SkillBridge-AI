package com.skillbridge.ai.service;

import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.model.AsistenteAdjunto;
import com.skillbridge.ai.model.AsistenteConversacion;
import com.skillbridge.ai.model.AsistenteMensaje;
import com.skillbridge.ai.repository.AsistenteAdjuntoRepository;
import com.skillbridge.ai.repository.AsistenteConversacionRepository;
import com.skillbridge.ai.repository.AsistenteMensajeRepository;
import com.skillbridge.ai.util.OperacionInvalidaException;
import com.skillbridge.ai.util.Roles;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Asistente IA con memoria (chatbot del Entregable 9). Cada mensaje:
 *  1. se guarda en asistente_mensajes (la conversación se crea si es nueva);
 *  2. se arma el contexto real del usuario según su vista (AsistenteContextoService);
 *  3. se envían a la IA ese contexto + los últimos turnos de la conversación;
 *  4. se guarda la respuesta. Las conversaciones quedan en el panel "Historial".
 */
@Service
public class AsistenteConversacionService {

    public static final int MAX_CARACTERES = 2000;
    private static final int TURNOS_DE_MEMORIA = 12;
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("d MMM", new Locale("es", "PE"));

    public static final int MAX_ADJUNTOS = 3;
    public static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final int MAX_TEXTO_ARCHIVO = 15000;
    /** Lo que la IA puede leer: imágenes y PDF (Claude) y archivos de texto (se leen como texto). */
    private static final Set<String> TIPOS_BINARIOS = Set.of("image/png", "image/jpeg", "image/webp", "image/gif", "application/pdf");
    private static final Set<String> EXT_TEXTO = Set.of("txt", "csv", "md", "json", "log", "xml", "java", "sql", "js", "html", "css", "yml", "yaml", "properties");

    public record ConversacionVista(Long id, String titulo, String cuando, boolean fijada) {}
    public record AdjuntoVista(Long id, String nombre, String tipo, String tamano, boolean imagen) {}
    public record MensajeVista(Long id, boolean deUsuario, String texto, String hora, List<AdjuntoVista> adjuntos) {}
    public record Respuesta(Long conversacionId, String titulo, boolean nueva, String respuesta, boolean ok,
                            String hora, List<AsistenteContextoService.Accion> acciones, List<AdjuntoVista> adjuntos) {}

    private final AsistenteConversacionRepository conversacionRepository;
    private final AsistenteMensajeRepository mensajeRepository;
    private final AsistenteContextoService contextoService;
    private final AsistenteIaService asistenteIaService;
    private final AsistenteAdjuntoRepository adjuntoRepository;
    private final com.skillbridge.ai.service.agente.PoliticaAgente politica;
    private final com.skillbridge.ai.repository.AgenteAlcanceRepository alcanceRepository;
    @org.springframework.beans.factory.annotation.Value("${agente.enabled:true}")
    private boolean agenteHabilitado;
    private final com.skillbridge.ai.service.agente.HerramientasAgente herramientas;
    private final com.skillbridge.ai.service.agente.ConfirmacionAgenteService confirmaciones;

    public AsistenteConversacionService(AsistenteConversacionRepository conversacionRepository,
                                        AsistenteMensajeRepository mensajeRepository,
                                        AsistenteContextoService contextoService, AsistenteIaService asistenteIaService,
                                        AsistenteAdjuntoRepository adjuntoRepository, com.skillbridge.ai.service.agente.HerramientasAgente herramientas, com.skillbridge.ai.service.agente.ConfirmacionAgenteService confirmaciones,
                                        com.skillbridge.ai.service.agente.PoliticaAgente politica,
                                        com.skillbridge.ai.repository.AgenteAlcanceRepository alcanceRepository) {
        this.politica = politica;
        this.alcanceRepository = alcanceRepository;
        this.confirmaciones = confirmaciones;
        this.herramientas = herramientas;
        this.adjuntoRepository = adjuntoRepository;
        this.conversacionRepository = conversacionRepository;
        this.mensajeRepository = mensajeRepository;
        this.contextoService = contextoService;
        this.asistenteIaService = asistenteIaService;
    }

    public static String rolDe(UsuarioSesion s) {
        String r = s.getRolEfectivo();
        return r == null ? Roles.COLABORADOR : r;
    }

    @Transactional(readOnly = true)
    public List<ConversacionVista> conversaciones(UsuarioSesion s) {
        return conversacionRepository.findTop30ByUsuarioIdAndRolOrderByFijadaDescFechaActualizacionDesc(s.getUsuarioId(), rolDe(s)).stream()
                .filter(c -> alcanceValido(c.getId(), s)).map(c -> new ConversacionVista(c.getId(), c.getTitulo(), cuando(c.getFechaActualizacion()), c.isFijada())).toList();
    }

    @Transactional(readOnly = true)
    public List<MensajeVista> mensajes(Long conversacionId, UsuarioSesion s) {
        exigirPropia(conversacionId, s);
        List<AsistenteMensaje> msgs = mensajeRepository.findByConversacionIdOrderByIdAsc(conversacionId);
        Map<Long, List<AdjuntoVista>> adjuntos = msgs.isEmpty() ? Map.of()
                : adjuntoRepository.infoDeMensajes(msgs.stream().map(AsistenteMensaje::getId).toList()).stream()
                .collect(Collectors.groupingBy(AsistenteAdjuntoRepository.Info::getMensajeId,
                        Collectors.mapping(a -> vista(a.getId(), a.getNombre(), a.getTipo(), a.getTamano()), Collectors.toList())));
        return msgs.stream().map(m -> new MensajeVista(m.getId(), m.esDelUsuario(), m.getContenido(), m.getFecha().format(HORA),
                adjuntos.getOrDefault(m.getId(), List.of()))).toList();
    }

    @Transactional
    public ConversacionVista renombrar(Long conversacionId, String titulo, UsuarioSesion s) {
        String t = titulo == null ? "" : titulo.replaceAll("\\s+", " ").trim();
        if (t.isEmpty()) throw new OperacionInvalidaException("Escribe un nombre para la conversación.");
        if (t.length() > 120) throw new OperacionInvalidaException("El nombre puede tener hasta 120 caracteres.");
        AsistenteConversacion c = exigirPropia(conversacionId, s);
        c.renombrar(t);
        return new ConversacionVista(c.getId(), c.getTitulo(), cuando(c.getFechaActualizacion()), c.isFijada());
    }

    @Transactional
    public ConversacionVista fijar(Long conversacionId, boolean fijada, UsuarioSesion s) {
        AsistenteConversacion c = exigirPropia(conversacionId, s);
        c.fijar(fijada);
        return new ConversacionVista(c.getId(), c.getTitulo(), cuando(c.getFechaActualizacion()), c.isFijada());
    }

    /** Adjunto para verlo o descargarlo (solo su dueño). */
    @Transactional(readOnly = true)
    public AsistenteAdjunto adjunto(Long id, UsuarioSesion s) {
        Long dueno = adjuntoRepository.duenoDe(id);
        Long conversacion = adjuntoRepository.conversacionDe(id);
        if (conversacion == null) throw new com.skillbridge.ai.util.RecursoNoEncontradoException("Ese archivo ya no existe.");
        exigirPropia(conversacion, s);
        if (dueno == null || !dueno.equals(s.getUsuarioId())) throw new com.skillbridge.ai.util.RecursoNoEncontradoException("Ese archivo ya no existe.");
        return adjuntoRepository.findById(id).orElseThrow(() -> new com.skillbridge.ai.util.RecursoNoEncontradoException("Ese archivo ya no existe."));
    }

    @Transactional
    public void eliminar(Long conversacionId, UsuarioSesion s) {
        conversacionRepository.delete(exigirPropia(conversacionId, s));
    }

    /**
     * Conversación de cada usuario (y rol) cuya respuesta todavía se está
     * generando. Si la persona sale de la página y vuelve, el navegador la
     * pregunta (GET /api/asistente/en-curso) para reabrirla y mostrar
     * "escribiendo…" hasta que llegue la respuesta. Vive en memoria: alcanza
     * con una instancia (EC2); si se reinicia el servidor, la respuesta en
     * curso se pierde de todos modos.
     */
    private final java.util.concurrent.ConcurrentHashMap<String, Long> enCurso = new java.util.concurrent.ConcurrentHashMap<>();

    /** Hilos para pedir el título con IA en paralelo a la respuesta (no la retrasan). */
    private final java.util.concurrent.ExecutorService titulos = java.util.concurrent.Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "asistente-titulos");
        t.setDaemon(true);
        return t;
    });

    private static String claveEnCurso(UsuarioSesion s) {
        return s.getUsuarioId() + ":" + rolDe(s);
    }

    /** Id de la conversación con una respuesta en curso, o null si no hay ninguna. */
    public Long conversacionEnCurso(UsuarioSesion s) {
        return enCurso.get(claveEnCurso(s));
    }

    public Respuesta enviar(UsuarioSesion s, Long conversacionId, String texto) {
        return enviar(s, conversacionId, texto, List.of());
    }

    public Respuesta enviar(UsuarioSesion s, Long conversacionId, String texto, List<MultipartFile> archivos) {
        String mensaje = texto == null ? "" : texto.trim();
        List<MultipartFile> validos = archivos == null ? List.of() : archivos.stream().filter(f -> f != null && !f.isEmpty()).toList();
        if (!validos.isEmpty()) throw new OperacionInvalidaException("Los adjuntos del asistente están deshabilitados. Escribe la información necesaria sin secretos.");
        if (validos.size() > MAX_ADJUNTOS) throw new OperacionInvalidaException("Puedes adjuntar hasta " + MAX_ADJUNTOS + " archivos por mensaje.");
        for (MultipartFile f : validos) {
            if (f.getSize() > MAX_BYTES) throw new OperacionInvalidaException("\"" + f.getOriginalFilename() + "\" pesa más de 5 MB.");
            if (tipoDe(f) == null) throw new OperacionInvalidaException("\"" + f.getOriginalFilename()
                    + "\" no es un tipo admitido: imágenes (PNG, JPG, WEBP, GIF), PDF o archivos de texto (TXT, CSV, MD, JSON…).");
        }
        if (mensaje.isEmpty() && validos.isEmpty()) throw new OperacionInvalidaException("Escribe un mensaje antes de enviar.");
        if (mensaje.length() > MAX_CARACTERES) throw new OperacionInvalidaException("El mensaje es demasiado largo (máximo " + MAX_CARACTERES + " caracteres).");
        String rol = rolDe(s);

        boolean nueva = conversacionId == null;
        AsistenteConversacion conv = nueva
                ? conversacionRepository.save(new AsistenteConversacion(s.getUsuarioId(), rol,
                        titulo(mensaje.isEmpty() ? nombreSeguro(validos.get(0).getOriginalFilename()) : mensaje)))
                : exigirPropia(conversacionId, s);

        if (nueva) alcanceRepository.save(new com.skillbridge.ai.model.AgenteAlcance(conv.getId(), politica.firma(s)));

        // Memoria: los últimos turnos de esta conversación (antes del mensaje nuevo)
        List<AsistenteMensaje> previos = new ArrayList<>(mensajeRepository.findTop12ByConversacionIdOrderByIdDesc(conv.getId()));
        Collections.reverse(previos);
        AsistenteMensaje guardado = mensajeRepository.save(new AsistenteMensaje(conv.getId(), AsistenteMensaje.USUARIO, mensaje));

        // Adjuntos: se guardan; imágenes y PDF viajan a la IA, los de texto se leen y se agregan al mensaje
        List<AsistenteIaService.Archivo> paraIa = new ArrayList<>();
        StringBuilder textoArchivos = new StringBuilder();
        List<AdjuntoVista> adjuntosVista = new ArrayList<>();
        for (MultipartFile f : validos) {
            byte[] datos;
            try { datos = f.getBytes(); } catch (IOException e) { throw new OperacionInvalidaException("No se pudo leer el archivo " + f.getOriginalFilename() + "."); }
            String tipo = tipoDe(f), nombre = nombreSeguro(f.getOriginalFilename());
            AsistenteAdjunto a = adjuntoRepository.save(new AsistenteAdjunto(guardado.getId(), nombre, tipo, datos));
            adjuntosVista.add(vista(a.getId(), nombre, tipo, datos.length));
            if (TIPOS_BINARIOS.contains(tipo)) {
                paraIa.add(new AsistenteIaService.Archivo(nombre, tipo, datos));
            } else {
                String contenido = new String(datos, StandardCharsets.UTF_8);
                if (contenido.length() > MAX_TEXTO_ARCHIVO) contenido = contenido.substring(0, MAX_TEXTO_ARCHIVO) + "\n[… archivo recortado]";
                textoArchivos.append("\n\n--- Archivo adjunto: ").append(nombre).append(" ---\n").append(contenido);
            }
        }
        String mensajeIa = (mensaje.isEmpty() ? "Revisa el archivo adjunto y dime qué contiene y qué me sirve para mi trabajo en SkillBridge." : mensaje)
                + textoArchivos;

        List<AsistenteIaService.Turno> turnos = new ArrayList<>();
        for (AsistenteMensaje m : previos) agregar(turnos, m.esDelUsuario(), m.getContenido().isEmpty() ? "(adjuntó un archivo)" : m.getContenido());
        agregar(turnos, true, mensajeIa);
        if (!paraIa.isEmpty()) {
            AsistenteIaService.Turno ultimo = turnos.remove(turnos.size() - 1);
            turnos.add(new AsistenteIaService.Turno(true, ultimo.texto(), paraIa));
        }
        while (!turnos.isEmpty() && !turnos.get(0).deUsuario()) turnos.remove(0);   // Claude exige empezar por el usuario
        if (turnos.size() > TURNOS_DE_MEMORIA) turnos = new ArrayList<>(turnos.subList(turnos.size() - TURNOS_DE_MEMORIA, turnos.size()));
        while (!turnos.get(0).deUsuario()) turnos.remove(0);

        AsistenteContextoService.Contexto ctx = new AsistenteContextoService.Contexto("", List.of(), List.of(), List.of());
        // Conversación nueva: la IA le pone un título que resume la petición, en paralelo.
        // Mientras tanto queda el provisional (las primeras palabras del mensaje).
        java.util.concurrent.CompletableFuture<String> tituloIa = nueva && !mensaje.isEmpty() && agenteHabilitado
                ? java.util.concurrent.CompletableFuture.supplyAsync(() -> asistenteIaService.titularConversacion(mensaje), titulos)
                : null;

        AsistenteIaService.Resultado r;
        String clave = claveEnCurso(s);
        enCurso.put(clave, conv.getId());
        try {
            r = agenteHabilitado
                    ? asistenteIaService.conversarConHerramientas(s, turnos, herramientas, confirmaciones)
                    : new AsistenteIaService.Resultado("El agente está deshabilitado en este entorno.", false);
            if (r.ok()) mensajeRepository.save(new AsistenteMensaje(conv.getId(), AsistenteMensaje.ASISTENTE, r.texto()));
        } finally {
            enCurso.remove(clave, conv.getId());
        }
        conversacionRepository.tocar(conv.getId(), LocalDateTime.now());
        String titulo = conv.getTitulo();
        if (tituloIa != null) {
            Long id = conv.getId();
            String provisional = conv.getTitulo();
            if (tituloIa.isDone()) {
                titulo = aplicarTituloIa(id, provisional, tituloIa.getNow(null));
            } else {
                // Si aún no llega, se guarda cuando llegue; el navegador recarga el historial.
                tituloIa.thenAccept(t -> aplicarTituloIa(id, provisional, t));
            }
        }
        return new Respuesta(conv.getId(), titulo, nueva, r.texto(), r.ok(),
                LocalDateTime.now().format(HORA), r.ok() ? ctx.acciones() : List.of(), adjuntosVista);
    }

    // ───────────────────────── helpers ─────────────────────────

    /** Tipo MIME aceptado o null. Los de texto se reconocen por extensión (los navegadores no siempre envían el tipo). */
    private static String tipoDe(MultipartFile f) {
        String ct = f.getContentType() == null ? "" : f.getContentType().toLowerCase(Locale.ROOT);
        if (ct.equals("image/jpg")) ct = "image/jpeg";
        if (TIPOS_BINARIOS.contains(ct)) return ct;
        String n = f.getOriginalFilename() == null ? "" : f.getOriginalFilename().toLowerCase(Locale.ROOT);
        String ext = n.contains(".") ? n.substring(n.lastIndexOf('.') + 1) : "";
        if (ct.startsWith("text/") || EXT_TEXTO.contains(ext)) return "text/plain";
        return switch (ext) { case "png" -> "image/png"; case "jpg", "jpeg" -> "image/jpeg"; case "webp" -> "image/webp";
            case "gif" -> "image/gif"; case "pdf" -> "application/pdf"; default -> null; };
    }

    private static String nombreSeguro(String original) {
        String n = original == null ? "archivo" : original.replaceAll("^.*[\\\\/]", "").replaceAll("[\\p{Cntrl}\"]", "").trim();
        if (n.isEmpty()) n = "archivo";
        return n.length() > 200 ? n.substring(n.length() - 200) : n;
    }

    private static AdjuntoVista vista(Long id, String nombre, String tipo, int bytes) {
        String tam = bytes < 1024 ? bytes + " B" : bytes < 1024 * 1024 ? Math.round(bytes / 1024.0) + " KB"
                : String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024));
        return new AdjuntoVista(id, nombre, tipo, tam, tipo.startsWith("image/"));
    }

    /** Une turnos seguidos del mismo autor (p. ej. si una respuesta falló y no se guardó). */
    private static void agregar(List<AsistenteIaService.Turno> turnos, boolean deUsuario, String texto) {
        if (!turnos.isEmpty() && turnos.get(turnos.size() - 1).deUsuario() == deUsuario) {
            AsistenteIaService.Turno ultimo = turnos.remove(turnos.size() - 1);
            turnos.add(new AsistenteIaService.Turno(deUsuario, ultimo.texto() + "\n\n" + texto));
        } else {
            turnos.add(new AsistenteIaService.Turno(deUsuario, texto));
        }
    }

    private AsistenteConversacion exigirPropia(Long id, UsuarioSesion s) {
        AsistenteConversacion c = conversacionRepository.findById(id)
                .orElseThrow(() -> new com.skillbridge.ai.util.RecursoNoEncontradoException("Esa conversación ya no existe."));
        if (!c.getUsuarioId().equals(s.getUsuarioId()) || !c.getRol().equals(rolDe(s)) || !alcanceValido(id,s)) throw new com.skillbridge.ai.util.RecursoNoEncontradoException("Esa conversación ya no existe.");
        return c;
    }

    private boolean alcanceValido(Long id, UsuarioSesion s) {
        return alcanceRepository.findById(id)
                .map(a -> a.getFirma().equals(politica.firma(s)))
                .orElse(false);
    }

    /** Guarda el título de la IA si la persona no renombró la conversación; devuelve el título vigente. */
    private String aplicarTituloIa(Long id, String provisional, String sugerido) {
        String limpio = limpiarTitulo(sugerido);
        try {
            if (limpio != null && conversacionRepository.titularSiNoCambio(id, provisional, limpio) == 1) return limpio;
            return conversacionRepository.findById(id).map(AsistenteConversacion::getTitulo).orElse(provisional);
        } catch (RuntimeException e) {
            return provisional;   // la conversación pudo eliminarse mientras tanto
        }
    }

    /** Una línea, sin comillas, Markdown ni punto final, de hasta 60 caracteres. */
    static String limpiarTitulo(String t) {
        if (t == null) return null;
        String linea = t.strip().split("\\R", 2)[0]
                .replaceAll("^(?i)t[ií]tulo\\s*:\\s*", "")
                .replaceAll("[\"'“”‘’`*#_]", "")
                .replaceAll("\\s+", " ")
                .replaceAll("[.。:;,\\s]+$", "")
                .strip();
        if (linea.isEmpty()) return null;
        return linea.length() > 60 ? linea.substring(0, 57).strip() + "…" : linea;
    }

    private static String titulo(String mensaje) {
        String t = mensaje.replaceAll("\\s+", " ").trim();
        return t.length() > 60 ? t.substring(0, 57).trim() + "…" : t;
    }

    private static String cuando(LocalDateTime f) {
        LocalDate d = f.toLocalDate(), hoy = LocalDate.now();
        if (d.equals(hoy)) return "Hoy " + f.format(HORA);
        if (d.equals(hoy.minusDays(1))) return "Ayer " + f.format(HORA);
        return f.format(FECHA);
    }
}
