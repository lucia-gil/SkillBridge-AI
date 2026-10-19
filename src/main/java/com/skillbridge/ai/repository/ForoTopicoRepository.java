package com.skillbridge.ai.repository;

import com.skillbridge.ai.model.ForoTopico;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ForoTopicoRepository extends JpaRepository<ForoTopico, Long> {

    List<ForoTopico> findAllByOrderByOrdenAscNombreAsc();

    Optional<ForoTopico> findByCodigo(String codigo);
}
