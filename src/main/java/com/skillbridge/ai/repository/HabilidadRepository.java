package com.skillbridge.ai.repository;

import com.skillbridge.ai.model.Habilidad;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

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

    @Query("select h from Habilidad h join fetch h.categoria where h.id = :id")
    Optional<Habilidad> findByIdConCategoria(Long id);

    // "Habilidades más declaradas" en UNA sola consulta (antes: una consulta
    // count por cada habilidad del catálogo, problema N+1). El left join
    // conserva las habilidades que nadie declaró (conteo 0). Desempate por
    // nombre para que el ranking sea estable entre recargas.
    @Query("select new com.skillbridge.ai.dto.VacanteHabilidad(h.nombre, count(ph)) " +
           "from Habilidad h left join PerfilHabilidad ph on ph.id.habilidadId = h.id " +
           "group by h.id, h.nombre order by count(ph) desc, h.nombre asc")
    List<com.skillbridge.ai.dto.VacanteHabilidad> rankingDeclaradas(org.springframework.data.domain.Pageable pagina);

    // Para el KPI "habilidades sin proyecto asociado" de Reportes globales.
    @Query("select count(h) from Habilidad h where h.id not in (select phr.id.habilidadId from ProyectoHabilidadRequerida phr)")
    long contarSinProyectoAsociado();
}
