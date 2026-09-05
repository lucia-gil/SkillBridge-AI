package com.nexacorp.skillbridge.repository;

import com.nexacorp.skillbridge.model.ForoPublicacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ForoPublicacionRepository extends JpaRepository<ForoPublicacion, Long> {
    List<ForoPublicacion> findByProyectoIdAndPublicacionPadreIsNull(Long proyectoId);
    List<ForoPublicacion> findByPublicacionPadreIdOrderByFechaPublicacionAsc(Long publicacionPadreId);
}
