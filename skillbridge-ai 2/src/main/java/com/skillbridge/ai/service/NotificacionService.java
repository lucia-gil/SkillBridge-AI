package com.skillbridge.ai.service;

import com.skillbridge.ai.dto.NotificacionFila;
import com.skillbridge.ai.model.Notificacion;
import com.skillbridge.ai.model.TipoNotificacion;
import com.skillbridge.ai.repository.NotificacionRepository;
import com.skillbridge.ai.repository.TipoNotificacionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Centro de notificaciones real (tabla "notificaciones" + "tipos_notificacion").
 * No hay ningun generador de notificaciones de fabrica: se crean como efecto
 * secundario de acciones reales de esta entrega (ver llamadas a crear(...)
 * desde ProyectoService al asignar un colaborador y desde ForoService al
 * responder un hilo) - por eso una base de datos recien creada empieza sin
 * notificaciones, hasta que ocurre la primera accion que las genera.
 */
@Service
public class NotificacionService {

    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final NotificacionRepository notificacionRepository;
    private final TipoNotificacionRepository tipoNotificacionRepository;

    public NotificacionService(NotificacionRepository notificacionRepository, TipoNotificacionRepository tipoNotificacionRepository) {
        this.notificacionRepository = notificacionRepository;
        this.tipoNotificacionRepository = tipoNotificacionRepository;
    }

    @Transactional
    public void crear(Long perfilId, String tipoCodigo, String titulo, String detalle, String enlaceAccion) {
        if (perfilId == null) return;
        Optional<TipoNotificacion> tipo = tipoNotificacionRepository.findByCodigo(tipoCodigo);
        if (tipo.isEmpty()) return; // catálogo sin ese código: no se rompe el flujo principal por esto
        Notificacion n = new Notificacion();
        n.setPerfilId(perfilId);
        n.setTipoId(tipo.get().getId());
        n.setTitulo(titulo);
        n.setDetalle(detalle);
        n.setEnlaceAccion(enlaceAccion);
        notificacionRepository.save(n);
    }

    public List<NotificacionFila> listar(Long perfilId) {
        return notificacionRepository.listarPorPerfil(perfilId).stream()
                .map(n -> new NotificacionFila(n.getId(), n.getTitulo(), n.getDetalle(), Boolean.TRUE.equals(n.getLeida()),
                        n.getTipo().getCodigo(), n.getTipo().getNombre(),
                        n.getFechaCreacion() != null ? n.getFechaCreacion().format(FORMATO) : "",
                        n.getEnlaceAccion()))
                .collect(Collectors.toList());
    }

    public long contarNoLeidas(Long perfilId) {
        if (perfilId == null) return 0;
        return notificacionRepository.countByPerfilIdAndLeidaFalse(perfilId);
    }

    @Transactional
    public void marcarLeida(Long id, Long perfilId) {
        notificacionRepository.findById(id).ifPresent(n -> {
            if (n.getPerfilId().equals(perfilId)) {
                n.setLeida(true);
                notificacionRepository.save(n);
            }
        });
    }

    @Transactional
    public void marcarTodasLeidas(Long perfilId) {
        notificacionRepository.marcarTodasLeidas(perfilId);
    }
}
