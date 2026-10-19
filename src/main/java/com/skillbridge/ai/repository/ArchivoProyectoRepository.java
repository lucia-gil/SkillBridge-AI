package com.skillbridge.ai.repository;

import com.skillbridge.ai.model.ArchivoProyecto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ArchivoProyectoRepository extends JpaRepository<ArchivoProyecto, Long> {

    /** Lista archivos de un proyecto ordenados por fecha (carga la entidad completa, incluido BLOB). */
    List<ArchivoProyecto> findByProyectoIdOrderByFechaSubidaDesc(Long proyectoId);

    long countByProyectoId(Long proyectoId);
}
