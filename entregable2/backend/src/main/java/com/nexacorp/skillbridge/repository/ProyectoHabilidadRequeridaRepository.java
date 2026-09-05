package com.nexacorp.skillbridge.repository;

import com.nexacorp.skillbridge.model.ProyectoHabilidadRequerida;
import com.nexacorp.skillbridge.model.id.ProyectoHabilidadRequeridaId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProyectoHabilidadRequeridaRepository extends JpaRepository<ProyectoHabilidadRequerida, ProyectoHabilidadRequeridaId> {
    List<ProyectoHabilidadRequerida> findByIdProyectoId(Long proyectoId);
}
