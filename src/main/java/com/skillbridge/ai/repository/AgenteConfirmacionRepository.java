package com.skillbridge.ai.repository;

import com.skillbridge.ai.model.AgenteConfirmacion;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface AgenteConfirmacionRepository extends JpaRepository<AgenteConfirmacion, String> {

    /** La confirmación pendiente y vigente más reciente del usuario en ese rol. */
    Optional<AgenteConfirmacion> findFirstByUsuarioIdAndRolAndEstadoAndExpiraAfterOrderByCreadaDesc(
            Long usuarioId, String rol, String estado, Instant ahora);

    /** Una propuesta pendiente por usuario y rol: al preparar otra, la anterior se cancela. */
    @Modifying
    @Query("update AgenteConfirmacion c set c.estado = 'cancelada' " +
           "where c.usuarioId = :usuarioId and c.rol = :rol and c.estado = 'pendiente'")
    int cancelarPendientes(@Param("usuarioId") Long usuarioId, @Param("rol") String rol);

    /** La confirmación a resolver, bloqueada (FOR UPDATE) para que no se use dos veces. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from AgenteConfirmacion c where c.id = :id and c.usuarioId = :usuarioId and c.rol = :rol " +
           "and c.estado = 'pendiente' and c.expira > :ahora")
    Optional<AgenteConfirmacion> bloquearPendiente(@Param("id") String id, @Param("usuarioId") Long usuarioId,
                                                   @Param("rol") String rol, @Param("ahora") Instant ahora);
}
