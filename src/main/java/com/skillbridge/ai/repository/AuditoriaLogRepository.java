package com.skillbridge.ai.repository;

import com.skillbridge.ai.model.AuditoriaLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Slice;
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

    String FILTROS = """
            (:desde is null or a.fecha >= :desde) and (:hasta is null or a.fecha < :hasta)
            and (:accion = '' or lower(a.accion) like lower(concat('%', :accion, '%')))
            and (:usuario = '' or exists (select u.id from Usuario u where u.id = a.usuarioId
            and lower(u.nombreCompleto) like lower(concat('%', :usuario, '%'))))
            """;

    @Query("select a from AuditoriaLog a where " + FILTROS)
    Page<AuditoriaLog> buscar(@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta,
                             @Param("usuario") String usuario, @Param("accion") String accion, Pageable pageable);

    interface DatosExportacion {
        Long getId();
        LocalDateTime getFecha();
        Long getUsuarioId();
        String getNombreUsuario();
        String getAccion();
        String getEntidad();
        Long getEntidadId();
        String getDetalle();
        String getAnterior();
        String getNuevo();
    }

    @Query("""
            select a.id as id, a.fecha as fecha, a.usuarioId as usuarioId,
                   u.nombreCompleto as nombreUsuario, a.accion as accion,
                   a.entidadAfectada as entidad, a.entidadId as entidadId,
                   a.detalle as detalle, a.valorAnterior as anterior, a.valorNuevo as nuevo
            from AuditoriaLog a left join Usuario u on u.id = a.usuarioId
            where
            """ + FILTROS)
    Slice<DatosExportacion> buscarExportacion(@Param("desde") LocalDateTime desde,
            @Param("hasta") LocalDateTime hasta, @Param("usuario") String usuario,
            @Param("accion") String accion, Pageable pageable);

    @Query("select a.accion, count(a) from AuditoriaLog a where " + FILTROS + " group by a.accion")
    List<Object[]> contarPorAccion(@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta,
                                  @Param("usuario") String usuario, @Param("accion") String accion);

    long countByFechaAfter(java.time.LocalDateTime desde);

    @Query("select a.usuarioId, count(a) from AuditoriaLog a where a.usuarioId is not null group by a.usuarioId order by count(a) desc")
    List<Object[]> topUsuariosPorEventos(Pageable pageable);
}
