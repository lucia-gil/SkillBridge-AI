package com.skillbridge.ai.service;

import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.model.AsistenteConversacion;
import com.skillbridge.ai.model.AsistenteMensaje;
import com.skillbridge.ai.repository.AsistenteConversacionRepository;
import com.skillbridge.ai.repository.AsistenteMensajeRepository;
import com.skillbridge.ai.util.OperacionInvalidaException;
import com.skillbridge.ai.util.Roles;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

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

    public record ConversacionVista(Long id, String titulo, String cuando) {}
    public record MensajeVista(Long id, boolean deUsuario, String texto, String hora) {}
    public record Respuesta(Long conversacionId, String titulo, boolean nueva, String respuesta, boolean ok,
                            String hora, List<AsistenteContextoService.Accion> acciones) {}

    private final AsistenteConversacionRepository conversacionRepository;
    private final AsistenteMensajeRepository mensajeRepository;
    private final AsistenteContextoService contextoService;
    private final AsistenteIaService asistenteIaService;

    public AsistenteConversacionService(AsistenteConversacionRepository conversacionRepository,
                                        AsistenteMensajeRepository mensajeRepository,
                                        AsistenteContextoService contextoService, AsistenteIaService asistenteIaService) {
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
        return conversacionRepository.findTop30ByUsuarioIdAndRolOrderByFechaActualizacionDesc(s.getUsuarioId(), rolDe(s)).stream()
                .map(c -> new ConversacionVista(c.getId(), c.getTitulo(), cuando(c.getFechaActualizacion()))).toList();
    }

    @Transactional(readOnly = true)
    public List<MensajeVista> mensajes(Long conversacionId, UsuarioSesion s) {
        exigirPropia(conversacionId, s);
        return mensajeRepository.findByConversacionIdOrderByIdAsc(conversacionId).stream()
                .map(m -> new MensajeVista(m.getId(), m.esDelUsuario(), m.getContenido(), m.getFecha().format(HORA))).toList();
    }

    @Transactional
    public void eliminar(Long conversacionId, UsuarioSesion s) {
        conversacionRepository.delete(exigirPropia(conversacionId, s));
    }

    @Transactional
    public Respuesta enviar(UsuarioSesion s, Long conversacionId, String texto) {
        String mensaje = texto == null ? "" : texto.trim();
        if (mensaje.isEmpty()) throw new OperacionInvalidaException("Escribe un mensaje antes de enviar.");
        if (mensaje.length() > MAX_CARACTERES) throw new OperacionInvalidaException("El mensaje es demasiado largo (máximo " + MAX_CARACTERES + " caracteres).");
        String rol = rolDe(s);

        boolean nueva = conversacionId == null;
        AsistenteConversacion conv = nueva
                ? conversacionRepository.save(new AsistenteConversacion(s.getUsuarioId(), rol, titulo(mensaje)))
                : exigirPropia(conversacionId, s);

        // Memoria: los últimos turnos de esta conversación (antes del mensaje nuevo)
        List<AsistenteMensaje> previos = new ArrayList<>(mensajeRepository.findTop12ByConversacionIdOrderByIdDesc(conv.getId()));
        Collections.reverse(previos);
        mensajeRepository.save(new AsistenteMensaje(conv.getId(), AsistenteMensaje.USUARIO, mensaje));

        List<AsistenteIaService.Turno> turnos = new ArrayList<>();
        for (AsistenteMensaje m : previos) agregar(turnos, m.esDelUsuario(), m.getContenido());
        agregar(turnos, true, mensaje);
        while (!turnos.isEmpty() && !turnos.get(0).deUsuario()) turnos.remove(0);   // Gemini exige empezar por el usuario
        if (turnos.size() > TURNOS_DE_MEMORIA) turnos = new ArrayList<>(turnos.subList(turnos.size() - TURNOS_DE_MEMORIA, turnos.size()));
        while (!turnos.get(0).deUsuario()) turnos.remove(0);

        AsistenteContextoService.Contexto ctx = contextoService.construir(s, rol, mensaje);
        AsistenteIaService.Resultado r = asistenteIaService.conversar(prompt(s, rol, ctx), turnos, 900);
        if (r.ok()) mensajeRepository.save(new AsistenteMensaje(conv.getId(), AsistenteMensaje.ASISTENTE, r.texto()));
        conv.tocar();
        conversacionRepository.save(conv);
        return new Respuesta(conv.getId(), conv.getTitulo(), nueva, r.texto(), r.ok(),
                LocalDateTime.now().format(HORA), r.ok() ? ctx.acciones() : List.of());
    }

    // ───────────────────────── helpers ─────────────────────────

    /** Une turnos seguidos del mismo autor (p. ej. si una respuesta falló y no se guardó). */
    private static void agregar(List<AsistenteIaService.Turno> turnos, boolean deUsuario, String texto) {
        if (!turnos.isEmpty() && turnos.get(turnos.size() - 1).deUsuario() == deUsuario) {
            AsistenteIaService.Turno ultimo = turnos.remove(turnos.size() - 1);
            turnos.add(new AsistenteIaService.Turno(deUsuario, ultimo.texto() + "\n\n" + texto));
        } else {
            turnos.add(new AsistenteIaService.Turno(deUsuario, texto));
        }
    }

    private String prompt(UsuarioSesion s, String rol, AsistenteContextoService.Contexto ctx) {
        return "Eres el Asistente IA de SkillBridge AI, la plataforma de gestión de talento y proyectos de NexaCorp. "
                + "Hablas con " + s.getNombreCompleto() + ", que usa la vista " + Roles.etiqueta(rol) + ". "
                + "Hoy es " + LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy", new Locale("es", "PE"))) + ".\n\n"
                + "DATOS ACTUALES DEL USUARIO (leídos de la base de datos en este momento):\n" + ctx.texto() + "\n"
                + "REGLAS:\n"
                + "- Responde en español, claro y directo, como un colega experto. Máximo 8 líneas salvo que pidan detalle.\n"
                + "- Usa SOLO los datos de arriba para hablar del usuario, su equipo o sus proyectos. Si algo no aparece, dilo; no inventes nombres, cifras ni fechas.\n"
                + "- Puedes usar viñetas que empiecen con \"- \" y **negritas** para lo importante.\n"
                + "- Recuerda lo conversado antes en esta conversación y úsalo.\n"
                + "- Si piden candidatos para un proyecto, usa el ranking de AI Talent Matching de los datos (si está) y recuerda que la decisión final es de una persona.\n"
                + "- Solo ayudas con SkillBridge AI, gestión de proyectos, talento y desarrollo de software. Si preguntan otra cosa, dilo amablemente y ofrece ayuda dentro de ese alcance.";
    }

    private AsistenteConversacion exigirPropia(Long id, UsuarioSesion s) {
        AsistenteConversacion c = conversacionRepository.findById(id)
                .orElseThrow(() -> new OperacionInvalidaException("Esa conversación ya no existe."));
        if (!c.getUsuarioId().equals(s.getUsuarioId())) throw new OperacionInvalidaException("Esa conversación ya no existe.");
        return c;
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
