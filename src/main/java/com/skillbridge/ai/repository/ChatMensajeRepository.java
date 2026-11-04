package com.skillbridge.ai.repository;

import com.skillbridge.ai.model.ChatMensaje;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChatMensajeRepository extends JpaRepository<ChatMensaje, Long> {

    /** Mensajes nuevos (id mayor que el último que tiene el navegador), del más viejo al más nuevo. */
    @Query("select m from ChatMensaje m join fetch m.autor a join fetch a.usuario where m.salaId = :salaId and m.id > :despues order by m.id asc")
    List<ChatMensaje> posteriores(@Param("salaId") Long salaId, @Param("despues") Long despues, Pageable limite);

    /** Mensajes anteriores a un id, del más nuevo al más viejo (el servicio los invierte). */
    @Query("select m from ChatMensaje m join fetch m.autor a join fetch a.usuario where m.salaId = :salaId and m.id < :antes order by m.id desc")
    List<ChatMensaje> anteriores(@Param("salaId") Long salaId, @Param("antes") Long antes, Pageable limite);

    /** No leídos para una persona: mensajes de otros después del último que leyó. */
    long countBySalaIdAndIdGreaterThanAndAutorIdNot(Long salaId, Long ultimoLeido, Long autorId);
}
