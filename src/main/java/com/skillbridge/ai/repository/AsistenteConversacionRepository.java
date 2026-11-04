package com.skillbridge.ai.repository;

import com.skillbridge.ai.model.AsistenteConversacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AsistenteConversacionRepository extends JpaRepository<AsistenteConversacion, Long> {
    List<AsistenteConversacion> findTop30ByUsuarioIdAndRolOrderByFijadaDescFechaActualizacionDesc(Long usuarioId, String rol);
}
