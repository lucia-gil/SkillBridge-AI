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

    // Se usan desde el catálogo de tipos_evento/tipos_audiencia (Admin)
    // para bloquear que se elimine un tipo que ya tiene eventos reales
    // apuntándole - borrarlo dejaría esos eventos con una FK rota.
    long countByTipoId(Long tipoId);

    long countByAudienciaId(Long audienciaId);

    /**
     * Hitos y entregables de los proyectos en curso que ve todo el equipo
     * (audiencia "todos"): el Resource Manager los ve en su calendario, solo
     * para consulta, junto a su agenda personal.
     */
    @Query("select e from EventoProyecto e join fetch e.tipo t join fetch e.proyecto p join fetch e.audiencia a " +
            "where (a.codigo = 'todos' or (a.codigo not in ('solo_pm','solo_colaboradores') and a.politica='todos')) and t.codigo in ('hito', 'entregable') " +
            "and p.estado in ('planificacion', 'activo', 'en_pausa') order by e.fechaInicio asc")
    List<EventoProyecto> hitosYEntregablesEnCurso();
}