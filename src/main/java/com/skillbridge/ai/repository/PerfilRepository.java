package com.skillbridge.ai.repository;

import com.skillbridge.ai.model.Perfil;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PerfilRepository extends JpaRepository<Perfil, Long> {

    Optional<Perfil> findByUsuarioId(Long usuarioId);

    // Perfiles activos con su Usuario ya cargado - usado para el selector
    // "Asignar colaborador" de Proyectos y para el reporte de ocupación.
    @Query("select p from Perfil p join fetch p.usuario u where p.estado = 'activo' order by u.nombreCompleto asc")
    List<Perfil> listarActivosConUsuario();

    // Un perfil con su Usuario ya cargado - usado por "Mi cuenta" (necesita
    // nombre_completo/correo de usuarios, y open-in-view=false impide tocar
    // el proxy lazy fuera de la transacción del service).
    @Query("select p from Perfil p join fetch p.usuario u where p.id = :id")
    Optional<Perfil> buscarConUsuario(@Param("id") Long id);

    long countByEstado(String estado);
}
