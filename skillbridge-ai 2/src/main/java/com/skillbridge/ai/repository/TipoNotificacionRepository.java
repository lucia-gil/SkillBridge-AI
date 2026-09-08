package com.skillbridge.ai.repository;

import com.skillbridge.ai.model.TipoNotificacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TipoNotificacionRepository extends JpaRepository<TipoNotificacion, Long> {

    Optional<TipoNotificacion> findByCodigo(String codigo);

    boolean existsByCodigo(String codigo);
}
