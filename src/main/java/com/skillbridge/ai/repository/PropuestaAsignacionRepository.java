package com.skillbridge.ai.repository;

import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import com.skillbridge.ai.model.PropuestaAsignacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PropuestaAsignacionRepository extends JpaRepository<PropuestaAsignacion, Long> {
    boolean existsByProyectoIdAndCandidatoPerfilIdAndEstado(Long proyectoId, Long candidatoPerfilId, String estado);

    // Borrado en cascada manual: fk_prop_proyecto NO tiene ON DELETE CASCADE,
    // asi que ProyectoService.eliminar() debe vaciar esta tabla para el
    // proyecto ANTES de borrar la fila de proyectos (mismo patron que
    // AsignacionRepository.deleteByProyectoId).
    void deleteByProyectoId(Long proyectoId);

    // Propuestas pendientes de un proyecto: el PM no puede proponer más
    // personas que vacantes libres descontando las que ya esperan al RM.
    long countByProyectoIdAndEstado(Long proyectoId, String estado);

    // Propuestas pendientes de un proyecto que se pausa o se cierra: se
    // rechazan solas con el motivo correspondiente (modelo v7, fase 2).
    List<PropuestaAsignacion> findByProyectoIdAndEstado(Long proyectoId, String estado);

    @Query("select p from PropuestaAsignacion p join fetch p.proyecto join fetch p.candidato c join fetch c.usuario " +
            "join fetch p.solicitante s join fetch s.usuario where p.estado = 'pendiente' order by p.fechaSolicitud asc")
    List<PropuestaAsignacion> listarPendientes();

    @Query("select p from PropuestaAsignacion p join fetch p.proyecto join fetch p.candidato c join fetch c.usuario " +
            "join fetch p.solicitante s join fetch s.usuario where p.estado <> 'pendiente' order by p.fechaResolucion desc")
    List<PropuestaAsignacion> listarResueltas();

    @Query("select p from PropuestaAsignacion p join fetch p.proyecto join fetch p.candidato c join fetch c.usuario " +
            "join fetch p.solicitante s join fetch s.usuario where p.id = :id")
    Optional<PropuestaAsignacion> buscarDetalle(@Param("id") Long id);

    // ── Agente IA (service/agente) ──

    @Query("select new com.skillbridge.ai.dto.AgenteSolicitudFila(p.id, p.proyectoId, p.candidatoPerfilId, " +
           "p.dedicacionPorcentaje, p.estado) from PropuestaAsignacion p where p.estado = 'pendiente' order by p.id desc")
    List<com.skillbridge.ai.dto.AgenteSolicitudFila> pendientesParaAgente(Pageable pagina);

    /** La propuesta bloqueada (FOR UPDATE) mientras el RM la resuelve desde el agente. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PropuestaAsignacion p where p.id = :id")
    Optional<PropuestaAsignacion> bloquear(@Param("id") Long id);
}
