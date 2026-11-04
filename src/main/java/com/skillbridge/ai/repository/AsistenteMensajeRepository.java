package com.skillbridge.ai.repository;

import com.skillbridge.ai.model.AsistenteMensaje;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AsistenteMensajeRepository extends JpaRepository<AsistenteMensaje, Long> {
    List<AsistenteMensaje> findByConversacionIdOrderByIdAsc(Long conversacionId);
    List<AsistenteMensaje> findTop12ByConversacionIdOrderByIdDesc(Long conversacionId);
    long countByConversacionId(Long conversacionId);
}
