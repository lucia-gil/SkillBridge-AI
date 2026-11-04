package com.skillbridge.ai.repository;

import com.skillbridge.ai.model.RecomendacionIaLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecomendacionIaLogRepository extends JpaRepository<RecomendacionIaLog, Long> {

    /** Marca como acertadas las recomendaciones de esa persona para ese proyecto. */
    @Modifying
    @Query("update RecomendacionIaLog r set r.fueAsignado = true where r.proyectoId = :proyectoId and r.perfilRecomendadoId = :perfilId and r.fueAsignado = false")
    int marcarAsignado(@Param("proyectoId") Long proyectoId, @Param("perfilId") Long perfilId);

    /** Personas distintas recomendadas por proyecto (para medir el acierto sin contar repeticiones). */
    @Query("select count(distinct concat(r.proyectoId, '-', r.perfilRecomendadoId)) from RecomendacionIaLog r")
    long contarRecomendacionesUnicas();

    @Query("select count(distinct concat(r.proyectoId, '-', r.perfilRecomendadoId)) from RecomendacionIaLog r where r.fueAsignado = true")
    long contarAciertos();
}
