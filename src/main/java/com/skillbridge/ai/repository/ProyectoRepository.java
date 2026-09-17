package com.skillbridge.ai.repository;

import com.skillbridge.ai.model.Proyecto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProyectoRepository extends JpaRepository<Proyecto, Long> {

    List<Proyecto> findAllByOrderByFechaCreacionDesc();

    long countByEstado(String estado);
}
