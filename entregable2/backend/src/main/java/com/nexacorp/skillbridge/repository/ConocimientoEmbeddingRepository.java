package com.nexacorp.skillbridge.repository;

import com.nexacorp.skillbridge.model.ConocimientoEmbedding;
import com.nexacorp.skillbridge.model.TipoOrigenEmbedding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConocimientoEmbeddingRepository extends JpaRepository<ConocimientoEmbedding, Long> {
    List<ConocimientoEmbedding> findByTipoOrigen(TipoOrigenEmbedding tipoOrigen);
}
