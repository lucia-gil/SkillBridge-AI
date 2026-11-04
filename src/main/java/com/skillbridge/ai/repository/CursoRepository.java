package com.skillbridge.ai.repository;

import com.skillbridge.ai.model.Curso;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CursoRepository extends JpaRepository<Curso, Long> {
    @Query(value = "select c from Curso c left join fetch c.categoria left join fetch c.habilidad " +
            "where (:q = '' or lower(c.titulo) like lower(concat('%', :q, '%'))) " +
            "and (:estado = '' or c.estado = :estado)",
            countQuery = "select count(c) from Curso c where (:q = '' or lower(c.titulo) like lower(concat('%', :q, '%'))) and (:estado = '' or c.estado = :estado)")
    Page<Curso> buscar(@Param("q") String q, @Param("estado") String estado, Pageable pageable);
    long countByEstado(String estado);

    /** Catálogo completo (vista del Administrador), con categoría y habilidad en la misma consulta. */
    @Query("select c from Curso c left join fetch c.categoria left join fetch c.habilidad order by c.destacado desc, c.fechaCreacion desc, c.id desc")
    java.util.List<Curso> listarConRelaciones();

    /** Solo lo publicado (catálogo de Colaborador / PM / RM). */
    @Query("select c from Curso c left join fetch c.categoria left join fetch c.habilidad where c.estado = 'PUBLICADO' order by c.destacado desc, c.fechaCreacion desc, c.id desc")
    java.util.List<Curso> listarPublicados();
}
