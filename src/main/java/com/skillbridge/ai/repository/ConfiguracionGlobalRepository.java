package com.skillbridge.ai.repository;

import com.skillbridge.ai.model.ConfiguracionGlobal;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConfiguracionGlobalRepository extends JpaRepository<ConfiguracionGlobal, String> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select c from ConfiguracionGlobal c order by c.clave")
    java.util.List<ConfiguracionGlobal> bloquearFormulario();
}
