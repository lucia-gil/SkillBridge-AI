package com.skillbridge.ai.repository;

import org.springframework.data.jpa.repository.Modifying;
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

    // Modelo v7: Miembros (sin rol organizacional) con cuenta y perfil activos.
    @Query("select count(p) from Perfil p join p.usuario u where p.estado = 'activo' and u.estado = 'activo' and u.rolOrganizacional is null")
    long contarMiembrosActivos();

    @Query("select p from Perfil p join fetch p.usuario u where p.estado = 'activo' and u.rolOrganizacional = :rol order by u.nombreCompleto")
    List<Perfil> listarActivosPorRolOrganizacional(@Param("rol") String rol);

    // Igual que arriba, pero SIN filtrar por estado - usado en
    // resource-manager/colaboradores.html, que necesita ver también a los
    // perfiles inactivos (para poder reactivarlos). Sin el join fetch, cada
    // p.getUsuario() dispara un LazyInitializationException apenas se sale
    // de la transacción (spring.jpa.open-in-view=false).
    @Query("select p from Perfil p join fetch p.usuario u order by u.nombreCompleto asc")
    List<Perfil> listarTodosConUsuario();

    /**
     * Personas a las que se puede escribir un mensaje directo: cuentas activas,
     * sin contar a quien busca; con soloResourceManagers, solo RMs. Busca por
     * nombre, correo o cargo.
     */
    @Query("select p from Perfil p join fetch p.usuario u where u.estado = 'activo' and p.id <> :yo " +
           "and (:soloResourceManagers = false or u.rolOrganizacional = 'resource_manager') " +
           "and (lower(u.nombreCompleto) like :q or lower(u.correo) like :q or lower(coalesce(p.cargo, '')) like :q) " +
           "order by u.nombreCompleto asc")
    List<Perfil> contactosParaMensaje(@Param("yo") Long yo, @Param("soloResourceManagers") boolean soloResourceManagers,
                                      @Param("q") String q, org.springframework.data.domain.Pageable pagina);

    // Un perfil con su Usuario ya cargado - usado por "Mi cuenta" (necesita
    // nombre_completo/correo de usuarios, y open-in-view=false impide tocar
    // el proxy lazy fuera de la transacción del service).
    @Query("select p from Perfil p join fetch p.usuario u where p.id = :id")
    Optional<Perfil> buscarConUsuario(@Param("id") Long id);

    long countByEstado(String estado);

    // ── Agente IA (service/agente) ──

    boolean existsByIdAndUsuarioId(Long id, Long usuarioId);

    /** Biografía propia: solo cambia si el perfil es de ese usuario (devuelve 1 si se actualizó). */
    @Modifying
    @Query("update Perfil p set p.biografia = :biografia where p.id = :perfilId and p.usuarioId = :usuarioId")
    int actualizarBiografia(@Param("perfilId") Long perfilId, @Param("usuarioId") Long usuarioId,
                            @Param("biografia") String biografia);
}
