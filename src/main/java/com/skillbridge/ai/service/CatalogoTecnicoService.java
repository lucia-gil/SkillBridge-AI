package com.skillbridge.ai.service;

import com.skillbridge.ai.model.TipoAudiencia;
import com.skillbridge.ai.model.TipoEvento;
import com.skillbridge.ai.model.TipoNotificacion;
import com.skillbridge.ai.repository.EventoProyectoRepository;
import com.skillbridge.ai.repository.NotificacionRepository;
import com.skillbridge.ai.repository.TipoAudienciaRepository;
import com.skillbridge.ai.repository.TipoEventoRepository;
import com.skillbridge.ai.repository.TipoNotificacionRepository;
import com.skillbridge.ai.util.OperacionInvalidaException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * CRUD de los 3 catálogos técnicos chicos (Módulo 4 del Admin):
 * tipos_notificacion, tipos_evento, tipos_audiencia. Comparten la misma
 * forma (código, nombre, descripción; tipos_evento además tiene color),
 * por eso viven en un solo Service en vez de tres casi idénticos.
 *
 * Las 3 validaciones de "no eliminar si está en uso" SÍ aplican ahora:
 * tipos_notificacion se valida contra `notificaciones` (NotificacionRepository),
 * y tipos_evento/tipos_audiencia contra `eventos_proyecto`
 * (EventoProyectoRepository, ya construido por el equipo para el
 * calendario) - antes de que existiera esa tabla, esos dos catálogos no
 * tenían nada real que pudiera romperse; ahora sí.
 */
@Service
public class CatalogoTecnicoService {

    private final TipoNotificacionRepository tipoNotificacionRepository;
    private final TipoEventoRepository tipoEventoRepository;
    private final TipoAudienciaRepository tipoAudienciaRepository;
    private final NotificacionRepository notificacionRepository;
    private final EventoProyectoRepository eventoProyectoRepository;
    private final AuditoriaService auditoriaService;

    public CatalogoTecnicoService(TipoNotificacionRepository tipoNotificacionRepository,
                                  TipoEventoRepository tipoEventoRepository,
                                  TipoAudienciaRepository tipoAudienciaRepository,
                                  NotificacionRepository notificacionRepository,
                                  EventoProyectoRepository eventoProyectoRepository,
                                  AuditoriaService auditoriaService) {
        this.tipoNotificacionRepository = tipoNotificacionRepository;
        this.tipoEventoRepository = tipoEventoRepository;
        this.tipoAudienciaRepository = tipoAudienciaRepository;
        this.notificacionRepository = notificacionRepository;
        this.eventoProyectoRepository = eventoProyectoRepository;
        this.auditoriaService = auditoriaService;
    }

    // ===================== TIPOS_NOTIFICACION =====================

    public List<TipoNotificacion> listarTiposNotificacion() {
        return tipoNotificacionRepository.findAll();
    }

    @Transactional
    public void crearTipoNotificacion(String codigo, String nombre, String descripcion, Long actorId) {
        validarCodigo(codigo);
        if (tipoNotificacionRepository.existsByCodigo(codigo)) {
            throw new OperacionInvalidaException("Ya existe un tipo de notificación con ese código.");
        }
        TipoNotificacion t = new TipoNotificacion();
        t.setCodigo(codigo.trim());
        t.setNombre(nombre);
        t.setDescripcion(descripcion);
        tipoNotificacionRepository.save(t);
        auditoriaService.registrar(actorId, "TIPO_NOTIFICACION_CREADO", "tipos_notificacion", t.getId(), null, null, nombre);
    }

    @Transactional
    public void editarTipoNotificacion(Long id, String nombre, String descripcion, Long actorId) {
        TipoNotificacion t = tipoNotificacionRepository.findById(id)
                .orElseThrow(() -> new OperacionInvalidaException("Ese tipo de notificación ya no existe."));
        t.setNombre(nombre);
        t.setDescripcion(descripcion);
        tipoNotificacionRepository.save(t);
        auditoriaService.registrar(actorId, "TIPO_NOTIFICACION_EDITADO", "tipos_notificacion", id, null, null, nombre);
    }

    @Transactional
    public void eliminarTipoNotificacion(Long id, Long actorId) {
        TipoNotificacion t = tipoNotificacionRepository.findById(id)
                .orElseThrow(() -> new OperacionInvalidaException("Ese tipo de notificación ya no existe."));
        if (notificacionRepository.countByTipoId(id) > 0) {
            throw new OperacionInvalidaException(
                    "No se puede eliminar \"" + t.getNombre() + "\": ya hay notificaciones creadas con este tipo.");
        }
        tipoNotificacionRepository.delete(t);
        auditoriaService.registrar(actorId, "TIPO_NOTIFICACION_ELIMINADO", "tipos_notificacion", id, null, null, t.getNombre());
    }

    // ===================== TIPOS_EVENTO =====================

    public List<TipoEvento> listarTiposEvento() {
        return tipoEventoRepository.findAll();
    }

    @Transactional
    public void crearTipoEvento(String codigo, String nombre, String descripcion, String color, Long actorId) {
        validarCodigo(codigo);
        if (tipoEventoRepository.existsByCodigo(codigo)) {
            throw new OperacionInvalidaException("Ya existe un tipo de evento con ese código.");
        }
        TipoEvento t = new TipoEvento();
        t.setCodigo(codigo.trim());
        t.setNombre(nombre);
        t.setDescripcion(descripcion);
        t.setColor(color);
        tipoEventoRepository.save(t);
        auditoriaService.registrar(actorId, "TIPO_EVENTO_CREADO", "tipos_evento", t.getId(), null, null, nombre);
    }

    @Transactional
    public void editarTipoEvento(Long id, String nombre, String descripcion, String color, Long actorId) {
        TipoEvento t = tipoEventoRepository.findById(id)
                .orElseThrow(() -> new OperacionInvalidaException("Ese tipo de evento ya no existe."));
        t.setNombre(nombre);
        t.setDescripcion(descripcion);
        t.setColor(color);
        tipoEventoRepository.save(t);
        auditoriaService.registrar(actorId, "TIPO_EVENTO_EDITADO", "tipos_evento", id, null, null, nombre);
    }

    @Transactional
    public void eliminarTipoEvento(Long id, Long actorId) {
        TipoEvento t = tipoEventoRepository.findById(id)
                .orElseThrow(() -> new OperacionInvalidaException("Ese tipo de evento ya no existe."));
        if (eventoProyectoRepository.countByTipoId(id) > 0) {
            throw new OperacionInvalidaException(
                    "No se puede eliminar \"" + t.getNombre() + "\": ya hay eventos del calendario creados con este tipo.");
        }
        tipoEventoRepository.delete(t);
        auditoriaService.registrar(actorId, "TIPO_EVENTO_ELIMINADO", "tipos_evento", id, null, null, t.getNombre());
    }

    // ===================== TIPOS_AUDIENCIA =====================

    public List<TipoAudiencia> listarTiposAudiencia() {
        return tipoAudienciaRepository.findAll();
    }

    @Transactional
    public void crearTipoAudiencia(String codigo, String nombre, String descripcion, Long actorId) {
        validarCodigo(codigo);
        if (tipoAudienciaRepository.existsByCodigo(codigo)) {
            throw new OperacionInvalidaException("Ya existe un tipo de audiencia con ese código.");
        }
        TipoAudiencia t = new TipoAudiencia();
        t.setCodigo(codigo.trim());
        t.setNombre(nombre);
        t.setDescripcion(descripcion);
        tipoAudienciaRepository.save(t);
        auditoriaService.registrar(actorId, "TIPO_AUDIENCIA_CREADO", "tipos_audiencia", t.getId(), null, null, nombre);
    }

    @Transactional
    public void editarTipoAudiencia(Long id, String nombre, String descripcion, Long actorId) {
        TipoAudiencia t = tipoAudienciaRepository.findById(id)
                .orElseThrow(() -> new OperacionInvalidaException("Ese tipo de audiencia ya no existe."));
        t.setNombre(nombre);
        t.setDescripcion(descripcion);
        tipoAudienciaRepository.save(t);
        auditoriaService.registrar(actorId, "TIPO_AUDIENCIA_EDITADO", "tipos_audiencia", id, null, null, nombre);
    }

    @Transactional
    public void eliminarTipoAudiencia(Long id, Long actorId) {
        TipoAudiencia t = tipoAudienciaRepository.findById(id)
                .orElseThrow(() -> new OperacionInvalidaException("Ese tipo de audiencia ya no existe."));
        if (eventoProyectoRepository.countByAudienciaId(id) > 0) {
            throw new OperacionInvalidaException(
                    "No se puede eliminar \"" + t.getNombre() + "\": ya hay eventos del calendario dirigidos a esta audiencia.");
        }
        tipoAudienciaRepository.delete(t);
        auditoriaService.registrar(actorId, "TIPO_AUDIENCIA_ELIMINADO", "tipos_audiencia", id, null, null, t.getNombre());
    }

    // ===================== Validación compartida =====================

    private void validarCodigo(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            throw new OperacionInvalidaException("El código es obligatorio (ej. 'entregable', 'reunion').");
        }
    }
}