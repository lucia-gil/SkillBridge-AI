package com.skillbridge.ai.repository;

import com.skillbridge.ai.dto.ForoAdjuntoInfo;
import com.skillbridge.ai.model.ForoAdjunto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ForoAdjuntoRepository extends JpaRepository<ForoAdjunto, Long> {

    // Solo metadatos: un "select new" no toca la columna LONGBLOB `contenido`.
    @Query("select new com.skillbridge.ai.dto.ForoAdjuntoInfo(a.id, a.publicacionId, a.tipo, a.nombre, a.mimeTipo, a.tamanoBytes, a.url) " +
            "from ForoAdjunto a where a.publicacionId in :publicacionIds order by a.id asc")
    List<ForoAdjuntoInfo> listarInfo(@Param("publicacionIds") Collection<Long> publicacionIds);

    long countByPublicacionId(Long publicacionId);
}
