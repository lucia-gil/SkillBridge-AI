package com.skillbridge.ai.repository;

import com.skillbridge.ai.model.Notificacion;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {

    // Los avisos internos de administración usan rutas absolutas de ese panel.
    // Si cambia el rol del destinatario, esos avisos quedan fuera de su bandeja.
    String BANDEJA = """
            n.perfilId = :perfilId
            and (:admin = true or coalesce(n.enlaceAccion, '') not like '/administrador/%')
            and coalesce(n.enlaceAccion, '') not like '/administrador/seguridad.html?id=%'
            """;

    @Query("select n from Notificacion n join fetch n.tipo where " + BANDEJA + " order by n.fechaCreacion desc, n.id desc")
    List<Notificacion> listarPorPerfil(@Param("perfilId") Long perfilId, @Param("admin") boolean admin, Pageable pagina);

    @Query("select count(n) from Notificacion n where " + BANDEJA + " and n.leida = false")
    long contarNoLeidas(@Param("perfilId") Long perfilId, @Param("admin") boolean admin);

    long countByTipoId(Long tipoId);

    @Modifying
    @Query("update Notificacion n set n.leida = true where " + BANDEJA + " and n.id = :id and n.leida = false")
    int marcarLeida(@Param("perfilId") Long perfilId, @Param("admin") boolean admin, @Param("id") Long id);

    @Modifying
    @Query("update Notificacion n set n.leida = true where " + BANDEJA + " and n.leida = false")
    int marcarTodasLeidas(@Param("perfilId") Long perfilId, @Param("admin") boolean admin);
}
