package com.skillbridge.ai.repository;

import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.skillbridge.ai.model.Proyecto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProyectoRepository extends JpaRepository<Proyecto, Long> {

    List<Proyecto> findAllByOrderByFechaCreacionDesc();

    long countByEstado(String estado);

    // Usado para bloquear la creacion de proyectos con nombre duplicado
    // (RF03: nombre no es UNIQUE a nivel de esquema, se valida en servicio).
    boolean existsByNombreIgnoreCase(String nombre);

    // ── Agente IA (service/agente) ──

    /** El proyecto bloqueado (FOR UPDATE) mientras el agente aplica una edición confirmada. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Proyecto p where p.id = :id")
    Optional<Proyecto> bloquear(@Param("id") Long id);
}
