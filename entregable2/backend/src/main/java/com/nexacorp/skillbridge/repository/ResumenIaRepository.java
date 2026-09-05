package com.nexacorp.skillbridge.repository;

import com.nexacorp.skillbridge.model.ResumenIa;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ResumenIaRepository extends JpaRepository<ResumenIa, Long> {
    Optional<ResumenIa> findByPublicacionId(Long publicacionId);
}
