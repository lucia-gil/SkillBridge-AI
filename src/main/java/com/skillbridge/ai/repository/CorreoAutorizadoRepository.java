package com.skillbridge.ai.repository;

import com.skillbridge.ai.model.CorreoAutorizado;
import org.springframework.data.jpa.repository.JpaRepository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CorreoAutorizadoRepository extends JpaRepository<CorreoAutorizado, Long> {

    Optional<CorreoAutorizado> findByCorreoIgnoreCase(String correo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CorreoAutorizado c where c.id = :id")
    Optional<CorreoAutorizado> bloquearPorId(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CorreoAutorizado c where c.correo = :correo")
    Optional<CorreoAutorizado> bloquearPorCorreo(@Param("correo") String correo);

    boolean existsByCorreoIgnoreCase(String correo);

    java.util.List<CorreoAutorizado> findAllByOrderByFechaAutorizacionDesc();

    /** Invitaciones que todavía no se usaron para crear una cuenta. */
    long countByUtilizadoFalse();
}
