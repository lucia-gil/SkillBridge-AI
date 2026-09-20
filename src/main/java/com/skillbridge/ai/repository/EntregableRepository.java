package com.skillbridge.ai.repository;

import com.skillbridge.ai.model.Entregable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EntregableRepository extends JpaRepository<Entregable, Long> {

    List<Entregable> findByProyectoIdOrderByFechaCierreAsc(Long proyectoId);

    // Entregables de TODOS los proyectos donde el perfil tiene una asignación
    // activa (para "Mis entregables" del colaborador), sin traer los
    // cancelados por el PM.
    @Query("select e from Entregable e join fetch e.proyecto p " +
            "where e.proyectoId in (select a.proyectoId from Asignacion a where a.perfilId = :perfilId and a.estado = 'activa') " +
            "and e.estado = 'activo' order by e.fechaCierre asc")
    List<Entregable> listarVisiblesParaPerfil(@Param("perfilId") Long perfilId);

    long countByProyectoIdAndEstado(Long proyectoId, String estado);
}
