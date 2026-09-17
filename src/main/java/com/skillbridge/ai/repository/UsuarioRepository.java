package com.skillbridge.ai.repository;

import com.skillbridge.ai.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByCorreoIgnoreCase(String correo);

    boolean existsByCorreoIgnoreCase(String correo);

    List<Usuario> findAllByOrderByNombreCompletoAsc();

    long countByRolOrganizacionalIsNull();

    long countByRolOrganizacional(String rolOrganizacional);

    long countByEstado(String estado);
}
