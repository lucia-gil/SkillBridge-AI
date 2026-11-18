package com.skillbridge.ai.repository;

import com.skillbridge.ai.model.AgenteMutex;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface AgenteMutexRepository extends JpaRepository<AgenteMutex, Integer> {

    /** SELECT ... FOR UPDATE sobre la fila única: serializa las escrituras del agente. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from AgenteMutex m where m.id = 1")
    Optional<AgenteMutex> bloquear();
}
