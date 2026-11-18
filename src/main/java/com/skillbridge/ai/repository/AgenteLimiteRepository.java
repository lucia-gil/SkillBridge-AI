package com.skillbridge.ai.repository;

import com.skillbridge.ai.model.AgenteLimite;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AgenteLimiteRepository extends JpaRepository<AgenteLimite, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from AgenteLimite l where l.usuarioId = :usuarioId")
    Optional<AgenteLimite> bloquear(@Param("usuarioId") Long usuarioId);

    /** Libera el lease solo si sigue siendo el de esta solicitud. */
    @Modifying
    @Query("update AgenteLimite l set l.leaseHasta = 0, l.leaseId = null " +
           "where l.usuarioId = :usuarioId and l.leaseId = :leaseId")
    int liberar(@Param("usuarioId") Long usuarioId, @Param("leaseId") String leaseId);
}
