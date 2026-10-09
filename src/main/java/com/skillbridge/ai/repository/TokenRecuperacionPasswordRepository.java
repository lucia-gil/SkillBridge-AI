package com.skillbridge.ai.repository;

import com.skillbridge.ai.model.TokenRecuperacionPassword;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TokenRecuperacionPasswordRepository extends JpaRepository<TokenRecuperacionPassword, Long> {

    Optional<TokenRecuperacionPassword> findByToken(String token);

    // Al pedir un enlace nuevo se invalidan los anteriores del mismo usuario,
    // asi un correo viejo que alguien encuentre despues no sirve para nada.
    void deleteByUsuarioId(Long usuarioId);
}
