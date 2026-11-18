package com.skillbridge.ai.repository;

import com.skillbridge.ai.model.EventoPersonal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EventoPersonalRepository extends JpaRepository<EventoPersonal, Long> {

    /** La agenda personal de un usuario, con el tipo ya cargado, en orden cronológico. */
    @Query("select e from EventoPersonal e join fetch e.tipo where e.usuarioId = :usuarioId order by e.fechaInicio asc")
    List<EventoPersonal> listarDe(@Param("usuarioId") Long usuarioId);

    /** Un evento solo si es de ese usuario (para editar o eliminar). */
    Optional<EventoPersonal> findByIdAndUsuarioId(Long id, Long usuarioId);

    /** Para no dejar eliminar un tipo de evento que ya se usa (catálogo del Administrador). */
    long countByTipoId(Long tipoId);
}
