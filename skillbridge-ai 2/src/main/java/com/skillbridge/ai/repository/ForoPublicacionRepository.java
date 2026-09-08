package com.skillbridge.ai.repository;

import com.skillbridge.ai.model.ForoPublicacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ForoPublicacionRepository extends JpaRepository<ForoPublicacion, Long> {

    // Hilos raíz (publicacion_padre_id NULL) de los proyectos donde el
    // colaborador tiene o tuvo una asignación - "Foros" en este esquema no
    // es un espacio único global, cada post cuelga de un proyecto (ver
    // comentario de foro_publicaciones en skillbridge_db_v4.sql).
    @Query("select f from ForoPublicacion f join fetch f.autor a join fetch a.usuario join fetch f.proyecto where f.publicacionPadreId is null and f.proyectoId in :proyectoIds order by f.fechaPublicacion desc")
    List<ForoPublicacion> listarHilosDeProyectos(@Param("proyectoIds") List<Long> proyectoIds);

    @Query("select f from ForoPublicacion f join fetch f.autor a join fetch a.usuario join fetch f.proyecto where f.id = :id")
    Optional<ForoPublicacion> buscarHiloConDetalle(@Param("id") Long id);

    @Query("select f from ForoPublicacion f join fetch f.autor a join fetch a.usuario where f.publicacionPadreId = :padreId order by f.fechaPublicacion asc")
    List<ForoPublicacion> listarRespuestas(@Param("padreId") Long padreId);

    List<ForoPublicacion> findByPublicacionPadreId(Long padreId);

    long countByPublicacionPadreId(Long padreId);

    long countByProyectoIdAndPublicacionPadreIdIsNull(Long proyectoId);

    @Query("select f from ForoPublicacion f where f.publicacionPadreId is null and f.proyectoId = :proyectoId and f.id <> :excludeId order by f.fechaPublicacion desc")
    List<ForoPublicacion> listarRelacionados(@Param("proyectoId") Long proyectoId, @Param("excludeId") Long excludeId);
}
