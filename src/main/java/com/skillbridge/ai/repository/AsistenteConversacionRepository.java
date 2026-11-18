package com.skillbridge.ai.repository;

import java.time.LocalDateTime;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import com.skillbridge.ai.model.AsistenteConversacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AsistenteConversacionRepository extends JpaRepository<AsistenteConversacion, Long> {
    List<AsistenteConversacion> findTop30ByUsuarioIdAndRolOrderByFijadaDescFechaActualizacionDesc(Long usuarioId, String rol);

    /** Marca la conversación como usada ahora (orden del historial) sin tocar el título. */
    @Transactional
    @Modifying
    @Query("update AsistenteConversacion c set c.fechaActualizacion = :ahora where c.id = :id")
    int tocar(@Param("id") Long id, @Param("ahora") LocalDateTime ahora);

    /**
     * Pone el título generado por la IA solo si la conversación sigue con el
     * título provisional (si la persona ya la renombró, no se toca).
     */
    @Transactional
    @Modifying
    @Query("update AsistenteConversacion c set c.titulo = :nuevo where c.id = :id and c.titulo = :provisional")
    int titularSiNoCambio(@Param("id") Long id, @Param("provisional") String provisional, @Param("nuevo") String nuevo);
}
