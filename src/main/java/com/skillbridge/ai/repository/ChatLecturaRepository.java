package com.skillbridge.ai.repository;

import com.skillbridge.ai.model.ChatLectura;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatLecturaRepository extends JpaRepository<ChatLectura, ChatLectura.Id> {
}
