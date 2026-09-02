package com.nexacorp.skillbridge.service;

import com.nexacorp.skillbridge.model.Asignacion;
import com.nexacorp.skillbridge.model.EstadoAsignacion;
import com.nexacorp.skillbridge.model.Perfil;
import com.nexacorp.skillbridge.model.Proyecto;
import com.nexacorp.skillbridge.model.RolEnProyecto;
import com.nexacorp.skillbridge.repository.AsignacionRepository;
import com.nexacorp.skillbridge.repository.ProyectoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resuelve el "problema del huevo y la gallina" de proyectos.creado_por_id
 * señalado explícitamente en el documento de diseño: crear un Proyecto por
 * sí solo deja a su creador registrado como "creador" pero sin una fila
 * formal en `asignaciones` como project_manager. Este servicio hace las dos
 * inserciones (proyecto + asignación PM) en una sola transacción, tal como
 * se recomienda ahí.
 */
@Service
public class ProyectoService {

    private final ProyectoRepository proyectoRepository;
    private final AsignacionRepository asignacionRepository;

    public ProyectoService(ProyectoRepository proyectoRepository, AsignacionRepository asignacionRepository) {
        this.proyectoRepository = proyectoRepository;
        this.asignacionRepository = asignacionRepository;
    }

    @Transactional
    public Proyecto crearProyecto(Proyecto proyecto, Perfil pm, int cargaPorcentajePm) {
        proyecto.setCreadoPor(pm);
        Proyecto guardado = proyectoRepository.save(proyecto);

        Asignacion asignacionPm = new Asignacion();
        asignacionPm.setProyecto(guardado);
        asignacionPm.setPerfil(pm);
        asignacionPm.setRolEnProyecto(RolEnProyecto.project_manager);
        asignacionPm.setCargaPorcentaje(cargaPorcentajePm);
        asignacionPm.setEstado(EstadoAsignacion.activa);
        asignacionPm.setFechaInicio(guardado.getFechaInicio());
        asignacionRepository.save(asignacionPm);

        return guardado;
    }
}
