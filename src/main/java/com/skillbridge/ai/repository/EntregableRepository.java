package com.skillbridge.ai.repository;

import com.skillbridge.ai.model.Entregable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface EntregableRepository extends JpaRepository<Entregable, Long> {

    List<Entregable> findByProyectoIdOrderByFechaCierreAsc(Long proyectoId);

    // Entregables de los proyectos donde el perfil COLABORA (asignación
    // activa con rol colaborador) para "Mis entregables", sin traer los
    // cancelados por el PM. Modelo v7: si la misma persona lidera otro
    // proyecto, los entregables de ese proyecto los gestiona en su vista PM,
    // no le aparecen aquí para "entregar".
    @Query("select e from Entregable e join fetch e.proyecto p " +
            "where e.proyectoId in (select a.proyectoId from Asignacion a where a.perfilId = :perfilId " +
            "and a.estado = 'activa' and a.rolEnProyecto = 'colaborador') " +
            "and e.estado = 'activo' order by e.fechaCierre asc")
    List<Entregable> listarVisiblesParaPerfil(@Param("perfilId") Long perfilId);

    long countByProyectoIdAndEstado(Long proyectoId, String estado);

    long countByProyectoId(Long proyectoId);

    // Para el KPI "Entregables vencidos" del reporte del PM: entregables
    // todavía activos (el PM no los retiró) cuya fecha de cierre ya pasó,
    // dentro del conjunto de proyectos visible según los filtros aplicados.
    @Query("select count(e) from Entregable e where e.proyectoId in :proyectoIds " +
            "and e.estado = 'activo' and e.fechaCierre < :ahora")
    long countVencidos(@Param("proyectoIds") List<Long> proyectoIds, @Param("ahora") LocalDateTime ahora);
}
