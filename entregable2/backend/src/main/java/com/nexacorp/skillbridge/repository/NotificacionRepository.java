package com.nexacorp.skillbridge.repository;

import com.nexacorp.skillbridge.model.Notificacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {
    List<Notificacion> findByPerfilIdAndLeidaFalse(Long perfilId);
}
