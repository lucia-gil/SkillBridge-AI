package com.skillbridge.ai.repository;

import com.skillbridge.ai.model.ForoDestacado;
import com.skillbridge.ai.model.ForoDestacadoId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ForoDestacadoRepository extends JpaRepository<ForoDestacado, ForoDestacadoId> {

    /** Filas [publicacionId, cantidad] para pintar el contador de varias publicaciones con una sola consulta. */
    @Query("select d.publicacionId, count(d) from ForoDestacado d where d.publicacionId in :publicacionIds group by d.publicacionId")
    List<Object[]> contarPorPublicacion(@Param("publicacionIds") Collection<Long> publicacionIds);

    /** Cuáles de esas publicaciones ya destacó este perfil (para pintar la estrella activa). */
    @Query("select d.publicacionId from ForoDestacado d where d.perfilId = :perfilId and d.publicacionId in :publicacionIds")
    List<Long> destacadasPorPerfil(@Param("perfilId") Long perfilId,
                                    @Param("publicacionIds") Collection<Long> publicacionIds);
}
