package com.skillbridge.ai.repository;

import com.skillbridge.ai.model.EventoProyecto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EventoProyectoRepository extends JpaRepository<EventoProyecto, Long> {

    @Query("select e from EventoProyecto e join fetch e.tipo join fetch e.proyecto join fetch e.audiencia " +
           "where e.proyectoId in :ids order by e.fechaInicio asc")
    List<EventoProyecto> listarPorProyectos(@Param("ids") List<Long> ids);
}
