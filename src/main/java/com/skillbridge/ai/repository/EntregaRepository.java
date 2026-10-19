package com.skillbridge.ai.repository;

import com.skillbridge.ai.model.Entrega;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EntregaRepository extends JpaRepository<Entrega, Long> {

    Optional<Entrega> findByEntregableIdAndPerfilId(Long entregableId, Long perfilId);

    // Todas las entregas de un entregable, con el perfil/usuario ya cargados
    // (para la tabla de revisión del PM: nombre + iniciales sin N+1 queries).
    @Query("select en from Entrega en join fetch en.perfil p join fetch p.usuario u " +
            "where en.entregableId = :entregableId order by en.fechaEntrega asc")
    List<Entrega> listarPorEntregable(@Param("entregableId") Long entregableId);

    long countByEntregableId(Long entregableId);

    long countByEntregableIdAndEstado(Long entregableId, String estado);

    void deleteByEntregableIdAndPerfilId(Long entregableId, Long perfilId);
}
