package com.skillbridge.ai.service;

import com.skillbridge.ai.dto.HiloDetalle;
import com.skillbridge.ai.dto.HiloFila;
import com.skillbridge.ai.dto.HiloResumen;
import com.skillbridge.ai.dto.ProyectoOpcion;
import com.skillbridge.ai.dto.RespuestaFila;
import com.skillbridge.ai.model.Asignacion;
import com.skillbridge.ai.model.ForoPublicacion;
import com.skillbridge.ai.model.Proyecto;
import com.skillbridge.ai.repository.AsignacionRepository;
import com.skillbridge.ai.repository.ForoPublicacionRepository;
import com.skillbridge.ai.util.OperacionInvalidaException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Foros (RF06) sobre "foro_publicaciones": una sola tabla autorreferencial,
 * cada post cuelga obligatoriamente de un proyecto (proyecto_id NOT NULL en
 * el esquema). Como el mockup mostraba un foro único con "categorías"
 * genéricas (Backend/Frontend...) que no existen como catálogo en v4, la
 * lista de "Categorías" se reinterpreta como "tus proyectos" (la única
 * dimensión real de agrupación que ofrece el esquema) - declarado en el
 * README.
 *
 * El resumen generado por IA y el conteo de "útil" del mockup original NO
 * se implementan: el primero requeriría un motor de IA (fuera de alcance
 * por pedido explícito) y el segundo no tiene columna en el esquema.
 */
@Service
public class ForoService {

    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ForoPublicacionRepository foroPublicacionRepository;
    private final AsignacionRepository asignacionRepository;
    private final NotificacionService notificacionService;
    private final AuditoriaService auditoriaService;

    public ForoService(ForoPublicacionRepository foroPublicacionRepository, AsignacionRepository asignacionRepository,
                        NotificacionService notificacionService, AuditoriaService auditoriaService) {
        this.foroPublicacionRepository = foroPublicacionRepository;
        this.asignacionRepository = asignacionRepository;
        this.notificacionService = notificacionService;
        this.auditoriaService = auditoriaService;
    }

    private List<Long> proyectosDelPerfil(Long perfilId) {
        return asignacionRepository.listarPorPerfil(perfilId).stream()
                .map(Asignacion::getProyectoId)
                .distinct()
                .collect(Collectors.toList());
    }

    public List<ProyectoOpcion> misProyectosConHilos(Long perfilId) {
        List<Asignacion> asignaciones = asignacionRepository.listarPorPerfil(perfilId);
        Map<Long, String> nombresPorProyecto = new LinkedHashMap<>();
        for (Asignacion a : asignaciones) {
            nombresPorProyecto.putIfAbsent(a.getProyectoId(), a.getProyecto().getNombre());
        }
        return nombresPorProyecto.entrySet().stream()
                .map(e -> new ProyectoOpcion(e.getKey(), e.getValue(),
                        foroPublicacionRepository.countByProyectoIdAndPublicacionPadreIdIsNull(e.getKey())))
                .collect(Collectors.toList());
    }

    public List<HiloFila> listarHilosDeMisProyectos(Long perfilId) {
        List<Long> proyectoIds = proyectosDelPerfil(perfilId);
        if (proyectoIds.isEmpty()) return List.of();
        return foroPublicacionRepository.listarHilosDeProyectos(proyectoIds).stream()
                .map(this::aFila)
                .sorted((a, b) -> Boolean.compare(b.isEsFijado(), a.isEsFijado()))
                .collect(Collectors.toList());
    }

    private HiloFila aFila(ForoPublicacion f) {
        long numRespuestas = foroPublicacionRepository.countByPublicacionPadreId(f.getId());
        boolean tieneAceptada = !foroPublicacionRepository.findByPublicacionPadreId(f.getId()).stream()
                .filter(r -> Boolean.TRUE.equals(r.getEsSolucion())).toList().isEmpty();
        return new HiloFila(f.getId(), f.getTitulo(), f.getProyectoId(), f.getProyecto().getNombre(),
                f.getAutor().getUsuario().getNombreCompleto(),
                f.getFechaPublicacion() != null ? f.getFechaPublicacion().format(FORMATO) : "",
                numRespuestas, f.getNumVistas(), tieneAceptada, Boolean.TRUE.equals(f.getEsFijado()));
    }

    @Transactional
    public ForoPublicacion crearHilo(Long perfilId, Long proyectoId, String titulo, String contenido) {
        if (titulo == null || titulo.isBlank()) {
            throw new OperacionInvalidaException("Escribe un título para tu hilo.");
        }
        if (contenido == null || contenido.isBlank()) {
            throw new OperacionInvalidaException("Escribe un mensaje para tu hilo.");
        }
        if (!proyectosDelPerfil(perfilId).contains(proyectoId)) {
            throw new OperacionInvalidaException("Solo puedes publicar en proyectos donde tienes una asignación.");
        }
        ForoPublicacion f = new ForoPublicacion();
        f.setProyectoId(proyectoId);
        f.setAutorId(perfilId);
        f.setTitulo(titulo.trim());
        f.setContenido(contenido.trim());
        return foroPublicacionRepository.save(f);
    }

    @Transactional
    public HiloDetalle obtenerDetalle(Long hiloId, Long viewerPerfilId, boolean viewerEsAdmin) {
        ForoPublicacion hilo = foroPublicacionRepository.buscarHiloConDetalle(hiloId)
                .orElseThrow(() -> new OperacionInvalidaException("El hilo ya no existe."));
        hilo.setNumVistas(hilo.getNumVistas() + 1);
        foroPublicacionRepository.save(hilo);

        List<ForoPublicacion> respuestasCrudo = foroPublicacionRepository.listarRespuestas(hiloId);
        List<RespuestaFila> respuestas = respuestasCrudo.stream()
                .map(r -> new RespuestaFila(r.getId(), r.getAutor().getUsuario().getNombreCompleto(),
                        r.getAutor().getCargo(), r.getFechaPublicacion() != null ? r.getFechaPublicacion().format(FORMATO) : "",
                        r.getContenido(), Boolean.TRUE.equals(r.getEsSolucion())))
                .collect(Collectors.toList());

        Set<String> participantes = new LinkedHashSet<>();
        participantes.add(hilo.getAutor().getUsuario().getNombreCompleto());
        respuestasCrudo.forEach(r -> participantes.add(r.getAutor().getUsuario().getNombreCompleto()));
        List<String> participantesIniciales = participantes.stream()
                .map(com.skillbridge.ai.dto.InicialesUtil::de)
                .collect(Collectors.toList());

        List<HiloResumen> relacionados = foroPublicacionRepository.listarRelacionados(hilo.getProyectoId(), hiloId).stream()
                .limit(4)
                .map(r -> new HiloResumen(r.getId(), r.getTitulo(), foroPublicacionRepository.countByPublicacionPadreId(r.getId())))
                .collect(Collectors.toList());

        boolean permiteMarcarSolucion = viewerEsAdmin || hilo.getAutorId().equals(viewerPerfilId);

        return new HiloDetalle(hilo.getId(), hilo.getTitulo(), hilo.getProyectoId(), hilo.getProyecto().getNombre(),
                hilo.getAutor().getUsuario().getNombreCompleto(),
                hilo.getFechaPublicacion() != null ? hilo.getFechaPublicacion().format(FORMATO) : "",
                hilo.getContenido(), hilo.getNumVistas(), permiteMarcarSolucion, respuestas, participantesIniciales, relacionados);
    }

    @Transactional
    public void responder(Long hiloId, Long perfilId, String contenido) {
        if (contenido == null || contenido.isBlank()) {
            throw new OperacionInvalidaException("Escribe una respuesta antes de publicar.");
        }
        ForoPublicacion hilo = foroPublicacionRepository.buscarHiloConDetalle(hiloId)
                .orElseThrow(() -> new OperacionInvalidaException("El hilo ya no existe."));

        ForoPublicacion respuesta = new ForoPublicacion();
        respuesta.setProyectoId(hilo.getProyectoId());
        respuesta.setAutorId(perfilId);
        respuesta.setPublicacionPadreId(hiloId);
        respuesta.setContenido(contenido.trim());
        foroPublicacionRepository.save(respuesta);

        if (!hilo.getAutorId().equals(perfilId)) {
            notificacionService.crear(hilo.getAutorId(), "foro_respuesta", "Respuesta en tu hilo",
                    "Alguien respondió en \"" + hilo.getTitulo() + "\".", "foro-hilo.html?id=" + hiloId);
        }
    }

    @Transactional
    public void marcarSolucion(Long respuestaId, Long actorPerfilId, boolean actorEsAdmin) {
        ForoPublicacion respuesta = foroPublicacionRepository.findById(respuestaId)
                .orElseThrow(() -> new OperacionInvalidaException("La respuesta ya no existe."));
        Long hiloId = respuesta.getPublicacionPadreId();
        if (hiloId == null) {
            throw new OperacionInvalidaException("Solo se puede marcar como solución una respuesta, no el mensaje inicial.");
        }
        ForoPublicacion hilo = foroPublicacionRepository.findById(hiloId)
                .orElseThrow(() -> new OperacionInvalidaException("El hilo ya no existe."));
        if (!actorEsAdmin && !hilo.getAutorId().equals(actorPerfilId)) {
            throw new OperacionInvalidaException("Solo quien abrió el hilo (o un administrador) puede marcar la solución.");
        }
        foroPublicacionRepository.findByPublicacionPadreId(hiloId).forEach(r -> {
            if (Boolean.TRUE.equals(r.getEsSolucion())) {
                r.setEsSolucion(false);
                foroPublicacionRepository.save(r);
            }
        });
        respuesta.setEsSolucion(true);
        foroPublicacionRepository.save(respuesta);
    }

    /** Fija o desfija un hilo raiz (aparece primero en la lista). Usado por el Project Manager. */
    @Transactional
    public void establecerFijado(Long hiloId, boolean valor) {
        ForoPublicacion hilo = foroPublicacionRepository.findById(hiloId)
                .orElseThrow(() -> new OperacionInvalidaException("El hilo ya no existe."));
        if (hilo.getPublicacionPadreId() != null) {
            throw new OperacionInvalidaException("Solo se puede fijar un hilo, no una respuesta.");
        }
        hilo.setEsFijado(valor);
        foroPublicacionRepository.save(hilo);
    }
}
