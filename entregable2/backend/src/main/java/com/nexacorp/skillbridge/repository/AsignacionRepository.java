package com.nexacorp.skillbridge.repository;

import com.nexacorp.skillbridge.model.Asignacion;
import com.nexacorp.skillbridge.model.EstadoAsignacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface AsignacionRepository extends JpaRepository<Asignacion, Long> {

    List<Asignacion> findByPerfilIdAndEstado(Long perfilId, EstadoAsignacion estado);

    List<Asignacion> findByProyectoIdAndEstado(Long proyectoId, EstadoAsignacion estado);

    Optional<Asignacion> findByProyectoIdAndPerfilIdAndEstado(Long proyectoId, Long perfilId, EstadoAsignacion estado);

    /** Carga total (%) de un colaborador: suma de sus asignaciones vigentes. No se guarda en columna aparte (ver schema.sql). */
    @Query("SELECT COALESCE(SUM(a.cargaPorcentaje), 0) FROM Asignacion a WHERE a.perfil.id = :perfilId AND a.estado = 'activa'")
    int calcularCargaActualDelPerfil(Long perfilId);
}
