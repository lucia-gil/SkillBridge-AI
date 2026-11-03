package com.skillbridge.ai.repository;

import com.skillbridge.ai.model.ForoPublicacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ForoPublicacionRepository extends JpaRepository<ForoPublicacion, Long> {

    // Hilos raíz (publicacion_padre_id NULL) de los proyectos donde el
    // colaborador tiene o tuvo una asignación - "Foros" en este esquema no
    // es un espacio único global, cada post cuelga de un proyecto (ver
    // comentario de foro_publicaciones en skillbridge_db.sql).
    // left join fetch del tópico: es NULL en hilos anteriores a la v6 que
    // todavía no se clasificaron.
    @Query("select f from ForoPublicacion f join fetch f.autor a join fetch a.usuario join fetch f.proyecto left join fetch f.topico " +
            "where f.publicacionPadreId is null and f.proyectoId in :proyectoIds order by f.fechaPublicacion desc")
    List<ForoPublicacion> listarHilosDeProyectos(@Param("proyectoIds") List<Long> proyectoIds);

    @Query("select f from ForoPublicacion f join fetch f.autor a join fetch a.usuario join fetch f.proyecto left join fetch f.topico where f.id = :id")
    Optional<ForoPublicacion> buscarHiloConDetalle(@Param("id") Long id);

    @Query("select f from ForoPublicacion f join fetch f.autor a join fetch a.usuario where f.publicacionPadreId = :padreId order by f.fechaPublicacion asc, f.id asc")
    List<ForoPublicacion> listarRespuestas(@Param("padreId") Long padreId);

    /** Respuestas de varios hilos en UNA consulta (lista de foros: conteos, participantes, texto buscable). */
    @Query("select f from ForoPublicacion f join fetch f.autor a join fetch a.usuario where f.publicacionPadreId in :padreIds order by f.fechaPublicacion asc, f.id asc")
    List<ForoPublicacion> listarRespuestasDeHilos(@Param("padreIds") Collection<Long> padreIds);

    List<ForoPublicacion> findByPublicacionPadreId(Long padreId);

    long countByPublicacionPadreId(Long padreId);

    long countByProyectoIdAndPublicacionPadreIdIsNull(Long proyectoId);

    @Query("select f from ForoPublicacion f where f.publicacionPadreId is null and f.proyectoId = :proyectoId and f.id <> :excludeId order by f.fechaPublicacion desc")
    List<ForoPublicacion> listarRelacionados(@Param("proyectoId") Long proyectoId, @Param("excludeId") Long excludeId);

    /** Hilos del mismo tópico en los proyectos visibles para el usuario (para "Hilos relacionados"). */
    @Query("select f from ForoPublicacion f join fetch f.proyecto where f.publicacionPadreId is null and f.topicoId = :topicoId " +
            "and f.proyectoId in :proyectoIds and f.id <> :excludeId order by f.fechaPublicacion desc")
    List<ForoPublicacion> listarMismoTopico(@Param("topicoId") Long topicoId, @Param("proyectoIds") Collection<Long> proyectoIds,
                                             @Param("excludeId") Long excludeId);

    /** Hilos sin tópico (anteriores a la v6): los clasifica InicializadorTopicosForo al arrancar. */
    @Query("select f from ForoPublicacion f where f.publicacionPadreId is null and f.topicoId is null")
    List<ForoPublicacion> listarHilosSinTopico();

    // Usado al eliminar un hilo: primero se borran todas sus respuestas
    // (misma tabla autorreferencial) para no violar la FK antes de borrar el hilo.
    void deleteByPublicacionPadreId(Long padreId);
}
