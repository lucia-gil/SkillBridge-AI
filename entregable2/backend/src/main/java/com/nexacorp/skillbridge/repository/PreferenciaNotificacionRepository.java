package com.nexacorp.skillbridge.repository;

import com.nexacorp.skillbridge.model.PreferenciaNotificacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PreferenciaNotificacionRepository extends JpaRepository<PreferenciaNotificacion, Long> {
    List<PreferenciaNotificacion> findByPerfilId(Long perfilId);
}
