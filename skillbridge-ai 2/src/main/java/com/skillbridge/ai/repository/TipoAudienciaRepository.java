package com.skillbridge.ai.repository;

import com.skillbridge.ai.model.TipoAudiencia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TipoAudienciaRepository extends JpaRepository<TipoAudiencia, Long> {
    Optional<TipoAudiencia> findByCodigo(String codigo);
}
