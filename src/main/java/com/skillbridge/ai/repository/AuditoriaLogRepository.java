package com.skillbridge.ai.repository;

import com.skillbridge.ai.model.AuditoriaLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface AuditoriaLogRepository extends JpaRepository<AuditoriaLog, Long> {

    // Tope de 200 eventos: la pantalla de Auditoria no trae paginación en el
    // mockup original, así que se acota a los más recientes en vez de traer
    // la tabla completa sin límite.
    List<AuditoriaLog> findTop200ByOrderByFechaDesc();

    long countByFechaAfter(java.time.LocalDateTime desde);

    @Query("select a.usuarioId, count(a) from AuditoriaLog a where a.usuarioId is not null group by a.usuarioId order by count(a) desc")
    List<Object[]> topUsuariosPorEventos(Pageable pageable);
}
