package com.skillbridge.ai.repository;

import com.skillbridge.ai.model.ChatParticipante;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChatParticipanteRepository extends JpaRepository<ChatParticipante, ChatParticipante.Id> {

    /** Perfiles (ids) de una sala directa. */
    @Query("select p.id.perfilId from ChatParticipante p where p.id.salaId = :salaId")
    List<Long> perfilesDe(@Param("salaId") Long salaId);

    /** Ids de las salas directas en las que participa un perfil. */
    @Query("select p.id.salaId from ChatParticipante p where p.id.perfilId = :perfilId")
    List<Long> salasDe(@Param("perfilId") Long perfilId);
}
