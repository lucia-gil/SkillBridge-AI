package com.skillbridge.ai.repository;

import com.skillbridge.ai.dto.AgenteAuditoriaFila;
import com.skillbridge.ai.model.AgenteAuditoria;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface AgenteAuditoriaRepository extends JpaRepository<AgenteAuditoria, Long> {

    @Query("select new com.skillbridge.ai.dto.AgenteAuditoriaFila(a.usuarioId, a.herramienta, a.resultado, a.fecha) " +
           "from AgenteAuditoria a order by a.id desc")
    List<AgenteAuditoriaFila> ultimas(Pageable pagina);
}
