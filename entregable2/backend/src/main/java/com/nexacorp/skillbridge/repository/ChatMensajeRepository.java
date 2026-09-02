package com.nexacorp.skillbridge.repository;

import com.nexacorp.skillbridge.model.ChatMensaje;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMensajeRepository extends JpaRepository<ChatMensaje, Long> {
    List<ChatMensaje> findBySalaIdOrderByFechaEnvioAsc(Long salaId);
}
