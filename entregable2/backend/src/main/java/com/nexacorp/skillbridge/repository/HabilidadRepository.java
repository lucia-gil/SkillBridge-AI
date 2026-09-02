package com.nexacorp.skillbridge.repository;

import com.nexacorp.skillbridge.model.Habilidad;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HabilidadRepository extends JpaRepository<Habilidad, Long> {
    List<Habilidad> findByCategoria(String categoria);
}
