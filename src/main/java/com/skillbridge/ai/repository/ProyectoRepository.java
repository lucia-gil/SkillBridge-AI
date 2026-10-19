package com.skillbridge.ai.repository;

import com.skillbridge.ai.model.Proyecto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProyectoRepository extends JpaRepository<Proyecto, Long> {

    List<Proyecto> findAllByOrderByFechaCreacionDesc();

    long countByEstado(String estado);

    // Usado para bloquear la creacion de proyectos con nombre duplicado
    // (RF03: nombre no es UNIQUE a nivel de esquema, se valida en servicio).
    boolean existsByNombreIgnoreCase(String nombre);
}
