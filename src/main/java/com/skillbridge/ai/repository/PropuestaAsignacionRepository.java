package com.skillbridge.ai.repository;

import com.skillbridge.ai.model.PropuestaAsignacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PropuestaAsignacionRepository extends JpaRepository<PropuestaAsignacion, Long> {
    boolean existsByProyectoIdAndCandidatoPerfilIdAndEstado(Long proyectoId, Long candidatoPerfilId, String estado);

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
}
