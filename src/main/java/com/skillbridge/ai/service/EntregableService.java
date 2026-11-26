package com.skillbridge.ai.service;

import com.skillbridge.ai.dto.EntregaFila;
import com.skillbridge.ai.dto.EntregableFila;
import com.skillbridge.ai.model.Asignacion;
import com.skillbridge.ai.model.Entrega;
import com.skillbridge.ai.model.Entregable;
import com.skillbridge.ai.model.Proyecto;
import com.skillbridge.ai.repository.AsignacionRepository;
import com.skillbridge.ai.repository.EntregaRepository;
import com.skillbridge.ai.repository.EntregableRepository;
import com.skillbridge.ai.repository.ProyectoRepository;
import com.skillbridge.ai.util.OperacionInvalidaException;
import com.skillbridge.ai.util.RecursoNoEncontradoException;
import com.skillbridge.ai.util.Roles;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * "Avance real" de un proyecto (RF nuevo, sección Entregables): a
 * diferencia del % heurístico de ProyectoService (por tiempo transcurrido),
 * este módulo mide trabajo real: el PM (o Administrador) crea entregables
 * sobre un proyecto, cada colaborador del equipo activo sube su entrega
 * (texto/link/archivo), y el PM la revisa asignándole un estado de revisión (Completado,
 * Requiere ajustes o Rehacer) junto con un comentario obligatorio. No es una
 * nota: es el seguimiento del avance real del trabajo.
 *
 * Reglas de negocio:
 *  - Solo puede crear/editar/cancelar entregables el PM activo del
 *    proyecto o un Administrador (verificarGestion()).
 *  - Solo puede entregar/editar/borrar SU propia entrega un colaborador
 *    con asignación activa en ese proyecto (verificarPertenece()).
 *  - Se puede entregar (o reemplazar la entrega) incluso después de
 *    fecha_cierre — igual que Moodle: no se bloquea, se marca "atrasado"
 *    (calculado, no guardado) para que el PM lo vea al revisar.
 *  - Un entregable "cancelado" ya no acepta entregas nuevas, pero las que
 *    ya existían se conservan (no se borran) para no perder trabajo ni comentarios.
 *  - Una entrega marcada "completado" queda cerrada: ya no se puede
 *    reemplazar ni borrar. Con "requiere_ajustes" o "rehacer" el
 *    colaborador vuelve a entregar y pasa otra vez a "enviado".
 */
@Service
public class EntregableService {

    private static final long MAX_ARCHIVO_BYTES = 15L * 1024 * 1024; // 15 MB
    private static final Set<String> TIPOS_PERMITIDOS = Set.of(
            "application/pdf", "text/plain", "image/png", "image/jpeg", "image/webp",
            "application/zip", "application/x-zip-compressed",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/json");

    /**
     * Extensión → tipo MIME que se guarda. El tipo que se sirve al descargar lo
     * decide el SERVIDOR según la extensión (no el Content-Type que manda el
     * navegador, que el usuario puede falsificar: p.ej. un HTML con JavaScript
     * declarado como "image/png").
     */
    private static final Map<String, String> MIME_POR_EXTENSION = Map.ofEntries(
            Map.entry("pdf", "application/pdf"),
            Map.entry("txt", "text/plain"),
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("webp", "image/webp"),
            Map.entry("zip", "application/zip"),
            Map.entry("doc", "application/msword"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("xls", "application/vnd.ms-excel"),
            Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            Map.entry("json", "application/json"));

    private final EntregableRepository entregableRepository;
    private final EntregaRepository entregaRepository;
    private final ProyectoRepository proyectoRepository;
    private final AsignacionRepository asignacionRepository;
    private final AuditoriaService auditoriaService;
    private final NotificacionService notificacionService;

    public EntregableService(EntregableRepository entregableRepository, EntregaRepository entregaRepository,
                              ProyectoRepository proyectoRepository, AsignacionRepository asignacionRepository,
                              AuditoriaService auditoriaService, NotificacionService notificacionService) {
        this.entregableRepository = entregableRepository;
        this.entregaRepository = entregaRepository;
        this.proyectoRepository = proyectoRepository;
        this.asignacionRepository = asignacionRepository;
        this.auditoriaService = auditoriaService;
        this.notificacionService = notificacionService;
    }

    // ───────────────────────── Gestión (PM / Administrador) ─────────────────────────

    /** Un Administrador gestiona cualquier proyecto; un PM solo el que le pertenece. */
    private void verificarGestion(Long proyectoId, Long actorPerfilId, String rolEfectivo) {
        if (Roles.ADMINISTRADOR.equals(rolEfectivo)) return;
        boolean esPm = asignacionRepository.existsByProyectoIdAndPerfilIdAndRolEnProyectoAndEstado(
                proyectoId, actorPerfilId, Roles.PROJECT_MANAGER, "activa");
        if (!esPm) throw new OperacionInvalidaException("No administras este proyecto.");
    }

    @Transactional(readOnly = true)
    public List<EntregableFila> listarParaGestion(Long proyectoId) {
        int totalEquipo = (int) asignacionRepository.listarEquipoDeProyecto(proyectoId, "activa").stream()
                .filter(a -> Roles.COLABORADOR.equals(a.getRolEnProyecto())).count();
        return entregableRepository.findByProyectoIdOrderByFechaCierreAsc(proyectoId).stream()
                .map(e -> aFila(e, totalEquipo, null))
                .collect(Collectors.toList());
    }

    @Transactional
    public Entregable crear(Long proyectoId, Long actorPerfilId, String rolEfectivo, Long actorUsuarioId,
                             String titulo, String descripcion, String fechaAperturaIso,
                             String fechaCierreIso) {
        verificarGestion(proyectoId, actorPerfilId, rolEfectivo);
        Proyecto proyecto = proyectoRepository.findById(proyectoId)
                .orElseThrow(() -> new OperacionInvalidaException("El proyecto no existe."));
        LocalDateTime fechaApertura = parsearFecha(fechaAperturaIso);
        LocalDateTime fechaCierre = parsearFecha(fechaCierreIso);
        validarCampos(titulo, fechaApertura, fechaCierre);

        Entregable e = new Entregable();
        e.setProyectoId(proyectoId);
        e.setCreadoPorId(actorPerfilId);
        e.setTitulo(titulo.trim());
        e.setDescripcion(descripcion != null && !descripcion.isBlank() ? descripcion.trim() : null);
        e.setFechaApertura(fechaApertura);
        e.setFechaCierre(fechaCierre);
        e = entregableRepository.save(e);

        List<Asignacion> equipo = asignacionRepository.listarEquipoDeProyecto(proyectoId, "activa");
        for (Asignacion a : equipo) {
            if (Roles.COLABORADOR.equals(a.getRolEnProyecto())) {
                notificacionService.crear(a.getPerfilId(), "entregable_nuevo", "Nuevo entregable: " + e.getTitulo(),
                        "En \"" + proyecto.getNombre() + "\" · cierra el " + fechaCierre.toLocalDate() + ".",
                        "/colaborador/entregables/" + e.getId() + ".html");
            }
        }
        auditoriaService.registrar(actorUsuarioId, "ENTREGABLE_CREADO", "entregable", e.getId(), null, null,
                e.getTitulo() + " · " + proyecto.getNombre());
        return e;
    }

    @Transactional
    public void editar(Long entregableId, Long actorPerfilId, String rolEfectivo, Long actorUsuarioId,
                        String titulo, String descripcion, String fechaAperturaIso, String fechaCierreIso) {
        Entregable e = entregableRepository.findById(entregableId)
                .orElseThrow(() -> new OperacionInvalidaException("El entregable ya no existe."));
        verificarGestion(e.getProyectoId(), actorPerfilId, rolEfectivo);
        LocalDateTime fechaApertura = parsearFecha(fechaAperturaIso);
        LocalDateTime fechaCierre = parsearFecha(fechaCierreIso);
        validarCampos(titulo, fechaApertura, fechaCierre);

        e.setTitulo(titulo.trim());
        e.setDescripcion(descripcion != null && !descripcion.isBlank() ? descripcion.trim() : null);
        e.setFechaApertura(fechaApertura);
        e.setFechaCierre(fechaCierre);
        entregableRepository.save(e);

        auditoriaService.registrar(actorUsuarioId, "ENTREGABLE_EDITADO", "entregable", e.getId(), null, null,
                e.getTitulo());
    }

    /** No se borra físicamente (se perdería la trazabilidad de lo ya entregado/revisado): se cancela. */
    @Transactional
    public void cancelar(Long entregableId, Long actorPerfilId, String rolEfectivo, String motivo, Long actorUsuarioId) {
        Entregable e = entregableRepository.findById(entregableId)
                .orElseThrow(() -> new OperacionInvalidaException("El entregable ya no existe."));
        verificarGestion(e.getProyectoId(), actorPerfilId, rolEfectivo);
        if (AuditoriaService.limpiarMotivo(motivo) == null) {
            throw new OperacionInvalidaException("Indica el motivo de la cancelación: queda registrado en Auditoría.");
        }
        e.setEstado("cancelado");
        entregableRepository.save(e);
        auditoriaService.registrar(actorUsuarioId, "ENTREGABLE_CANCELADO", "entregable", e.getId(), null, null,
                AuditoriaService.conMotivo(e.getTitulo(), motivo));
    }

    private LocalDateTime parsearFecha(String iso) {
        if (iso == null || iso.isBlank()) throw new OperacionInvalidaException("Selecciona fecha y hora.");
        try {
            return LocalDateTime.parse(iso);
        } catch (Exception ex) {
            throw new OperacionInvalidaException("Fecha u hora no válida.");
        }
    }

    private void validarCampos(String titulo, LocalDateTime apertura, LocalDateTime cierre) {
        if (titulo == null || titulo.isBlank()) throw new OperacionInvalidaException("Ingresa un título para el entregable.");
        if (apertura == null || cierre == null) throw new OperacionInvalidaException("Selecciona apertura y cierre.");
        if (!cierre.isAfter(apertura)) throw new OperacionInvalidaException("El cierre debe ser posterior a la apertura.");
    }

    // ───────────────────────── Revisión (PM / Administrador) ─────────────────────────

    /** Tabla de revisión: TODOS los colaboradores del equipo, hayan entregado o no. */
    @Transactional(readOnly = true)
    public List<EntregaFila> detalleParaRevision(Long entregableId, Long actorPerfilId, String rolEfectivo) {
        Entregable e = entregableRepository.findById(entregableId)
                .orElseThrow(() -> new OperacionInvalidaException("El entregable ya no existe."));
        verificarGestion(e.getProyectoId(), actorPerfilId, rolEfectivo);

        List<Entrega> entregas = entregaRepository.listarPorEntregable(entregableId);
        java.util.Map<Long, Entrega> porPerfil = entregas.stream()
                .collect(Collectors.toMap(Entrega::getPerfilId, x -> x));

        List<Asignacion> equipo = asignacionRepository.listarEquipoDeProyecto(e.getProyectoId(), "activa").stream()
                .filter(a -> Roles.COLABORADOR.equals(a.getRolEnProyecto()))
                .sorted(Comparator.comparing(a -> a.getPerfil().getUsuario().getNombreCompleto()))
                .toList();

        return equipo.stream().map(a -> {
            Entrega en = porPerfil.get(a.getPerfilId());
            String nombre = a.getPerfil().getUsuario().getNombreCompleto();
            if (en == null) {
                return new EntregaFila(null, entregableId, a.getPerfilId(), nombre, false,
                        null, false, null, null, false, null, "sin_entregar", null);
            }
            boolean atrasado = en.getFechaEntrega() != null && en.getFechaEntrega().isAfter(e.getFechaCierre());
            return new EntregaFila(en.getId(), entregableId, a.getPerfilId(), nombre, true,
                    en.getFechaEntrega(), atrasado, en.getTexto(), en.getUrlEntrega(),
                    en.tieneArchivo(), en.getArchivoNombre(), en.getEstado(), en.getComentarioPm());
        }).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Entregable obtener(Long entregableId) {
        return entregableRepository.findById(entregableId)
                .orElseThrow(() -> new RecursoNoEncontradoException("El entregable ya no existe."));
    }

    /** Estados con los que el PM/Administrador cierra la revisión de una entrega. */
    public static final List<String> ESTADOS_REVISION = List.of("completado", "requiere_ajustes", "rehacer");
    /** Largo mínimo del comentario: obliga a dar una retroalimentación real, aunque sea breve. */
    public static final int COMENTARIO_MIN = 10;
    public static final int COMENTARIO_MAX = 1000;

    public static String etiquetaEstado(String estado) {
        return switch (estado == null ? "" : estado) {
            case "completado" -> "Completado";
            case "requiere_ajustes" -> "Requiere ajustes";
            case "rehacer" -> "Rehacer";
            case "enviado" -> "Por revisar";
            default -> "Sin entregar";
        };
    }

    /**
     * Revisión de una entrega: en lugar de una nota, el PM elige un estado
     * (completado / requiere_ajustes / rehacer) y deja SIEMPRE un comentario,
     * también cuando está completa (un "buen trabajo, ten en cuenta..." sirve).
     */
    @Transactional
    public void revisar(Long entregaId, Long actorPerfilId, String rolEfectivo, Long actorUsuarioId,
                         String estado, String comentario) {
        Entrega en = entregaRepository.findById(entregaId)
                .orElseThrow(() -> new OperacionInvalidaException("La entrega ya no existe."));
        Entregable e = en.getEntregable() != null ? en.getEntregable() : obtener(en.getEntregableId());
        verificarGestion(e.getProyectoId(), actorPerfilId, rolEfectivo);

        String estadoNuevo = estado != null ? estado.trim().toLowerCase(Locale.ROOT) : "";
        if (!ESTADOS_REVISION.contains(estadoNuevo)) {
            throw new OperacionInvalidaException("Elige un estado: Completado, Requiere ajustes o Rehacer.");
        }
        String texto = comentario != null ? comentario.trim() : "";
        if (texto.length() < COMENTARIO_MIN) {
            throw new OperacionInvalidaException("Deja un comentario para el colaborador (mínimo " + COMENTARIO_MIN + " caracteres).");
        }
        if (texto.length() > COMENTARIO_MAX) {
            throw new OperacionInvalidaException("El comentario no puede superar los " + COMENTARIO_MAX + " caracteres.");
        }
        en.setComentarioPm(texto);
        en.setEstado(estadoNuevo);
        en.setRevisadoPorId(actorPerfilId);
        en.setFechaRevision(LocalDateTime.now());
        entregaRepository.save(en);

        String etiqueta = etiquetaEstado(estadoNuevo);
        String resumen = texto.length() > 120 ? texto.substring(0, 117) + "..." : texto;
        String titulo = switch (estadoNuevo) {
            case "completado" -> "Entrega completada: ";
            case "requiere_ajustes" -> "Tu entrega requiere ajustes: ";
            default -> "Debes rehacer tu entrega: ";
        };
        notificacionService.crear(en.getPerfilId(), "entregable_revisado", titulo + e.getTitulo(),
                etiqueta + " · " + resumen,
                "/colaborador/entregables/" + e.getId() + ".html");
        auditoriaService.registrar(actorUsuarioId, "ENTREGA_REVISADA", "entrega", en.getId(), null, null,
                e.getTitulo() + " · " + etiqueta);
    }

    // ───────────────────────── Colaborador ─────────────────────────

    private void verificarPertenece(Long proyectoId, Long perfilId) {
        // Solo quien COLABORA en el proyecto entrega (el PM la revisa).
        boolean pertenece = asignacionRepository.findByProyectoIdAndPerfilIdAndEstado(proyectoId, perfilId, "activa")
                .filter(a -> Roles.COLABORADOR.equals(a.getRolEnProyecto()))
                .isPresent();
        if (!pertenece) throw new OperacionInvalidaException("No eres colaborador de este proyecto.");
    }

    @Transactional(readOnly = true)
    public List<EntregableFila> misEntregables(Long perfilId) {
        List<Entregable> entregables = entregableRepository.listarVisiblesParaPerfil(perfilId);
        Set<Long> proyectoIds = entregables.stream().map(Entregable::getProyectoId).collect(Collectors.toCollection(HashSet::new));
        java.util.Map<Long, Integer> equipoPorProyecto = proyectoIds.stream()
                .collect(Collectors.toMap(id -> id, id -> (int) asignacionRepository.listarEquipoDeProyecto(id, "activa").stream()
                        .filter(a -> Roles.COLABORADOR.equals(a.getRolEnProyecto())).count()));

        return entregables.stream().map(e -> {
            Entrega mia = entregaRepository.findByEntregableIdAndPerfilId(e.getId(), perfilId).orElse(null);
            EntregaFila fila = null;
            if (mia != null) {
                boolean atrasado = mia.getFechaEntrega() != null && mia.getFechaEntrega().isAfter(e.getFechaCierre());
                fila = new EntregaFila(mia.getId(), e.getId(), perfilId, null, true, mia.getFechaEntrega(), atrasado,
                        mia.getTexto(), mia.getUrlEntrega(), mia.tieneArchivo(), mia.getArchivoNombre(),
                        mia.getEstado(), mia.getComentarioPm());
            }
            return aFila(e, equipoPorProyecto.getOrDefault(e.getProyectoId(), 0), fila);
        }).collect(Collectors.toList());
    }

    @Transactional
    public void entregar(Long entregableId, Long perfilId, Long actorUsuarioId,
                          String texto, String urlEntrega, MultipartFile archivo) {
        Entregable e = obtener(entregableId);
        verificarPertenece(e.getProyectoId(), perfilId);
        if ("cancelado".equals(e.getEstado())) throw new OperacionInvalidaException("Este entregable fue cancelado por el PM.");

        boolean hayTexto = texto != null && !texto.isBlank();
        boolean hayUrl = urlEntrega != null && !urlEntrega.isBlank();
        boolean hayArchivo = archivo != null && !archivo.isEmpty();
        if (!hayTexto && !hayUrl && !hayArchivo) {
            throw new OperacionInvalidaException("Adjunta al menos texto, un link o un archivo.");
        }

        Entrega en = entregaRepository.findByEntregableIdAndPerfilId(entregableId, perfilId).orElseGet(Entrega::new);
        if ("completado".equals(en.getEstado())) {
            throw new OperacionInvalidaException("Tu entrega ya fue marcada como completada; no se puede reemplazar.");
        }
        en.setEntregableId(entregableId);
        en.setPerfilId(perfilId);
        en.setTexto(hayTexto ? texto.trim() : null);
        en.setUrlEntrega(hayUrl ? urlEntrega.trim() : null);
        if (hayArchivo) {
            byte[] contenido;
            try {
                contenido = archivo.getBytes();
            } catch (IOException ex) {
                throw new OperacionInvalidaException("No se pudo procesar el archivo.");
            }
            String nombre = nombreSeguro(archivo.getOriginalFilename());
            en.setArchivoTipo(validarArchivo(archivo, nombre, contenido));
            en.setArchivoContenido(contenido);
            en.setArchivoNombre(nombre);
        }
        // Reemplazar/quitar el archivo si ya existía uno y ahora no viene ninguno nuevo
        // se maneja en editar(); acá (primera entrega o "entregar de nuevo" trae siempre
        // el estado completo del formulario).
        // Si vuelve a entregar después de "requiere_ajustes" o "rehacer", se reabre a
        // "enviado" (Por revisar): el comentario anterior queda visible hasta la nueva revisión.
        boolean reenvio = en.getId() != null;
        en.setEstado("enviado");
        entregaRepository.save(en);

        auditoriaService.registrar(actorUsuarioId, reenvio ? "ENTREGA_ACTUALIZADA" : "ENTREGA_REALIZADA",
                "entrega", en.getId(), null, null, e.getTitulo());

        notificarEntregaAlPm(e, perfilId, reenvio);
    }

    /** Avisa (en la app y por correo) al/los PM del proyecto que un colaborador entregó o reenvió. */
    private void notificarEntregaAlPm(Entregable e, Long colaboradorPerfilId, boolean reenvio) {
        Proyecto proyecto = proyectoRepository.findById(e.getProyectoId()).orElse(null);
        List<Asignacion> equipo = asignacionRepository.listarEquipoDeProyecto(e.getProyectoId(), "activa");
        String nombreColaborador = equipo.stream()
                .filter(a -> a.getPerfilId().equals(colaboradorPerfilId))
                .findFirst()
                .map(a -> a.getPerfil().getUsuario().getNombreCompleto())
                .orElse("Un colaborador");
        String nombreProyecto = proyecto != null ? proyecto.getNombre() : "";
        equipo.stream()
                .filter(a -> Roles.PROJECT_MANAGER.equals(a.getRolEnProyecto()))
                .forEach(pm -> notificacionService.crear(pm.getPerfilId(),
                        "entregable_entregado",
                        (reenvio ? "Entrega actualizada: " : "Nueva entrega: ") + e.getTitulo(),
                        nombreColaborador + " " + (reenvio ? "actualizó su entrega" : "entregó") + " en \""
                                + nombreProyecto + "\".",
                        "/project-manager/entregables/" + e.getId() + ".html"));
    }

    @Transactional
    public void eliminarEntrega(Long entregableId, Long perfilId, Long actorUsuarioId) {
        Entrega en = entregaRepository.findByEntregableIdAndPerfilId(entregableId, perfilId)
                .orElseThrow(() -> new OperacionInvalidaException("No tienes una entrega para borrar."));
        if ("completado".equals(en.getEstado())) {
            throw new OperacionInvalidaException("Tu entrega ya fue marcada como completada; no se puede borrar.");
        }
        verificarPertenece(en.getEntregable() != null ? en.getEntregable().getProyectoId() : obtener(entregableId).getProyectoId(), perfilId);
        entregaRepository.delete(en);
        auditoriaService.registrar(actorUsuarioId, "ENTREGA_ELIMINADA", "entrega", entregableId, null, null,
                "Perfil " + perfilId);
    }

    /**
     * Valida tamaño, tipo declarado, extensión y (para PDF/imágenes, que se
     * abren en el navegador) la firma real del contenido. Devuelve el tipo
     * MIME que se guardará, decidido por el servidor.
     */
    private String validarArchivo(MultipartFile archivo, String nombre, byte[] contenido) {
        if (archivo.getSize() > MAX_ARCHIVO_BYTES) {
            throw new OperacionInvalidaException("El archivo no puede superar los 15 MB.");
        }
        String tipo = archivo.getContentType();
        int punto = nombre.lastIndexOf('.');
        String extension = punto >= 0 ? nombre.substring(punto + 1).toLowerCase(Locale.ROOT) : "";
        String mime = MIME_POR_EXTENSION.get(extension);
        if (tipo == null || !TIPOS_PERMITIDOS.contains(tipo) || mime == null) {
            throw new OperacionInvalidaException("Formato no permitido. Usa PDF, Word, Excel, imagen, ZIP o texto plano.");
        }
        if (!firmaCoincide(extension, contenido)) {
            throw new OperacionInvalidaException("El contenido del archivo no corresponde a su extensión ." + extension + ".");
        }
        return mime;
    }

    /** "Magic numbers" de los formatos que el navegador abre directamente. */
    private static boolean firmaCoincide(String extension, byte[] b) {
        switch (extension) {
            case "pdf":
                return empiezaCon(b, 0x25, 0x50, 0x44, 0x46, 0x2D);                 // %PDF-
            case "png":
                return empiezaCon(b, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
            case "jpg":
            case "jpeg":
                return empiezaCon(b, 0xFF, 0xD8, 0xFF);
            case "webp":
                return b.length >= 12 && empiezaCon(b, 0x52, 0x49, 0x46, 0x46)       // RIFF....WEBP
                        && b[8] == 0x57 && b[9] == 0x45 && b[10] == 0x42 && b[11] == 0x50;
            case "zip":
            case "docx":
            case "xlsx":
                return empiezaCon(b, 0x50, 0x4B);                                     // PK
            default:
                return true;
        }
    }

    private static boolean empiezaCon(byte[] b, int... firma) {
        if (b == null || b.length < firma.length) return false;
        for (int i = 0; i < firma.length; i++) {
            if ((b[i] & 0xFF) != firma[i]) return false;
        }
        return true;
    }

    /** Solo el nombre (sin rutas), sin saltos de línea ni comillas, máximo 200 caracteres. */
    private static String nombreSeguro(String original) {
        if (original == null || original.isBlank()) return "entrega";
        String limpio = original.replace("\\", "/");
        limpio = limpio.substring(limpio.lastIndexOf('/') + 1).replaceAll("[\\r\\n\"<>]", "").trim();
        if (limpio.length() > 200) {
            int punto = limpio.lastIndexOf('.');
            String ext = punto > 0 && limpio.length() - punto <= 10 ? limpio.substring(punto) : "";
            limpio = limpio.substring(0, 200 - ext.length()) + ext;
        }
        return limpio.isEmpty() ? "entrega" : limpio;
    }

    // ───────────────────────── Común ─────────────────────────

    private EntregableFila aFila(Entregable e, int totalEquipo, EntregaFila miEntrega) {
        int entregados = (int) entregaRepository.countByEntregableId(e.getId());
        int completados = (int) entregaRepository.countByEntregableIdAndEstado(e.getId(), "completado");
        int revisados = completados
                + (int) entregaRepository.countByEntregableIdAndEstado(e.getId(), "requiere_ajustes")
                + (int) entregaRepository.countByEntregableIdAndEstado(e.getId(), "rehacer");
        Proyecto p = e.getProyecto() != null ? e.getProyecto() : proyectoRepository.findById(e.getProyectoId()).orElse(null);
        return new EntregableFila(e.getId(), e.getProyectoId(), p != null ? p.getNombre() : "—", e.getTitulo(),
                e.getDescripcion(), e.getFechaApertura(), e.getFechaCierre(),
                e.getEstado(), totalEquipo, entregados, revisados, completados, miEntrega);
    }
}
