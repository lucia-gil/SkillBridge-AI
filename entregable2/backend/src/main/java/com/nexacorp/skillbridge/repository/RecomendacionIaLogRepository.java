package com.nexacorp.skillbridge.repository;

import com.nexacorp.skillbridge.model.RecomendacionIaLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RecomendacionIaLogRepository extends JpaRepository<RecomendacionIaLog, Long> {
    List<RecomendacionIaLog> findByProyectoIdOrderByPuntajeCompatibilidadDesc(Long proyectoId);
}
