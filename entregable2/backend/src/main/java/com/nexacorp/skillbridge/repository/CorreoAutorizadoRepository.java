package com.nexacorp.skillbridge.repository;

import com.nexacorp.skillbridge.model.CorreoAutorizado;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CorreoAutorizadoRepository extends JpaRepository<CorreoAutorizado, Long> {
    Optional<CorreoAutorizado> findByCorreoAndUtilizadoFalse(String correo);
}
