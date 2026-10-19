package com.skillbridge.ai.repository;

import com.skillbridge.ai.model.PerfilHabilidad;
import com.skillbridge.ai.model.PerfilHabilidadId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PerfilHabilidadRepository extends JpaRepository<PerfilHabilidad, PerfilHabilidadId> {

    long countById_HabilidadId(Long habilidadId);

    List<PerfilHabilidad> findById_PerfilId(Long perfilId);

    long countById_PerfilId(Long perfilId);

    long countById_PerfilIdAndValidadoPorIdIsNotNull(Long perfilId);

    @Query("select avg(p.nivel) from PerfilHabilidad p where p.id.habilidadId = :habilidadId")
    Double promedioNivel(Long habilidadId);
}
