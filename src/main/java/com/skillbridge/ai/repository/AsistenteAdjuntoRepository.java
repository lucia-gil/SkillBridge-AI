package com.skillbridge.ai.repository;

import com.skillbridge.ai.model.AsistenteAdjunto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface AsistenteAdjuntoRepository extends JpaRepository<AsistenteAdjunto, Long> {

    /** Datos livianos (sin el contenido) para pintar los adjuntos de una conversación. */
    interface Info {
        Long getId();
        Long getMensajeId();
        String getNombre();
        String getTipo();
        int getTamano();
    }

    @Query("select a.id as id, a.mensajeId as mensajeId, a.nombre as nombre, a.tipo as tipo, a.tamano as tamano "
            + "from AsistenteAdjunto a where a.mensajeId in :ids order by a.id")
    List<Info> infoDeMensajes(@Param("ids") Collection<Long> ids);

    /** Usuario dueño del adjunto (para no servir adjuntos ajenos). */
    @Query("select c.usuarioId from AsistenteAdjunto a, AsistenteMensaje m, AsistenteConversacion c "
            + "where a.id = :id and m.id = a.mensajeId and c.id = m.conversacionId")
    Long duenoDe(@Param("id") Long id);
}
