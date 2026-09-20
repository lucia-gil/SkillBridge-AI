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
import com.skillbridge.ai.util.Roles;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * "Avance real" de un proyecto (RF nuevo, sección Entregables): a
 * diferencia del % heurístico de ProyectoService (por tiempo transcurrido),
 * este módulo mide trabajo real: el PM (o Administrador) crea entregables
 * sobre un proyecto, cada colaborador del equipo activo sube su entrega
 * (texto/link/archivo), y el PM la revisa con nota (0-20) y comentario.
 *
 * Reglas de negocio:
 *  - Solo puede crear/editar/cancelar entregables el PM activo del
 *    proyecto o un Administrador (verificarGestion()).
 *  - Solo puede entregar/editar/borrar SU propia entrega un colaborador
 *    con asignación activa en ese proyecto (verificarPertenece()).
 *  - Se puede entregar (o reemplazar la entrega) incluso después de
 *    fecha_cierre — igual que Moodle: no se bloquea, se marca "atrasado"
 *    (calculado, no guardado) para que el PM lo vea al calificar.
 *  - Un entregable "cancelado" ya no acepta entregas nuevas, pero las que
 *    ya existían se conservan (no se borran) para no perder trabajo/notas.
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
                             String fechaCierreIso, BigDecimal puntajeMaximo) {
        verificarGestion(proyectoId, actorPerfilId, rolEfectivo);
        Proyecto proyecto = proyectoRepository.findById(proyectoId)
                .orElseThrow(() -> new OperacionInvalidaException("El proyecto no existe."));
        LocalDateTime fechaApertura = parsearFecha(fechaAperturaIso);
        LocalDateTime fechaCierre = parsearFecha(fechaCierreIso);
        validarCampos(titulo, fechaApertura, fechaCierre, puntajeMaximo);

        Entregable e = new Entregable();
        e.setProyectoId(proyectoId);
        e.setCreadoPorId(actorPerfilId);
        e.setTitulo(titulo.trim());
        e.setDescripcion(descripcion != null && !descripcion.isBlank() ? descripcion.trim() : null);
        e.setFechaApertura(fechaApertura);
        e.setFechaCierre(fechaCierre);
        e.setPuntajeMaximo(puntajeMaximo);
        e = entregableRepository.save(e);

        List<Asignacion> equipo = asignacionRepository.listarEquipoDeProyecto(proyectoId, "activa");
        for (Asignacion a : equipo) {
            if (Roles.COLABORADOR.equals(a.getRolEnProyecto())) {
                notificacionService.crear(a.getPerfilId(), "entregable_nuevo", "Nuevo entregable: " + e.getTitulo(),
                        "En \"" + proyecto.getNombre() + "\" · cierra el " + fechaCierre.toLocalDate() + ".",
                        "proyectos.html");
            }
        }
        auditoriaService.registrar(actorUsuarioId, "ENTREGABLE_CREADO", "entregable", e.getId(), null, null,
                e.getTitulo() + " · " + proyecto.getNombre());
        return e;
    }

    @Transactional
    public void editar(Long entregableId, Long actorPerfilId, String rolEfectivo, Long actorUsuarioId,
                        String titulo, String descripcion, String fechaAperturaIso, String fechaCierreIso,
                        BigDecimal puntajeMaximo) {
        Entregable e = entregableRepository.findById(entregableId)
                .orElseThrow(() -> new OperacionInvalidaException("El entregable ya no existe."));
        verificarGestion(e.getProyectoId(), actorPerfilId, rolEfectivo);
        LocalDateTime fechaApertura = parsearFecha(fechaAperturaIso);
        LocalDateTime fechaCierre = parsearFecha(fechaCierreIso);
        validarCampos(titulo, fechaApertura, fechaCierre, puntajeMaximo);

        e.setTitulo(titulo.trim());
        e.setDescripcion(descripcion != null && !descripcion.isBlank() ? descripcion.trim() : null);
        e.setFechaApertura(fechaApertura);
        e.setFechaCierre(fechaCierre);
        e.setPuntajeMaximo(puntajeMaximo);
        entregableRepository.save(e);

        auditoriaService.registrar(actorUsuarioId, "ENTREGABLE_EDITADO", "entregable", e.getId(), null, null,
                e.getTitulo());
    }

    /** No se borra físicamente (se perdería la trazabilidad de lo ya entregado/calificado): se cancela. */
    @Transactional
    public void cancelar(Long entregableId, Long actorPerfilId, String rolEfectivo, Long actorUsuarioId) {
        Entregable e = entregableRepository.findById(entregableId)
                .orElseThrow(() -> new OperacionInvalidaException("El entregable ya no existe."));
        verificarGestion(e.getProyectoId(), actorPerfilId, rolEfectivo);
        e.setEstado("cancelado");
        entregableRepository.save(e);
        auditoriaService.registrar(actorUsuarioId, "ENTREGABLE_CANCELADO", "entregable", e.getId(), null, null,
                e.getTitulo());
    }

    private LocalDateTime parsearFecha(String iso) {
        if (iso == null || iso.isBlank()) throw new OperacionInvalidaException("Selecciona fecha y hora.");
        try {
            return LocalDateTime.parse(iso);
        } catch (Exception ex) {
            throw new OperacionInvalidaException("Fecha u hora no válida.");
        }
    }

    private void validarCampos(String titulo, LocalDateTime apertura, LocalDateTime cierre, BigDecimal puntajeMaximo) {
        if (titulo == null || titulo.isBlank()) throw new OperacionInvalidaException("Ingresa un título para el entregable.");
        if (apertura == null || cierre == null) throw new OperacionInvalidaException("Selecciona apertura y cierre.");
        if (!cierre.isAfter(apertura)) throw new OperacionInvalidaException("El cierre debe ser posterior a la apertura.");
        if (puntajeMaximo == null || puntajeMaximo.signum() <= 0) throw new OperacionInvalidaException("El puntaje máximo debe ser mayor que cero.");
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
                        null, false, null, null, false, null, "sin_entregar", null, null);
            }
            boolean atrasado = en.getFechaEntrega() != null && en.getFechaEntrega().isAfter(e.getFechaCierre());
            return new EntregaFila(en.getId(), entregableId, a.getPerfilId(), nombre, true,
                    en.getFechaEntrega(), atrasado, en.getTexto(), en.getUrlEntrega(),
                    en.tieneArchivo(), en.getArchivoNombre(), en.getEstado(), en.getCalificacion(), en.getComentarioPm());
        }).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Entregable obtener(Long entregableId) {
        return entregableRepository.findById(entregableId)
                .orElseThrow(() -> new OperacionInvalidaException("El entregable ya no existe."));
    }

    @Transactional
    public void calificar(Long entregaId, Long actorPerfilId, String rolEfectivo, Long actorUsuarioId,
                           BigDecimal calificacion, String comentario) {
        Entrega en = entregaRepository.findById(entregaId)
                .orElseThrow(() -> new OperacionInvalidaException("La entrega ya no existe."));
        Entregable e = en.getEntregable() != null ? en.getEntregable() : obtener(en.getEntregableId());
        verificarGestion(e.getProyectoId(), actorPerfilId, rolEfectivo);

        if (calificacion != null && (calificacion.signum() < 0 || calificacion.compareTo(e.getPuntajeMaximo()) > 0)) {
            throw new OperacionInvalidaException("La calificación debe estar entre 0 y " + e.getPuntajeMaximo() + ".");
        }
        en.setCalificacion(calificacion);
        en.setComentarioPm(comentario != null && !comentario.isBlank() ? comentario.trim() : null);
        en.setEstado("revisado");
        en.setRevisadoPorId(actorPerfilId);
        en.setFechaRevision(LocalDateTime.now());
        entregaRepository.save(en);

        notificacionService.crear(en.getPerfilId(), "entregable_revisado", "Tu entrega fue revisada: " + e.getTitulo(),
                calificacion != null ? "Calificación: " + calificacion + "/" + e.getPuntajeMaximo() : "El PM dejó un comentario.",
                "proyectos.html");
        auditoriaService.registrar(actorUsuarioId, "ENTREGA_CALIFICADA", "entrega", en.getId(), null, null,
                e.getTitulo() + " · " + (calificacion != null ? calificacion + "/" + e.getPuntajeMaximo() : "sin nota"));
    }

    // ───────────────────────── Colaborador ─────────────────────────

    private void verificarPertenece(Long proyectoId, Long perfilId) {
        boolean pertenece = asignacionRepository.findByProyectoIdAndPerfilIdAndEstado(proyectoId, perfilId, "activa").isPresent();
        if (!pertenece) throw new OperacionInvalidaException("No perteneces a este proyecto.");
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
                        mia.getEstado(), mia.getCalificacion(), mia.getComentarioPm());
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
        en.setEntregableId(entregableId);
        en.setPerfilId(perfilId);
        en.setTexto(hayTexto ? texto.trim() : null);
        en.setUrlEntrega(hayUrl ? urlEntrega.trim() : null);
        if (hayArchivo) {
            validarArchivo(archivo);
            try {
                en.setArchivoContenido(archivo.getBytes());
            } catch (IOException ex) {
                throw new OperacionInvalidaException("No se pudo procesar el archivo.");
            }
            en.setArchivoTipo(archivo.getContentType() != null ? archivo.getContentType() : "application/octet-stream");
            String original = archivo.getOriginalFilename();
            en.setArchivoNombre(original != null && !original.isBlank() ? original : "entrega");
        }
        // Reemplazar/quitar el archivo si ya existía uno y ahora no viene ninguno nuevo
        // se maneja en editar(); acá (primera entrega o "entregar de nuevo" trae siempre
        // el estado completo del formulario).
        // Si vuelve a entregar después de que ya estaba revisada, se reabre a "enviado":
        // la nota/comentario anteriores quedan pero deben volver a confirmarse.
        boolean reenvio = en.getId() != null;
        en.setEstado("enviado");
        entregaRepository.save(en);

        auditoriaService.registrar(actorUsuarioId, reenvio ? "ENTREGA_ACTUALIZADA" : "ENTREGA_REALIZADA",
                "entrega", en.getId(), null, null, e.getTitulo());
    }

    @Transactional
    public void eliminarEntrega(Long entregableId, Long perfilId, Long actorUsuarioId) {
        Entrega en = entregaRepository.findByEntregableIdAndPerfilId(entregableId, perfilId)
                .orElseThrow(() -> new OperacionInvalidaException("No tienes una entrega para borrar."));
        verificarPertenece(en.getEntregable() != null ? en.getEntregable().getProyectoId() : obtener(entregableId).getProyectoId(), perfilId);
        entregaRepository.delete(en);
        auditoriaService.registrar(actorUsuarioId, "ENTREGA_ELIMINADA", "entrega", entregableId, null, null,
                "Perfil " + perfilId);
    }

    private void validarArchivo(MultipartFile archivo) {
        if (archivo.getSize() > MAX_ARCHIVO_BYTES) {
            throw new OperacionInvalidaException("El archivo no puede superar los 15 MB.");
        }
        String tipo = archivo.getContentType();
        if (tipo == null || !TIPOS_PERMITIDOS.contains(tipo)) {
            throw new OperacionInvalidaException("Formato no permitido. Usa PDF, Word, Excel, imagen, ZIP o texto plano.");
        }
    }

    // ───────────────────────── Común ─────────────────────────

    private EntregableFila aFila(Entregable e, int totalEquipo, EntregaFila miEntrega) {
        int entregados = (int) entregaRepository.countByEntregableId(e.getId());
        int revisados = (int) entregaRepository.countByEntregableIdAndEstado(e.getId(), "revisado");
        Proyecto p = e.getProyecto() != null ? e.getProyecto() : proyectoRepository.findById(e.getProyectoId()).orElse(null);
        return new EntregableFila(e.getId(), e.getProyectoId(), p != null ? p.getNombre() : "—", e.getTitulo(),
                e.getDescripcion(), e.getFechaApertura(), e.getFechaCierre(),
                e.getPuntajeMaximo().stripTrailingZeros().toPlainString(),
                e.getEstado(), totalEquipo, entregados, revisados, miEntrega);
    }
}
