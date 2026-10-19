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

    // Para el KPI "habilidades sin proyecto asociado" de Reportes globales.
    @Query("select count(h) from Habilidad h where h.id not in (select phr.id.habilidadId from ProyectoHabilidadRequerida phr)")
    long contarSinProyectoAsociado();
}
