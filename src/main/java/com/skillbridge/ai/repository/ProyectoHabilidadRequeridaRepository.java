package com.skillbridge.ai.repository;

import com.skillbridge.ai.model.ProyectoHabilidadRequerida;
import com.skillbridge.ai.model.ProyectoHabilidadRequeridaId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProyectoHabilidadRequeridaRepository
        extends JpaRepository<ProyectoHabilidadRequerida, ProyectoHabilidadRequeridaId> {

    long countById_HabilidadId(Long habilidadId);
}
