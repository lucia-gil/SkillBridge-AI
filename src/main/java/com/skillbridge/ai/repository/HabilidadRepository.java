package com.skillbridge.ai.repository;

import com.skillbridge.ai.dto.VacanteHabilidad;
import com.skillbridge.ai.model.Habilidad;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface HabilidadRepository extends JpaRepository<Habilidad, Long> {

    boolean existsByNombreIgnoreCase(String nombre);

    long countByCategoriaId(Long categoriaId);

    // join fetch: evita LazyInitializationException al leer h.getCategoria()
    // desde la plantilla Thymeleaf (spring.jpa.open-in-view=false cierra la
    // sesion de Hibernate antes de renderizar la vista).
    @Query("select h from Habilidad h join fetch h.categoria order by h.nombre asc")
    List<Habilidad> findAllConCategoriaOrderByNombre();

    @Query(value = "select h from Habilidad h join fetch h.categoria c " +
            "where (:texto = '' or lower(h.nombre) like lower(concat('%', :texto, '%'))) " +
            "and (:categoriaId is null or c.id = :categoriaId)",
            countQuery = "select count(h) from Habilidad h join h.categoria c " +
                    "where (:texto = '' or lower(h.nombre) like lower(concat('%', :texto, '%'))) " +
                    "and (:categoriaId is null or c.id = :categoriaId)")
    Page<Habilidad> buscarCatalogo(@Param("texto") String texto, @Param("categoriaId") Long categoriaId, Pageable pageable);

    @Query("select h from Habilidad h join fetch h.categoria where h.id = :id")
    Optional<Habilidad> findByIdConCategoria(Long id);

    // Para el KPI "habilidades sin proyecto asociado" de Reportes globales.
    @Query("select count(h) from Habilidad h where h.id not in (select phr.id.habilidadId from ProyectoHabilidadRequerida phr)")
    long contarSinProyectoAsociado();

    // Ranking de habilidades mas declaradas por los colaboradores (perfil_habilidad),
    // usado en Reportes globales del Administrador (ver ReporteService.topHabilidadesDeclaradas).
    @Query("select new com.skillbridge.ai.dto.VacanteHabilidad(h.nombre, count(ph)) " +
            "from Habilidad h left join PerfilHabilidad ph on ph.id.habilidadId = h.id " +
            "group by h.id, h.nombre order by count(ph) desc")
    List<VacanteHabilidad> rankingDeclaradas(Pageable pageable);
}
