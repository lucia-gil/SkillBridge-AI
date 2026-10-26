package com.skillbridge.ai.repository;

import com.skillbridge.ai.model.CertificadoHabilidad;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CertificadoHabilidadRepository extends JpaRepository<CertificadoHabilidad, Long> {

    List<CertificadoHabilidad> findByPerfilIdAndHabilidadId(Long perfilId, Long habilidadId);

    // Se usa al editar/reemplazar la constancia de una habilidad (Opción A:
    // como máximo un certificado por habilidad, se borra el anterior antes
    // de guardar el nuevo, en vez de acumular varios).
    void deleteByPerfilIdAndHabilidadId(Long perfilId, Long habilidadId);

    // Solo id y fecha de subida de las constancias de un perfil (sin traer el
    // contenido del archivo): la vista de perfil profesional muestra la fecha.
    @org.springframework.data.jpa.repository.Query(
            "select c.id, c.fechaSubida from CertificadoHabilidad c where c.perfilId = :perfilId")
    List<Object[]> fechasDePerfil(@org.springframework.data.repository.query.Param("perfilId") Long perfilId);
}
