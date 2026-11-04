package com.skillbridge.ai.repository;

import com.skillbridge.ai.model.ChatSala;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChatSalaRepository extends JpaRepository<ChatSala, Long> {
    Optional<ChatSala> findFirstByProyectoIdOrderByIdAsc(Long proyectoId);
}
