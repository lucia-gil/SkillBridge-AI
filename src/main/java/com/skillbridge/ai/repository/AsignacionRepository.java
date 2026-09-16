package com.skillbridge.ai.repository;

import com.skillbridge.ai.model.Asignacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AsignacionRepository extends JpaRepository<Asignacion, Long> {

    // "Mis proyectos actuales" del colaborador (con el proyecto ya cargado,
    // para no golpear la BD de nuevo por cada fila en la plantilla).
    @Query("select a from Asignacion a join fetch a.proyecto p where a.perfilId = :perfilId and a.estado = :estado order by a.fechaInicio desc")
    List<Asignacion> listarPorPerfilYEstado(@Param("perfilId") Long perfilId, @Param("estado") String estado);

    // Historial completo (todas las asignaciones, cualquier estado) de un perfil.
    @Query("select a from Asignacion a join fetch a.proyecto p where a.perfilId = :perfilId order by a.fechaInicio desc")
    List<Asignacion> listarPorPerfil(@Param("perfilId") Long perfilId);

    // Proyectos que el perfil gestiona o gestionó como PM. Se usa una sola
    // fuente para la vista de reportes y sus exportaciones.
    @Query("select distinct p from Asignacion a join a.proyecto p " +
            "where a.perfilId = :perfilId and a.rolEnProyecto = 'project_manager' " +
            "order by p.fechaInicio desc, p.nombre asc")
    List<com.skillbridge.ai.model.Proyecto> listarProyectosGestionados(@Param("perfilId") Long perfilId);

    // Equipo de un proyecto (con nombre/correo de cada persona ya cargados).
    @Query("select a from Asignacion a join fetch a.perfil p join fetch p.usuario u where a.proyectoId = :proyectoId and a.estado = :estado order by a.rolEnProyecto asc, u.nombreCompleto asc")
    List<Asignacion> listarEquipoDeProyecto(@Param("proyectoId") Long proyectoId, @Param("estado") String estado);

    // PM activo de un proyecto (fuente de verdad desde v4: no existe proyectos.creado_por_id).
    @Query("select a from Asignacion a join fetch a.perfil p join fetch p.usuario u where a.proyectoId = :proyectoId and a.rolEnProyecto = :rol and a.estado = 'activa'")
    Optional<Asignacion> buscarResponsableActivo(@Param("proyectoId") Long proyectoId, @Param("rol") String rol);

    // Puede haber mas de un PM activo si el rol se asigno a varias personas:
    // se devuelve lista y el servicio toma el primero, para no reventar con
    // NonUniqueResultException al cargar la vista de Proyectos.
    @Query("select a from Asignacion a join fetch a.perfil p join fetch p.usuario u where a.proyectoId = :proyectoId and a.rolEnProyecto = :rol and a.estado = 'activa' order by a.fechaInicio asc")
    List<Asignacion> buscarResponsablesActivos(@Param("proyectoId") Long proyectoId, @Param("rol") String rol);

    long countByProyectoIdAndEstado(Long proyectoId, String estado);

    // Cuenta cuantos PM activos tiene un proyecto - se usa para bloquear que
    // se finalice la asignacion del UNICO PM (un proyecto nunca debe quedar
    // sin nadie a cargo). Spring Data genera la consulta sola a partir del
    // nombre del metodo, no hace falta escribir JPQL para algo tan simple.
    long countByProyectoIdAndRolEnProyectoAndEstado(Long proyectoId, String rolEnProyecto, String estado);

    long countByProyectoIdAndEstadoAndRolEnProyecto(Long proyectoId, String estado, String rolEnProyecto);

    long countByPerfilIdAndEstado(Long perfilId, String estado);

    long countByEstado(String estado);

    Optional<Asignacion> findByProyectoIdAndPerfilIdAndEstado(Long proyectoId, Long perfilId, String estado);

    @Query("select coalesce(sum(a.cargaPorcentaje), 0) from Asignacion a where a.perfilId = :perfilId and a.estado = 'activa'")
    Integer sumarCargaActivaDePerfil(@Param("perfilId") Long perfilId);

    // Ocupación de TODOS los colaboradores con al menos una asignación activa,
    // en una sola consulta (evita golpear la BD una vez por colaborador en
    // Reportes globales / KPI de sobrecarga del dashboard de Administrador).
    @Query("select a.perfilId, sum(a.cargaPorcentaje) from Asignacion a where a.estado = 'activa' group by a.perfilId")
    List<Object[]> sumarCargaActivaAgrupadaPorPerfil();
}
