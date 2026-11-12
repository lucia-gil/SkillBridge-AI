package com.skillbridge.ai.service;

import com.skillbridge.ai.dto.CitaForo;
import com.skillbridge.ai.dto.ForoAdjuntoInfo;
import com.skillbridge.ai.dto.HiloDetalle;
import com.skillbridge.ai.dto.HiloFila;
import com.skillbridge.ai.dto.HiloResumen;
import com.skillbridge.ai.dto.InicialesUtil;
import com.skillbridge.ai.dto.ParticipanteFila;
import com.skillbridge.ai.dto.ProyectoOpcion;
import com.skillbridge.ai.dto.RespuestaFila;
import com.skillbridge.ai.dto.TopicoFila;
import com.skillbridge.ai.model.Asignacion;
import com.skillbridge.ai.model.ForoAdjunto;
import com.skillbridge.ai.model.ForoDestacado;
import com.skillbridge.ai.model.ForoDestacadoId;
import com.skillbridge.ai.model.ForoPublicacion;
import com.skillbridge.ai.model.ForoTopico;
import com.skillbridge.ai.repository.AsignacionRepository;
import com.skillbridge.ai.repository.ConfiguracionGlobalRepository;
import com.skillbridge.ai.repository.ForoAdjuntoRepository;
import com.skillbridge.ai.repository.ForoDestacadoRepository;
import com.skillbridge.ai.repository.ForoPublicacionRepository;
import com.skillbridge.ai.repository.ForoTopicoRepository;
import com.skillbridge.ai.repository.PerfilRepository;
import com.skillbridge.ai.repository.ProyectoRepository;
import com.skillbridge.ai.util.OperacionInvalidaException;
import com.skillbridge.ai.util.Roles;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Foros (RF06) sobre "foro_publicaciones": una sola tabla autorreferencial,
 * cada post cuelga obligatoriamente de un proyecto (proyecto_id NOT NULL en
 * el esquema). La lista "Tus proyectos" agrupa por proyecto, y desde la v6
 * (skillbridge_db.sql) además cada hilo tiene un TÓPICO (foro_topicos)
 * y etiquetas, para usar el foro como centro de ayuda.
 *
 * v6 agrega:
 *  - Tópicos y etiquetas asignados automáticamente (ForoClasificadorService)
 *    o elegidos por quien abre el hilo.
 *  - Responder a un mensaje puntual (respuesta_a_id): el hilo sigue plano.
 *  - Huella de edición (editado, num_ediciones, fecha_edicion) y límite de
 *    ediciones (configuracion_global 'foro_max_ediciones'); el texto anterior
 *    queda en auditoria_logs.
 *  - Destacados ("me fue útil") y adjuntos (archivos y enlaces).
 *  - Permisos más estrictos: solo miembros del proyecto ven un hilo, y la
 *    moderación (marcar solución, eliminar mensajes ajenos, fijar) es del
 *    Administrador o del PM ACTIVO de ESE proyecto.
 *
 * El resumen generado por IA del mockup original sigue sin implementarse
 * (no hay un LLM integrado en el proyecto).
 */
@Service
public class ForoService {

    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter FORMATO_ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    public static final String CLAVE_MAX_EDICIONES = "foro_max_ediciones";
    private static final int MAX_EDICIONES_POR_DEFECTO = 3;

    // Adjuntos: mismo límite por archivo que spring.servlet.multipart.max-file-size.
    public static final int MAX_ARCHIVOS_POR_MENSAJE = 3;
    public static final int MAX_ENLACES_POR_MENSAJE = 3;
    public static final long MAX_BYTES_POR_ARCHIVO = 5L * 1024 * 1024;
    private static final int MAX_LARGO_TITULO = 200;
    private static final int MAX_LARGO_CONTENIDO = 10_000;

    /**
     * Extensiones permitidas y el tipo MIME con el que se guardan y sirven.
     * El tipo se decide por la extensión (no por lo que declara el navegador)
     * y no se aceptan SVG/HTML: se sirven inline y podrían ejecutar scripts.
     */
    private static final Map<String, String> MIME_POR_EXTENSION = Map.ofEntries(
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("gif", "image/gif"),
            Map.entry("webp", "image/webp"),
            Map.entry("pdf", "application/pdf"),
            Map.entry("doc", "application/msword"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("xls", "application/vnd.ms-excel"),
            Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            Map.entry("ppt", "application/vnd.ms-powerpoint"),
            Map.entry("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
            Map.entry("txt", "text/plain"),
            Map.entry("csv", "text/csv"),
            Map.entry("md", "text/markdown"),
            Map.entry("zip", "application/zip")
    );

    private final ForoPublicacionRepository foroPublicacionRepository;
    private final AsignacionRepository asignacionRepository;
    private final NotificacionService notificacionService;
    private final AuditoriaService auditoriaService;
    private final ProyectoRepository proyectoRepository;
    private final ForoTopicoRepository foroTopicoRepository;
    private final ForoDestacadoRepository foroDestacadoRepository;
    private final ForoAdjuntoRepository foroAdjuntoRepository;
    private final ConfiguracionGlobalRepository configuracionGlobalRepository;
    private final PerfilRepository perfilRepository;
    private final ForoClasificadorService clasificador;
    private final AsistenteIaService asistenteIaService;

    public ForoService(ForoPublicacionRepository foroPublicacionRepository, AsignacionRepository asignacionRepository,
                        NotificacionService notificacionService, AuditoriaService auditoriaService,
                        ProyectoRepository proyectoRepository, ForoTopicoRepository foroTopicoRepository,
                        ForoDestacadoRepository foroDestacadoRepository, ForoAdjuntoRepository foroAdjuntoRepository,
                        ConfiguracionGlobalRepository configuracionGlobalRepository, PerfilRepository perfilRepository,
                        ForoClasificadorService clasificador, AsistenteIaService asistenteIaService) {
        this.foroPublicacionRepository = foroPublicacionRepository;
        this.asignacionRepository = asignacionRepository;
        this.notificacionService = notificacionService;
        this.auditoriaService = auditoriaService;
        this.proyectoRepository = proyectoRepository;
        this.foroTopicoRepository = foroTopicoRepository;
        this.foroDestacadoRepository = foroDestacadoRepository;
        this.foroAdjuntoRepository = foroAdjuntoRepository;
        this.configuracionGlobalRepository = configuracionGlobalRepository;
        this.perfilRepository = perfilRepository;
        this.clasificador = clasificador;
        this.asistenteIaService = asistenteIaService;
    }

    // ═════════════════════════════ Permisos ═════════════════════════════

    /** Restriccion de foros: un proyecto completado o cancelado queda en solo lectura. */
    private void exigirForoAbierto(Long proyectoId) {
        if (!foroAbierto(proyectoId)) {
            String estado = proyectoRepository.findById(proyectoId).map(p -> p.getEstado()).orElse("cerrado");
            throw new OperacionInvalidaException("El foro de este proyecto esta cerrado (proyecto " + estado + "). Solo puedes leerlo.");
        }
    }

    private boolean foroAbierto(Long proyectoId) {
        return proyectoRepository.findById(proyectoId)
                .map(p -> !"completado".equals(p.getEstado()) && !"cancelado".equals(p.getEstado()))
                .orElse(false);
    }

    private List<Long> proyectosDelPerfil(Long perfilId) {
        return asignacionRepository.listarPorPerfil(perfilId).stream()
                .map(Asignacion::getProyectoId)
                .distinct()
                .collect(Collectors.toList());
    }

    /** Proyectos donde el perfil es PM con asignación ACTIVA (moderador de ese foro). */
    private Set<Long> proyectosQueModera(Long perfilId) {
        return asignacionRepository.listarPorPerfil(perfilId).stream()
                .filter(a -> Roles.PROJECT_MANAGER.equals(a.getRolEnProyecto()) && "activa".equals(a.getEstado()))
                .map(Asignacion::getProyectoId)
                .collect(Collectors.toSet());
    }

    private boolean esModerador(Long perfilId, Long proyectoId, boolean esAdmin) {
        return esAdmin || asignacionRepository.existsByProyectoIdAndPerfilIdAndRolEnProyectoAndEstado(
                proyectoId, perfilId, Roles.PROJECT_MANAGER, "activa");
    }

    private void exigirMiembro(Long perfilId, Long proyectoId, boolean esAdmin) {
        if (!esAdmin && !proyectosDelPerfil(perfilId).contains(proyectoId)) {
            throw new OperacionInvalidaException("No tienes acceso a este foro: no participas en ese proyecto.");
        }
    }

    public int maxEdiciones() {
        return configuracionGlobalRepository.findById(CLAVE_MAX_EDICIONES)
                .map(c -> {
                    try {
                        return Integer.parseInt(c.getValor().trim());
                    } catch (Exception e) {
                        return MAX_EDICIONES_POR_DEFECTO;
                    }
                })
                .orElse(MAX_EDICIONES_POR_DEFECTO);
    }

    private Long usuarioIdDePerfil(Long perfilId) {
        return perfilRepository.findById(perfilId).map(p -> p.getUsuarioId()).orElse(null);
    }

    // ═════════════════════════════ Catálogos ═════════════════════════════

    public List<TopicoFila> topicos() {
        return foroTopicoRepository.findAllByOrderByOrdenAscNombreAsc().stream()
                .map(ForoService::aTopicoFila)
                .collect(Collectors.toList());
    }

    /** Tópicos con cuántos hilos de la lista caen en cada uno (para los filtros). */
    public List<TopicoFila> topicosConConteo(List<HiloFila> hilos) {
        Map<String, Long> conteo = hilos.stream()
                .collect(Collectors.groupingBy(HiloFila::getTopicoCodigo, Collectors.counting()));
        List<TopicoFila> lista = topicos();
        lista.forEach(t -> t.setNumHilos(conteo.getOrDefault(t.getCodigo(), 0L)));
        return lista;
    }

    private static TopicoFila aTopicoFila(ForoTopico t) {
        String palabras = String.join(",", ForoClasificadorService.palabrasClave(t));
        return new TopicoFila(t.getId(), t.getCodigo(), t.getNombre(), t.getDescripcion(),
                t.getColor(), t.getIcono(), palabras);
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

    /** Proyectos donde hoy se puede publicar (para el selector de "Nuevo hilo"). */
    public List<ProyectoOpcion> proyectosDondePublicar(Long perfilId) {
        return misProyectosConHilos(perfilId).stream()
                .filter(p -> foroAbierto(p.id()))
                .collect(Collectors.toList());
    }

    // ═════════════════════════════ Lista de hilos ═════════════════════════════

    public List<HiloFila> listarHilosDeMisProyectos(Long perfilId) {
        List<Long> proyectoIds = proyectosDelPerfil(perfilId);
        if (proyectoIds.isEmpty()) return List.of();
        List<ForoPublicacion> hilos = foroPublicacionRepository.listarHilosDeProyectos(proyectoIds);
        if (hilos.isEmpty()) return List.of();

        List<Long> hiloIds = hilos.stream().map(ForoPublicacion::getId).toList();
        Map<Long, List<ForoPublicacion>> respuestasPorHilo = foroPublicacionRepository.listarRespuestasDeHilos(hiloIds).stream()
                .collect(Collectors.groupingBy(ForoPublicacion::getPublicacionPadreId, LinkedHashMap::new, Collectors.toList()));

        Set<Long> todasLasIds = new HashSet<>(hiloIds);
        respuestasPorHilo.values().forEach(l -> l.forEach(r -> todasLasIds.add(r.getId())));
        Map<Long, Long> destacados = contarDestacados(hiloIds);
        Map<Long, Integer> adjuntosPorPublicacion = new HashMap<>();
        foroAdjuntoRepository.listarInfo(todasLasIds)
                .forEach(a -> adjuntosPorPublicacion.merge(a.getPublicacionId(), 1, Integer::sum));

        Set<Long> modera = proyectosQueModera(perfilId);
        Map<Long, Boolean> abiertos = new HashMap<>();

        List<HiloFila> filas = new ArrayList<>();
        for (ForoPublicacion f : hilos) {
            List<ForoPublicacion> respuestas = respuestasPorHilo.getOrDefault(f.getId(), List.of());
            boolean tieneAceptada = respuestas.stream().anyMatch(r -> Boolean.TRUE.equals(r.getEsSolucion()));
            HiloFila fila = new HiloFila(f.getId(), f.getTitulo(), f.getProyectoId(), f.getProyecto().getNombre(),
                    nombreDe(f), formatear(f.getFechaPublicacion()),
                    respuestas.size(), f.getNumVistas(), tieneAceptada, Boolean.TRUE.equals(f.getEsFijado()));

            ForoTopico topico = f.getTopico();
            if (topico != null) {
                fila.setTopicoCodigo(topico.getCodigo());
                fila.setTopicoNombre(topico.getNombre());
                fila.setTopicoColor(topico.getColor());
                fila.setTopicoIcono(topico.getIcono());
            }
            fila.setEtiquetas(ForoClasificadorService.leerEtiquetasJson(f.getEtiquetas()));
            fila.setExtracto(abreviar(f.getContenido(), 220));
            fila.setNumDestacados(destacados.getOrDefault(f.getId(), 0L));

            int numAdjuntos = adjuntosPorPublicacion.getOrDefault(f.getId(), 0);
            for (ForoPublicacion r : respuestas) numAdjuntos += adjuntosPorPublicacion.getOrDefault(r.getId(), 0);
            fila.setNumAdjuntos(numAdjuntos);

            StringBuilder texto = new StringBuilder(f.getContenido() != null ? f.getContenido() : "");
            Set<String> participantes = new LinkedHashSet<>();
            participantes.add(nombreDe(f));
            for (ForoPublicacion r : respuestas) {
                if (texto.length() < 4000) texto.append(' ').append(r.getContenido());
                participantes.add(nombreDe(r));
            }
            fila.setTextoBusqueda(abreviar(texto.toString(), 4000));
            fila.setParticipantesIniciales(participantes.stream().limit(4).map(InicialesUtil::de).toList());
            fila.setNumParticipantes(participantes.size());

            ForoPublicacion ultima = respuestas.isEmpty() ? f : respuestas.get(respuestas.size() - 1);
            fila.setFechaIso(iso(f.getFechaPublicacion()));
            fila.setUltimaActividadIso(iso(ultima.getFechaPublicacion()));
            fila.setUltimaActividadLabel(formatear(ultima.getFechaPublicacion()));
            fila.setUltimoAutorNombre(nombreDe(ultima));

            fila.setEsMio(f.getAutorId().equals(perfilId));
            fila.setPuedeFijar(modera.contains(f.getProyectoId()));
            fila.setForoAbierto(abiertos.computeIfAbsent(f.getProyectoId(), this::foroAbierto));
            filas.add(fila);
        }
        // Fijados primero; dentro de cada grupo, el orden de la consulta (más recientes primero).
        filas.sort((a, b) -> Boolean.compare(b.isEsFijado(), a.isEsFijado()));
        return filas;
    }

    // ═════════════════════════════ Crear y responder ═════════════════════════════

    /** Compatibilidad: crear un hilo solo con texto (tópico y etiquetas automáticos). */
    @Transactional
    public ForoPublicacion crearHilo(Long perfilId, Long proyectoId, String titulo, String contenido) {
        return crearHilo(perfilId, proyectoId, titulo, contenido, null, null, null, null);
    }

    /**
     * @param topicoCodigo  código del tópico elegido; null/vacío/"auto" = lo asigna el clasificador.
     * @param etiquetasTexto etiquetas separadas por coma; null/vacío = se sugieren del catálogo de habilidades.
     */
    @Transactional
    public ForoPublicacion crearHilo(Long perfilId, Long proyectoId, String titulo, String contenido,
                                      String topicoCodigo, String etiquetasTexto,
                                      List<MultipartFile> archivos, List<String> enlaces) {
        if (titulo == null || titulo.isBlank()) {
            throw new OperacionInvalidaException("Escribe un título para tu hilo.");
        }
        if (titulo.trim().length() > MAX_LARGO_TITULO) {
            throw new OperacionInvalidaException("El título no puede superar los " + MAX_LARGO_TITULO + " caracteres.");
        }
        if (contenido == null || contenido.isBlank()) {
            throw new OperacionInvalidaException("Escribe un mensaje para tu hilo.");
        }
        validarLargo(contenido);
        if (!proyectosDelPerfil(perfilId).contains(proyectoId)) {
            throw new OperacionInvalidaException("Solo puedes publicar en proyectos donde tienes una asignación.");
        }
        exigirForoAbierto(proyectoId);
        List<MultipartFile> archivosValidos = validarArchivos(archivos);
        List<String> enlacesValidos = validarEnlaces(enlaces);

        ForoPublicacion f = new ForoPublicacion();
        f.setProyectoId(proyectoId);
        f.setAutorId(perfilId);
        f.setTitulo(titulo.trim());
        f.setContenido(contenido.trim());
        f.setTopicoId(resolverTopico(topicoCodigo, f.getTitulo(), f.getContenido()));
        f.setEtiquetas(ForoClasificadorService.escribirEtiquetasJson(resolverEtiquetas(etiquetasTexto, f.getTitulo(), f.getContenido())));
        ForoPublicacion guardado = foroPublicacionRepository.save(f);
        guardarAdjuntos(guardado.getId(), archivosValidos, enlacesValidos);

        // Aviso al resto del equipo del proyecto (no al autor): antes solo se
        // notificaba una RESPUESTA a un hilo, nunca la creacion del hilo en si,
        // asi que nadie se enteraba de un hilo nuevo hasta que alguien mas
        // entraba al foro por su cuenta. Tipo "info" porque no tiene
        // preferencia configurable todavia (ver CODIGO_A_TIPO_EVENTO en
        // NotificacionService) - siempre se manda, igual que foro_respuesta.
        String enlaceHilo = "foro-hilo.html?id=" + guardado.getId();
        for (Asignacion a : asignacionRepository.listarEquipoDeProyecto(proyectoId, "activa")) {
            if (!a.getPerfilId().equals(perfilId)) {
                notificacionService.crear(a.getPerfilId(), "info", "Nuevo hilo: " + guardado.getTitulo(),
                        "Se abrió un hilo nuevo en el foro del proyecto.", enlaceHilo);
            }
        }
        return guardado;
    }

    /** Compatibilidad: responder solo con texto, sin cita ni adjuntos. */
    @Transactional
    public void responder(Long hiloId, Long perfilId, String contenido) {
        responder(hiloId, perfilId, contenido, null, null, null);
    }

    /**
     * @param respuestaAId mensaje del MISMO hilo al que se responde puntualmente (o null).
     * @return id de la respuesta creada (para volver al mensaje con #m-ID).
     */
    @Transactional
    public Long responder(Long hiloId, Long perfilId, String contenido, Long respuestaAId,
                          List<MultipartFile> archivos, List<String> enlaces) {
        ForoPublicacion hilo = foroPublicacionRepository.buscarHiloConDetalle(hiloId)
                .orElseThrow(() -> new OperacionInvalidaException("El hilo ya no existe."));
        if (hilo.getPublicacionPadreId() != null) {
            throw new OperacionInvalidaException("Solo se puede responder dentro de un hilo.");
        }
        if (!proyectosDelPerfil(perfilId).contains(hilo.getProyectoId())) {
            throw new OperacionInvalidaException("Solo puedes responder en foros de proyectos donde tienes una asignación.");
        }
        exigirForoAbierto(hilo.getProyectoId());

        List<MultipartFile> archivosValidos = validarArchivos(archivos);
        List<String> enlacesValidos = validarEnlaces(enlaces);
        boolean hayTexto = contenido != null && !contenido.isBlank();
        if (!hayTexto && archivosValidos.isEmpty() && enlacesValidos.isEmpty()) {
            throw new OperacionInvalidaException("Escribe una respuesta o adjunta un archivo antes de publicar.");
        }
        if (hayTexto) validarLargo(contenido);

        ForoPublicacion citado = null;
        if (respuestaAId != null) {
            citado = foroPublicacionRepository.findById(respuestaAId)
                    .filter(m -> m.getId().equals(hiloId) || hiloId.equals(m.getPublicacionPadreId()))
                    .orElseThrow(() -> new OperacionInvalidaException("El mensaje al que respondes ya no está en este hilo."));
        }

        ForoPublicacion respuesta = new ForoPublicacion();
        respuesta.setProyectoId(hilo.getProyectoId());
        respuesta.setAutorId(perfilId);
        respuesta.setPublicacionPadreId(hiloId);
        respuesta.setRespuestaAId(citado != null ? citado.getId() : null);
        respuesta.setContenido(hayTexto ? contenido.trim() : "");
        ForoPublicacion guardada = foroPublicacionRepository.save(respuesta);
        guardarAdjuntos(guardada.getId(), archivosValidos, enlacesValidos);

        String enlace = "foro-hilo.html?id=" + hiloId + "#m-" + guardada.getId();
        if (!hilo.getAutorId().equals(perfilId)) {
            notificacionService.crear(hilo.getAutorId(), "foro_respuesta", "Respuesta en tu hilo",
                    "Alguien respondió en \"" + hilo.getTitulo() + "\".", enlace);
        }
        if (citado != null && !citado.getAutorId().equals(perfilId) && !citado.getAutorId().equals(hilo.getAutorId())) {
            notificacionService.crear(citado.getAutorId(), "foro_cita", "Respondieron tu mensaje",
                    "Alguien respondió a tu mensaje en \"" + hilo.getTitulo() + "\".", enlace);
        }
        return guardada.getId();
    }

    private Long resolverTopico(String topicoCodigo, String titulo, String contenido) {
        if (topicoCodigo != null && !topicoCodigo.isBlank() && !"auto".equals(topicoCodigo)) {
            return foroTopicoRepository.findByCodigo(topicoCodigo.trim())
                    .map(ForoTopico::getId)
                    .orElseThrow(() -> new OperacionInvalidaException("El tópico elegido no existe."));
        }
        ForoTopico sugerido = clasificador.clasificar(titulo, contenido);
        return sugerido != null ? sugerido.getId() : null;
    }

    private List<String> resolverEtiquetas(String etiquetasTexto, String titulo, String contenido) {
        if (etiquetasTexto != null && !etiquetasTexto.isBlank()) {
            return ForoClasificadorService.parsearEtiquetasTexto(etiquetasTexto);
        }
        return clasificador.sugerirEtiquetas(titulo, contenido);
    }

    /** Clasifica los hilos que no tienen tópico (anteriores a la v6). Lo llama InicializadorTopicosForo. */
    @Transactional
    public int clasificarHilosSinTopico() {
        int n = 0;
        for (ForoPublicacion hilo : foroPublicacionRepository.listarHilosSinTopico()) {
            ForoTopico topico = clasificador.clasificar(hilo.getTitulo(), hilo.getContenido());
            if (topico == null) continue;
            hilo.setTopicoId(topico.getId());
            if (hilo.getEtiquetas() == null || hilo.getEtiquetas().isBlank()) {
                hilo.setEtiquetas(ForoClasificadorService.escribirEtiquetasJson(
                        clasificador.sugerirEtiquetas(hilo.getTitulo(), hilo.getContenido())));
            }
            foroPublicacionRepository.save(hilo);
            n++;
        }
        return n;
    }

    // ═════════════════════════════ Detalle del hilo ═════════════════════════════

    @Transactional
    public HiloDetalle obtenerDetalle(Long hiloId, Long viewerPerfilId, boolean viewerEsAdmin) {
        ForoPublicacion hilo = foroPublicacionRepository.buscarHiloConDetalle(hiloId)
                .filter(h -> h.getPublicacionPadreId() == null)
                .orElseThrow(() -> new OperacionInvalidaException("El hilo ya no existe."));
        exigirMiembro(viewerPerfilId, hilo.getProyectoId(), viewerEsAdmin);
        hilo.setNumVistas(hilo.getNumVistas() + 1);
        foroPublicacionRepository.save(hilo);

        boolean moderador = esModerador(viewerPerfilId, hilo.getProyectoId(), viewerEsAdmin);
        boolean abierto = foroAbierto(hilo.getProyectoId());
        boolean esAutorHilo = hilo.getAutorId().equals(viewerPerfilId);
        int maxEdiciones = maxEdiciones();

        List<ForoPublicacion> respuestasCrudo = foroPublicacionRepository.listarRespuestas(hiloId);
        List<ForoPublicacion> todos = new ArrayList<>();
        todos.add(hilo);
        todos.addAll(respuestasCrudo);
        Map<Long, ForoPublicacion> porId = todos.stream().collect(Collectors.toMap(ForoPublicacion::getId, m -> m));
        List<Long> ids = todos.stream().map(ForoPublicacion::getId).toList();

        Map<Long, Long> destacados = contarDestacados(ids);
        Set<Long> misDestacados = new HashSet<>(foroDestacadoRepository.destacadasPorPerfil(viewerPerfilId, ids));
        Map<Long, List<ForoAdjuntoInfo>> adjuntos = foroAdjuntoRepository.listarInfo(ids).stream()
                .collect(Collectors.groupingBy(ForoAdjuntoInfo::getPublicacionId));

        Contexto ctx = new Contexto(hilo, viewerPerfilId, moderador, abierto, esAutorHilo, maxEdiciones,
                porId, destacados, misDestacados, adjuntos);

        RespuestaFila mensajeInicial = aMensaje(hilo, ctx);
        List<RespuestaFila> respuestas = respuestasCrudo.stream().map(r -> aMensaje(r, ctx)).collect(Collectors.toList());
        RespuestaFila solucion = respuestas.stream().filter(RespuestaFila::isEsSolucion).findFirst().orElse(null);

        // Participantes: autor del hilo primero, luego por orden de aparición.
        Map<Long, int[]> mensajesPorAutor = new LinkedHashMap<>();
        Map<Long, ForoPublicacion> primerMensaje = new LinkedHashMap<>();
        for (ForoPublicacion m : todos) {
            mensajesPorAutor.computeIfAbsent(m.getAutorId(), k -> new int[1])[0]++;
            primerMensaje.putIfAbsent(m.getAutorId(), m);
        }
        List<ParticipanteFila> participantes = primerMensaje.entrySet().stream()
                .map(e -> new ParticipanteFila(nombreDe(e.getValue()), InicialesUtil.de(nombreDe(e.getValue())),
                        e.getValue().getAutor().getCargo() != null ? e.getValue().getAutor().getCargo() : "",
                        mensajesPorAutor.get(e.getKey())[0], e.getKey().equals(hilo.getAutorId())))
                .collect(Collectors.toList());
        List<String> participantesIniciales = participantes.stream().map(ParticipanteFila::iniciales).toList();

        // Relacionados: primero del mismo tópico (en cualquiera de mis proyectos), luego del mismo proyecto.
        List<Long> misProyectos = viewerEsAdmin ? List.of(hilo.getProyectoId()) : proyectosDelPerfil(viewerPerfilId);
        Map<Long, HiloResumen> relacionados = new LinkedHashMap<>();
        if (hilo.getTopicoId() != null && !misProyectos.isEmpty()) {
            for (ForoPublicacion r : foroPublicacionRepository.listarMismoTopico(hilo.getTopicoId(), misProyectos, hiloId)) {
                if (relacionados.size() >= 4) break;
                relacionados.put(r.getId(), aResumen(r, true));
            }
        }
        for (ForoPublicacion r : foroPublicacionRepository.listarRelacionados(hilo.getProyectoId(), hiloId)) {
            if (relacionados.size() >= 5) break;
            relacionados.putIfAbsent(r.getId(), aResumen(r, false));
        }

        boolean permiteMarcarSolucion = abierto && (moderador || esAutorHilo);
        HiloDetalle detalle = new HiloDetalle(hilo.getId(), hilo.getTitulo(), hilo.getProyectoId(), hilo.getProyecto().getNombre(),
                nombreDe(hilo), formatear(hilo.getFechaPublicacion()),
                hilo.getContenido(), hilo.getNumVistas(), permiteMarcarSolucion,
                mensajeInicial.isPermiteEditar(), mensajeInicial.isPermiteEliminar(),
                respuestas, participantesIniciales, new ArrayList<>(relacionados.values()));

        detalle.setMensajeInicial(mensajeInicial);
        detalle.setSolucion(solucion);
        detalle.setTopico(hilo.getTopico() != null ? aTopicoFila(hilo.getTopico()) : null);
        detalle.setEtiquetas(ForoClasificadorService.leerEtiquetasJson(hilo.getEtiquetas()));
        detalle.setForoAbierto(abierto);
        detalle.setEstadoProyecto(hilo.getProyecto().getEstado());
        detalle.setParticipantes(participantes);
        detalle.setAdjuntos(adjuntos.values().stream().flatMap(List::stream)
                .sorted(Comparator.comparing(ForoAdjuntoInfo::getId)).collect(Collectors.toList()));
        detalle.setNumDestacadosTotal(destacados.values().stream().mapToLong(Long::longValue).sum());
        ForoPublicacion ultima = respuestasCrudo.isEmpty() ? hilo : respuestasCrudo.get(respuestasCrudo.size() - 1);
        detalle.setUltimaActividadLabel(formatear(ultima.getFechaPublicacion()));
        detalle.setMaxEdiciones(maxEdiciones);
        detalle.setEsFijado(Boolean.TRUE.equals(hilo.getEsFijado()));
        detalle.setPermiteFijar(moderador);
        detalle.setEsModerador(moderador);
        if (hilo.getResumenIa() != null && !hilo.getResumenIa().isBlank()) {
            detalle.setResumenIa(hilo.getResumenIa());
            detalle.setResumenIaFechaLabel(hilo.getResumenIaGeneradoEn() != null ? formatear(hilo.getResumenIaGeneradoEn()) : "");
        }
        return detalle;
    }

    /**
     * Genera (o regenera) el resumen con IA de un hilo y lo guarda en
     * foro_publicaciones.resumen_ia, para no volver a llamar a Claude la
     * proxima vez que alguien lo abra. Cualquiera que pueda ver el hilo
     * (ya validado por exigirMiembro) puede pedir el resumen.
     */
    @Transactional
    public String generarResumenIa(Long hiloId, Long viewerPerfilId, boolean viewerEsAdmin) {
        ForoPublicacion hilo = foroPublicacionRepository.buscarHiloConDetalle(hiloId)
                .filter(h -> h.getPublicacionPadreId() == null)
                .orElseThrow(() -> new OperacionInvalidaException("El hilo ya no existe."));
        exigirMiembro(viewerPerfilId, hilo.getProyectoId(), viewerEsAdmin);

        List<ForoPublicacion> respuestas = foroPublicacionRepository.listarRespuestas(hiloId);
        StringBuilder texto = new StringBuilder();
        texto.append(nombreDe(hilo)).append(" (mensaje inicial): ").append(hilo.getContenido()).append("\n\n");
        for (ForoPublicacion r : respuestas) {
            texto.append(nombreDe(r)).append(": ").append(r.getContenido()).append("\n\n");
        }

        String resumen = asistenteIaService.resumirHilo(hilo.getTitulo(), texto.toString());
        hilo.setResumenIa(resumen);
        hilo.setResumenIaGeneradoEn(LocalDateTime.now());
        foroPublicacionRepository.save(hilo);
        return resumen;
    }

    /** Datos compartidos al convertir cada mensaje del hilo. */
    private record Contexto(ForoPublicacion hilo, Long viewerPerfilId, boolean moderador, boolean abierto,
                            boolean viewerEsAutorHilo, int maxEdiciones, Map<Long, ForoPublicacion> porId,
                            Map<Long, Long> destacados, Set<Long> misDestacados,
                            Map<Long, List<ForoAdjuntoInfo>> adjuntos) {
    }

    private RespuestaFila aMensaje(ForoPublicacion m, Contexto ctx) {
        boolean esInicial = m.getPublicacionPadreId() == null;
        boolean esMio = m.getAutorId().equals(ctx.viewerPerfilId());
        RespuestaFila fila = new RespuestaFila(m.getId(), m.getAutorId(), nombreDe(m), m.getAutor().getCargo(),
                formatear(m.getFechaPublicacion()), iso(m.getFechaPublicacion()), m.getContenido(),
                Boolean.TRUE.equals(m.getEsSolucion()), esInicial);

        int ediciones = m.getNumEdiciones() != null ? m.getNumEdiciones() : 0;
        int restantes = ctx.maxEdiciones() > 0 ? Math.max(0, ctx.maxEdiciones() - ediciones) : -1;
        fila.setEsMio(esMio);
        fila.setEsAutorDelHilo(m.getAutorId().equals(ctx.hilo().getAutorId()));
        fila.setEditado(Boolean.TRUE.equals(m.getEditado()));
        fila.setNumEdiciones(ediciones);
        fila.setFechaEdicionLabel(formatear(m.getFechaEdicion()));
        fila.setEdicionesRestantes(restantes);
        fila.setPermiteEditar(esMio && ctx.abierto() && restantes != 0);
        // El autor borra lo suyo mientras el foro esté abierto; el PM/Admin modera siempre.
        fila.setPermiteEliminar((esMio && ctx.abierto()) || ctx.moderador());
        fila.setPermiteMarcarSolucion(!esInicial && ctx.abierto() && !fila.isEsSolucion()
                && (ctx.moderador() || ctx.viewerEsAutorHilo()));

        if (m.getRespuestaAId() != null) {
            ForoPublicacion citado = ctx.porId().get(m.getRespuestaAId());
            fila.setCita(citado != null
                    ? new CitaForo(citado.getId(), nombreDe(citado), abreviar(citado.getContenido(), 160), false)
                    : CitaForo.eliminada());
        }
        fila.setNumDestacados(ctx.destacados().getOrDefault(m.getId(), 0L));
        fila.setDestacadoPorMi(ctx.misDestacados().contains(m.getId()));
        fila.setPuedeDestacar(!esMio);
        fila.setAdjuntos(ctx.adjuntos().getOrDefault(m.getId(), List.of()));
        return fila;
    }

    private HiloResumen aResumen(ForoPublicacion r, boolean mismoTopico) {
        String proyecto = r.getProyecto() != null ? r.getProyecto().getNombre() : "";
        return new HiloResumen(r.getId(), r.getTitulo(), foroPublicacionRepository.countByPublicacionPadreId(r.getId()),
                proyecto, mismoTopico);
    }

    // ═════════════════════════════ Solución, fijar, destacar ═════════════════════════════

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
        if (!esModerador(actorPerfilId, hilo.getProyectoId(), actorEsAdmin) && !hilo.getAutorId().equals(actorPerfilId)) {
            throw new OperacionInvalidaException("Solo quien abrió el hilo (o el PM del proyecto) puede marcar la solución.");
        }
        exigirForoAbierto(hilo.getProyectoId());
        foroPublicacionRepository.findByPublicacionPadreId(hiloId).forEach(r -> {
            if (Boolean.TRUE.equals(r.getEsSolucion())) {
                r.setEsSolucion(false);
                foroPublicacionRepository.save(r);
            }
        });
        respuesta.setEsSolucion(true);
        foroPublicacionRepository.save(respuesta);
        if (!respuesta.getAutorId().equals(actorPerfilId)) {
            notificacionService.crear(respuesta.getAutorId(), "foro_respuesta", "Tu respuesta resolvió un hilo",
                    "Marcaron tu respuesta como solución en \"" + hilo.getTitulo() + "\".",
                    "foro-hilo.html?id=" + hiloId + "#m-" + respuestaId);
        }
    }

    /**
     * Fija o desfija un hilo raíz (aparece primero en la lista). Antes no se
     * verificaba quién lo pedía: ahora solo el PM activo del proyecto o un
     * Administrador.
     */
    @Transactional
    public void establecerFijado(Long hiloId, boolean valor, Long actorPerfilId, boolean actorEsAdmin) {
        ForoPublicacion hilo = foroPublicacionRepository.findById(hiloId)
                .orElseThrow(() -> new OperacionInvalidaException("El hilo ya no existe."));
        if (hilo.getPublicacionPadreId() != null) {
            throw new OperacionInvalidaException("Solo se puede fijar un hilo, no una respuesta.");
        }
        if (!esModerador(actorPerfilId, hilo.getProyectoId(), actorEsAdmin)) {
            throw new OperacionInvalidaException("Solo el PM del proyecto puede fijar hilos.");
        }
        hilo.setEsFijado(valor);
        foroPublicacionRepository.save(hilo);
    }

    /**
     * Marca o quita "me fue útil" sobre un mensaje (hilo o respuesta).
     * @return true si quedó destacado, false si se quitó.
     */
    @Transactional
    public boolean alternarDestacado(Long publicacionId, Long perfilId, boolean esAdmin) {
        ForoPublicacion m = foroPublicacionRepository.findById(publicacionId)
                .orElseThrow(() -> new OperacionInvalidaException("El mensaje ya no existe."));
        exigirMiembro(perfilId, m.getProyectoId(), esAdmin);
        if (m.getAutorId().equals(perfilId)) {
            throw new OperacionInvalidaException("No puedes destacar tu propio mensaje.");
        }
        ForoDestacadoId id = new ForoDestacadoId(publicacionId, perfilId);
        if (foroDestacadoRepository.existsById(id)) {
            foroDestacadoRepository.deleteById(id);
            return false;
        }
        foroDestacadoRepository.save(new ForoDestacado(publicacionId, perfilId));
        return true;
    }

    /** Cuántas personas destacaron un mensaje. */
    public long numDestacados(Long publicacionId) {
        return contarDestacados(List.of(publicacionId)).getOrDefault(publicacionId, 0L);
    }

    /** Hilo al que pertenece un mensaje (el propio id si es el hilo raíz). */
    public Long hiloDe(Long publicacionId) {
        return foroPublicacionRepository.findById(publicacionId)
                .map(m -> m.getPublicacionPadreId() != null ? m.getPublicacionPadreId() : m.getId())
                .orElseThrow(() -> new OperacionInvalidaException("El mensaje ya no existe."));
    }

    // ═════════════════════════════ Editar (con límite y huella) ═════════════════════════════

    /**
     * Editar solo la cambia quien la escribio (autor): ni un administrador ni
     * el Project Manager pueden reescribir el mensaje de otra persona, solo
     * moderar (eliminar) - ver eliminarHilo/eliminarRespuesta.
     */
    @Transactional
    public void editarHilo(Long hiloId, Long actorPerfilId, String nuevoTitulo, String nuevoContenido) {
        editarHilo(hiloId, actorPerfilId, nuevoTitulo, nuevoContenido, null, null);
    }

    /**
     * Cambiar solo el tópico o las etiquetas NO cuenta como edición (no
     * consume el límite ni marca "editado"): solo cambia la clasificación.
     */
    @Transactional
    public void editarHilo(Long hiloId, Long actorPerfilId, String nuevoTitulo, String nuevoContenido,
                           String topicoCodigo, String etiquetasTexto) {
        ForoPublicacion hilo = foroPublicacionRepository.findById(hiloId)
                .orElseThrow(() -> new OperacionInvalidaException("El hilo ya no existe."));
        if (hilo.getPublicacionPadreId() != null) {
            throw new OperacionInvalidaException("Esto no es un hilo.");
        }
        if (!hilo.getAutorId().equals(actorPerfilId)) {
            throw new OperacionInvalidaException("Solo quien abrió el hilo puede editarlo.");
        }
        exigirForoAbierto(hilo.getProyectoId());
        if (nuevoTitulo == null || nuevoTitulo.isBlank()) {
            throw new OperacionInvalidaException("Escribe un título para el hilo.");
        }
        if (nuevoTitulo.trim().length() > MAX_LARGO_TITULO) {
            throw new OperacionInvalidaException("El título no puede superar los " + MAX_LARGO_TITULO + " caracteres.");
        }
        if (nuevoContenido == null || nuevoContenido.isBlank()) {
            throw new OperacionInvalidaException("Escribe un mensaje para el hilo.");
        }
        validarLargo(nuevoContenido);

        String tituloAnterior = hilo.getTitulo();
        String contenidoAnterior = hilo.getContenido();
        boolean cambioTexto = !nuevoTitulo.trim().equals(tituloAnterior) || !nuevoContenido.trim().equals(contenidoAnterior);

        if (topicoCodigo != null && !topicoCodigo.isBlank()) {
            hilo.setTopicoId(resolverTopico(topicoCodigo, nuevoTitulo, nuevoContenido));
        }
        if (etiquetasTexto != null) {
            hilo.setEtiquetas(ForoClasificadorService.escribirEtiquetasJson(
                    ForoClasificadorService.parsearEtiquetasTexto(etiquetasTexto)));
        }
        if (cambioTexto) {
            registrarEdicion(hilo, actorPerfilId);
            hilo.setTitulo(nuevoTitulo.trim());
            hilo.setContenido(nuevoContenido.trim());
            auditoriaService.registrar(usuarioIdDePerfil(actorPerfilId), "FORO_HILO_EDITADO", "foro_publicaciones", hiloId,
                    json("titulo", tituloAnterior, "contenido", contenidoAnterior),
                    json("titulo", hilo.getTitulo(), "contenido", hilo.getContenido()),
                    "Edición " + hilo.getNumEdiciones());
        }
        foroPublicacionRepository.save(hilo);
    }

    @Transactional
    public void editarRespuesta(Long respuestaId, Long actorPerfilId, String nuevoContenido) {
        ForoPublicacion respuesta = foroPublicacionRepository.findById(respuestaId)
                .orElseThrow(() -> new OperacionInvalidaException("La respuesta ya no existe."));
        if (respuesta.getPublicacionPadreId() == null) {
            throw new OperacionInvalidaException("Esto no es una respuesta.");
        }
        if (!respuesta.getAutorId().equals(actorPerfilId)) {
            throw new OperacionInvalidaException("Solo quien escribió la respuesta puede editarla.");
        }
        exigirForoAbierto(respuesta.getProyectoId());
        if (nuevoContenido == null || nuevoContenido.isBlank()) {
            throw new OperacionInvalidaException("Escribe una respuesta antes de guardar.");
        }
        validarLargo(nuevoContenido);
        if (nuevoContenido.trim().equals(respuesta.getContenido())) {
            return; // sin cambios: no consume una edición
        }
        String anterior = respuesta.getContenido();
        registrarEdicion(respuesta, actorPerfilId);
        respuesta.setContenido(nuevoContenido.trim());
        foroPublicacionRepository.save(respuesta);
        auditoriaService.registrar(usuarioIdDePerfil(actorPerfilId), "FORO_RESPUESTA_EDITADA", "foro_publicaciones", respuestaId,
                json("contenido", anterior), json("contenido", respuesta.getContenido()),
                "Edición " + respuesta.getNumEdiciones());
    }

    /** Verifica el límite y deja la huella de edición. */
    private void registrarEdicion(ForoPublicacion m, Long actorPerfilId) {
        int max = maxEdiciones();
        int usadas = m.getNumEdiciones() != null ? m.getNumEdiciones() : 0;
        if (max > 0 && usadas >= max) {
            throw new OperacionInvalidaException("Ya editaste este mensaje " + usadas + " "
                    + (usadas == 1 ? "vez" : "veces") + ", que es el máximo permitido.");
        }
        m.setEditado(true);
        m.setNumEdiciones(usadas + 1);
        m.setFechaEdicion(LocalDateTime.now());
    }

    // ═════════════════════════════ Eliminar ═════════════════════════════

    /** Eliminar el hilo se lleva sus respuestas (misma tabla autorreferencial). */
    @Transactional
    public void eliminarHilo(Long hiloId, Long actorPerfilId, boolean actorEsAdmin, String motivo) {
        ForoPublicacion hilo = foroPublicacionRepository.findById(hiloId)
                .orElseThrow(() -> new OperacionInvalidaException("El hilo ya no existe."));
        if (hilo.getPublicacionPadreId() != null) {
            throw new OperacionInvalidaException("Esto no es un hilo.");
        }
        boolean moderador = esModerador(actorPerfilId, hilo.getProyectoId(), actorEsAdmin);
        if (!moderador && !hilo.getAutorId().equals(actorPerfilId)) {
            throw new OperacionInvalidaException("Solo quien abrió el hilo (o el PM del proyecto) puede eliminarlo.");
        }
        if (!moderador) exigirForoAbierto(hilo.getProyectoId());
        boolean propio = hilo.getAutorId().equals(actorPerfilId);
        if (!propio && AuditoriaService.limpiarMotivo(motivo) == null) {
            throw new OperacionInvalidaException("Indica el motivo de la moderación: queda registrado en Auditoría.");
        }
        auditoriaService.registrar(usuarioIdDePerfil(actorPerfilId), "FORO_HILO_ELIMINADO", "foro_publicaciones", hiloId,
                json("titulo", hilo.getTitulo(), "contenido", hilo.getContenido()), null,
                AuditoriaService.conMotivo(propio ? "Eliminado por su autor" : "Eliminado por moderación", motivo));
        foroPublicacionRepository.deleteByPublicacionPadreId(hiloId);
        foroPublicacionRepository.delete(hilo);
    }

    @Transactional
    public void eliminarRespuesta(Long respuestaId, Long actorPerfilId, boolean actorEsAdmin, String motivo) {
        ForoPublicacion respuesta = foroPublicacionRepository.findById(respuestaId)
                .orElseThrow(() -> new OperacionInvalidaException("La respuesta ya no existe."));
        if (respuesta.getPublicacionPadreId() == null) {
            throw new OperacionInvalidaException("Esto no es una respuesta.");
        }
        boolean moderador = esModerador(actorPerfilId, respuesta.getProyectoId(), actorEsAdmin);
        if (!moderador && !respuesta.getAutorId().equals(actorPerfilId)) {
            throw new OperacionInvalidaException("Solo quien escribió la respuesta (o el PM del proyecto) puede eliminarla.");
        }
        if (!moderador) exigirForoAbierto(respuesta.getProyectoId());
        boolean propia = respuesta.getAutorId().equals(actorPerfilId);
        if (!propia && AuditoriaService.limpiarMotivo(motivo) == null) {
            throw new OperacionInvalidaException("Indica el motivo de la moderación: queda registrado en Auditoría.");
        }
        auditoriaService.registrar(usuarioIdDePerfil(actorPerfilId), "FORO_RESPUESTA_ELIMINADA", "foro_publicaciones", respuestaId,
                json("contenido", respuesta.getContenido()), null,
                AuditoriaService.conMotivo(propia ? "Eliminada por su autor" : "Eliminada por moderación", motivo));
        foroPublicacionRepository.delete(respuesta);
    }

    // ═════════════════════════════ Adjuntos ═════════════════════════════

    /** Adjunto completo (con binario) si el perfil puede ver el foro de ese proyecto. */
    @Transactional(readOnly = true)
    public ForoAdjunto obtenerAdjunto(Long adjuntoId, Long perfilId, boolean esAdmin) {
        ForoAdjunto adjunto = foroAdjuntoRepository.findById(adjuntoId)
                .orElseThrow(() -> new OperacionInvalidaException("El archivo ya no existe."));
        ForoPublicacion m = foroPublicacionRepository.findById(adjunto.getPublicacionId())
                .orElseThrow(() -> new OperacionInvalidaException("El archivo ya no existe."));
        exigirMiembro(perfilId, m.getProyectoId(), esAdmin);
        return adjunto;
    }

    private List<MultipartFile> validarArchivos(List<MultipartFile> archivos) {
        List<MultipartFile> validos = new ArrayList<>();
        if (archivos == null) return validos;
        for (MultipartFile a : archivos) {
            if (a == null || a.isEmpty()) continue;
            validos.add(a);
        }
        if (validos.size() > MAX_ARCHIVOS_POR_MENSAJE) {
            throw new OperacionInvalidaException("Puedes adjuntar hasta " + MAX_ARCHIVOS_POR_MENSAJE + " archivos por mensaje.");
        }
        for (MultipartFile a : validos) {
            if (a.getSize() > MAX_BYTES_POR_ARCHIVO) {
                throw new OperacionInvalidaException("\"" + nombreArchivo(a) + "\" pesa más de 5 MB.");
            }
            if (mimeDe(nombreArchivo(a)) == null) {
                throw new OperacionInvalidaException("\"" + nombreArchivo(a) + "\" no es un formato permitido. "
                        + "Usa imágenes, PDF, Word, Excel, PowerPoint, TXT, CSV o ZIP.");
            }
        }
        return validos;
    }

    private List<String> validarEnlaces(List<String> enlaces) {
        List<String> validos = new ArrayList<>();
        if (enlaces == null) return validos;
        for (String e : enlaces) {
            if (e == null || e.isBlank()) continue;
            String url = e.trim();
            if (!url.matches("(?i)^https?://[^\\s<>\"]+$")) {
                throw new OperacionInvalidaException("El enlace \"" + abreviar(url, 60) + "\" no es válido (debe empezar con http:// o https://).");
            }
            if (url.length() > 500) {
                throw new OperacionInvalidaException("Los enlaces no pueden superar los 500 caracteres.");
            }
            if (!validos.contains(url)) validos.add(url);
        }
        if (validos.size() > MAX_ENLACES_POR_MENSAJE) {
            throw new OperacionInvalidaException("Puedes adjuntar hasta " + MAX_ENLACES_POR_MENSAJE + " enlaces por mensaje.");
        }
        return validos;
    }

    private void guardarAdjuntos(Long publicacionId, List<MultipartFile> archivos, List<String> enlaces) {
        for (MultipartFile a : archivos) {
            ForoAdjunto adj = new ForoAdjunto();
            adj.setPublicacionId(publicacionId);
            adj.setTipo(ForoAdjunto.TIPO_ARCHIVO);
            String nombre = nombreArchivo(a);
            adj.setNombre(nombre);
            adj.setMimeTipo(mimeDe(nombre));
            adj.setTamanoBytes((int) a.getSize());
            try {
                adj.setContenido(a.getBytes());
            } catch (IOException ex) {
                throw new OperacionInvalidaException("No se pudo leer \"" + nombre + "\". Intenta adjuntarlo otra vez.");
            }
            foroAdjuntoRepository.save(adj);
        }
        for (String url : enlaces) {
            ForoAdjunto adj = new ForoAdjunto();
            adj.setPublicacionId(publicacionId);
            adj.setTipo(ForoAdjunto.TIPO_ENLACE);
            adj.setNombre(abreviar(url.replaceFirst("(?i)^https?://(www\\.)?", ""), 120));
            adj.setUrl(url);
            foroAdjuntoRepository.save(adj);
        }
    }

    private static String nombreArchivo(MultipartFile a) {
        String original = a.getOriginalFilename();
        if (original == null || original.isBlank()) return "archivo";
        String limpio = original.replace("\\", "/");
        limpio = limpio.substring(limpio.lastIndexOf('/') + 1).replaceAll("[\\r\\n\"]", "").trim();
        if (limpio.length() > 200) {
            int punto = limpio.lastIndexOf('.');
            String ext = punto > 0 ? limpio.substring(punto) : "";
            limpio = limpio.substring(0, 200 - ext.length()) + ext;
        }
        return limpio.isEmpty() ? "archivo" : limpio;
    }

    /** MIME permitido según la extensión, o null si la extensión no está permitida. */
    public static String mimeDe(String nombre) {
        int punto = nombre != null ? nombre.lastIndexOf('.') : -1;
        if (punto < 0) return null;
        return MIME_POR_EXTENSION.get(nombre.substring(punto + 1).toLowerCase(Locale.ROOT));
    }

    // ═════════════════════════════ Utilidades ═════════════════════════════

    private Map<Long, Long> contarDestacados(Collection<Long> ids) {
        Map<Long, Long> mapa = new HashMap<>();
        if (ids.isEmpty()) return mapa;
        for (Object[] fila : foroDestacadoRepository.contarPorPublicacion(ids)) {
            mapa.put(((Number) fila[0]).longValue(), ((Number) fila[1]).longValue());
        }
        return mapa;
    }

    private static void validarLargo(String contenido) {
        if (contenido.trim().length() > MAX_LARGO_CONTENIDO) {
            throw new OperacionInvalidaException("El mensaje no puede superar los " + MAX_LARGO_CONTENIDO + " caracteres.");
        }
    }

    private static String nombreDe(ForoPublicacion m) {
        return m.getAutor() != null && m.getAutor().getUsuario() != null
                ? m.getAutor().getUsuario().getNombreCompleto() : "Usuario";
    }

    private static String formatear(LocalDateTime fecha) {
        return fecha != null ? fecha.format(FORMATO) : "";
    }

    private static String iso(LocalDateTime fecha) {
        return fecha != null ? fecha.format(FORMATO_ISO) : "";
    }

    static String abreviar(String texto, int max) {
        if (texto == null) return "";
        String plano = texto.replaceAll("\\s+", " ").trim();
        return plano.length() <= max ? plano : plano.substring(0, max - 1).trim() + "…";
    }

    /** JSON de pares clave/valor (para valor_anterior / valor_nuevo de auditoria_logs). */
    static String json(String... paresClaveValor) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i + 1 < paresClaveValor.length; i += 2) {
            if (i > 0) sb.append(',');
            sb.append('"').append(escaparJson(paresClaveValor[i])).append("\":");
            String v = paresClaveValor[i + 1];
            sb.append(v == null ? "null" : "\"" + escaparJson(v) + "\"");
        }
        return sb.append('}').toString();
    }

    private static String escaparJson(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        return sb.toString();
    }
}
