package com.nexacorp.skillbridge.repository;

import com.nexacorp.skillbridge.model.ChatSala;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatSalaRepository extends JpaRepository<ChatSala, Long> {
    List<ChatSala> findByProyectoId(Long proyectoId);
}
