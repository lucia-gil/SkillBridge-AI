package com.skillbridge.ai.repository;

import com.skillbridge.ai.model.AuditoriaLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;

import java.util.List;

public interface AuditoriaLogRepository extends JpaRepository<AuditoriaLog, Long> {

    // Tope de 200 eventos: la pantalla de Auditoria no trae paginación en el
    // mockup original, así que se acota a los más recientes en vez de traer
    // la tabla completa sin límite.
    List<AuditoriaLog> findTop200ByOrderByFechaDesc();

    @Query("select a from AuditoriaLog a where " +
            "(:desde is null or a.fecha >= :desde) and (:hasta is null or a.fecha < :hasta) " +
            "and (:accion = '' or lower(a.accion) like lower(concat('%', :accion, '%'))) " +
            "and (:usuario = '' or exists (select u.id from Usuario u where u.id = a.usuarioId " +
            "and lower(u.nombreCompleto) like lower(concat('%', :usuario, '%'))))")
    Page<AuditoriaLog> buscar(@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta,
                             @Param("usuario") String usuario, @Param("accion") String accion, Pageable pageable);

    long countByFechaAfter(java.time.LocalDateTime desde);

    @Query("select a.usuarioId, count(a) from AuditoriaLog a where a.usuarioId is not null group by a.usuarioId order by count(a) desc")
    List<Object[]> topUsuariosPorEventos(Pageable pageable);
}
