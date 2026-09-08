package com.skillbridge.ai.repository;

import com.skillbridge.ai.model.PreferenciaNotificacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PreferenciaNotificacionRepository extends JpaRepository<PreferenciaNotificacion, Long> {

    Optional<PreferenciaNotificacion> findByPerfilIdAndTipoEvento(Long perfilId, String tipoEvento);

    List<PreferenciaNotificacion> findByPerfilId(Long perfilId);
}
