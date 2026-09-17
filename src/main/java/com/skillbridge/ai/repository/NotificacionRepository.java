package com.skillbridge.ai.repository;

import com.skillbridge.ai.model.Notificacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {

    @Query("select n from Notificacion n join fetch n.tipo where n.perfilId = :perfilId order by n.fechaCreacion desc")
    List<Notificacion> listarPorPerfil(@Param("perfilId") Long perfilId);

    long countByPerfilIdAndLeidaFalse(Long perfilId);

    // Se usa desde el catalogo de tipos_notificacion (Admin) para bloquear
    // que se elimine un tipo que ya tiene notificaciones reales apuntando
    // a el.
    long countByTipoId(Long tipoId);

    @Modifying
    @Query("update Notificacion n set n.leida = true where n.perfilId = :perfilId")
    void marcarTodasLeidas(@Param("perfilId") Long perfilId);
}