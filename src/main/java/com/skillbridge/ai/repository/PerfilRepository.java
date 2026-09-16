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

    @Query("select p from Perfil p join fetch p.usuario u where p.estado = 'activo' and u.rolOrganizacional = :rol order by u.nombreCompleto")
    List<Perfil> listarActivosPorRolOrganizacional(@Param("rol") String rol);

    // Igual que arriba, pero SIN filtrar por estado - usado en
    // resource-manager/colaboradores.html, que necesita ver también a los
    // perfiles inactivos (para poder reactivarlos). Sin el join fetch, cada
    // p.getUsuario() dispara un LazyInitializationException apenas se sale
    // de la transacción (spring.jpa.open-in-view=false).
    @Query("select p from Perfil p join fetch p.usuario u order by u.nombreCompleto asc")
    List<Perfil> listarTodosConUsuario();

    // Un perfil con su Usuario ya cargado - usado por "Mi cuenta" (necesita
    // nombre_completo/correo de usuarios, y open-in-view=false impide tocar
    // el proxy lazy fuera de la transacción del service).
    @Query("select p from Perfil p join fetch p.usuario u where p.id = :id")
    Optional<Perfil> buscarConUsuario(@Param("id") Long id);

    long countByEstado(String estado);
}
