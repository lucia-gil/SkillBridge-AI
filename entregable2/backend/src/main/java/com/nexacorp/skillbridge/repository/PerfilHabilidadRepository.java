package com.nexacorp.skillbridge.repository;

import com.nexacorp.skillbridge.model.PerfilHabilidad;
import com.nexacorp.skillbridge.model.id.PerfilHabilidadId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PerfilHabilidadRepository extends JpaRepository<PerfilHabilidad, PerfilHabilidadId> {
    List<PerfilHabilidad> findByPerfilId(Long perfilId);

    // Insumo directo del "dashboard de cobertura de habilidades": no hace
    // falta tabla nueva, es una consulta que agrupa y cuenta (ver informe).
    @org.springframework.data.jpa.repository.Query("""
            SELECT ph.habilidad.nombre AS habilidad, COUNT(ph) AS totalColaboradores
            FROM PerfilHabilidad ph
            GROUP BY ph.habilidad.nombre
            ORDER BY totalColaboradores DESC
            """)
    List<CoberturaHabilidad> obtenerCoberturaPorHabilidad();

    interface CoberturaHabilidad {
        String getHabilidad();
        Long getTotalColaboradores();
    }
}
