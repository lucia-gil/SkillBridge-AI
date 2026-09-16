package com.skillbridge.ai.repository;

import com.skillbridge.ai.model.CorreoAutorizado;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CorreoAutorizadoRepository extends JpaRepository<CorreoAutorizado, Long> {

    Optional<CorreoAutorizado> findByCorreoIgnoreCase(String correo);

    boolean existsByCorreoIgnoreCase(String correo);
}
