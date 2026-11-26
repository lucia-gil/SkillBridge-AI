package com.skillbridge.ai.repository;

import com.skillbridge.ai.model.AsistenteMensaje;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AsistenteMensajeRepository extends JpaRepository<AsistenteMensaje, Long> {
    List<AsistenteMensaje> findByConversacionIdOrderByIdAsc(Long conversacionId);
    List<AsistenteMensaje> findTop12ByConversacionIdOrderByIdDesc(Long conversacionId);
    long countByConversacionId(Long conversacionId);

    /** Mensajes que un usuario le envió al asistente desde una fecha (tope diario). */
    @org.springframework.data.jpa.repository.Query("select count(m) from AsistenteMensaje m, AsistenteConversacion c "
            + "where c.id = m.conversacionId and c.usuarioId = :usuarioId and m.autor = 'usuario' and m.fecha >= :desde")
    long contarDeUsuarioDesde(@org.springframework.data.repository.query.Param("usuarioId") Long usuarioId,
                              @org.springframework.data.repository.query.Param("desde") java.time.LocalDateTime desde);
}
