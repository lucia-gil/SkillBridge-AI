package com.nexacorp.skillbridge.repository;

import com.nexacorp.skillbridge.model.EstadoProyecto;
import com.nexacorp.skillbridge.model.Proyecto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProyectoRepository extends JpaRepository<Proyecto, Long> {
    List<Proyecto> findByEstado(EstadoProyecto estado);
}
