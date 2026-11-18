package com.skillbridge.ai.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    // Modelo v7 (fase 3): nunca puede quedar el sistema sin un Administrador
    // ni sin un Resource Manager ACTIVOS.
    long countByRolOrganizacionalAndEstado(String rolOrganizacional, String estado);

    long countByEstado(String estado);

    /** Cuentas creadas desde una fecha (resumen semanal del Administrador). */
    long countByFechaCreacionAfter(java.time.LocalDateTime desde);

    /**
     * Permite saber si un usuario tiene foto sin cargar el contenido del BLOB.
     */
    boolean existsByIdAndFotoPerfilIsNotNull(Long id);

    // ── Agente IA (service/agente) ──

    @Query("select new com.skillbridge.ai.dto.AgenteCuentaFila(u.id, u.nombreCompleto, u.rolOrganizacional, u.estado) " +
           "from Usuario u order by u.id")
    List<com.skillbridge.ai.dto.AgenteCuentaFila> cuentasParaAgente(Pageable pagina);
}
