package com.skillbridge.ai.repository;

import com.skillbridge.ai.model.CategoriaHabilidad;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoriaHabilidadRepository extends JpaRepository<CategoriaHabilidad, Long> {

    boolean existsByNombreIgnoreCase(String nombre);
}
