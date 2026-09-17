package com.skillbridge.ai.service;

import com.skillbridge.ai.dto.EstadoProyectoConConteo;
import com.skillbridge.ai.dto.OcupacionColaboradorFila;
import com.skillbridge.ai.dto.VacanteHabilidad;
import com.skillbridge.ai.model.Perfil;
import com.skillbridge.ai.repository.AsignacionRepository;
import com.skillbridge.ai.repository.HabilidadRepository;
import com.skillbridge.ai.repository.PerfilHabilidadRepository;
import com.skillbridge.ai.repository.PerfilRepository;
import com.skillbridge.ai.repository.ProyectoRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Agregaciones para administrador/inicio.html y administrador/reportes.html.
 *
 * "Vacantes por habilidad" del mockup original no tiene una fuente real en
 * el esquema (proyecto_habilidad_requerida se queda vacía en esta entrega:
 * no se construyó una pantalla para cargarla, ver README) - se reemplaza
 * por "Habilidades más declaradas" (conteo real de perfil_habilidad), que sí
 * se llena a medida que la gente se registra o agrega habilidades a su
 * perfil.
 */
@Service
public class ReporteService {

    private final ProyectoRepository proyectoRepository;
    private final AsignacionRepository asignacionRepository;
    private final PerfilRepository perfilRepository;
    private final HabilidadRepository habilidadRepository;
    private final PerfilHabilidadRepository perfilHabilidadRepository;

    public ReporteService(ProyectoRepository proyectoRepository, AsignacionRepository asignacionRepository,
                           PerfilRepository perfilRepository, HabilidadRepository habilidadRepository,
                           PerfilHabilidadRepository perfilHabilidadRepository) {
        this.proyectoRepository = proyectoRepository;
        this.asignacionRepository = asignacionRepository;
        this.perfilRepository = perfilRepository;
        this.habilidadRepository = habilidadRepository;
        this.perfilHabilidadRepository = perfilHabilidadRepository;
    }

    public Map<String, Long> kpisAdministrador() {
        Map<String, Long> kpis = new LinkedHashMap<>();
        kpis.put("colaboradoresActivos", perfilRepository.countByEstado("activo"));
        kpis.put("proyectosEnCurso", proyectoRepository.countByEstado("activo"));
        kpis.put("habilidadesCatalogo", habilidadRepository.count());
        kpis.put("habilidadesSinAsignar", habilidadRepository.contarSinProyectoAsociado());
        kpis.put("asignacionesActivas", asignacionRepository.countByEstado("activa"));
        return kpis;
    }

    public List<VacanteHabilidad> topHabilidadesDeclaradas(int limite) {
        return habilidadRepository.findAllConCategoriaOrderByNombre().stream()
                .map(h -> new VacanteHabilidad(h.getNombre(), perfilHabilidadRepository.countById_HabilidadId(h.getId())))
                .sorted(Comparator.comparingLong(VacanteHabilidad::vacantes).reversed())
                .limit(limite)
                .collect(Collectors.toList());
    }

    public Map<String, Long> proyectosPorEstado() {
        Map<String, Long> mapa = new LinkedHashMap<>();
        for (String estado : List.of("activo", "planificacion", "en_pausa", "completado", "cancelado")) {
            long c = proyectoRepository.countByEstado(estado);
            if (c > 0) mapa.put(estado, c);
        }
        return mapa;
    }

    /** Igual que proyectosPorEstado() pero ya lista para pintar (label/badge resueltos). */
    public List<EstadoProyectoConConteo> proyectosPorEstadoDetallado() {
        return proyectosPorEstado().entrySet().stream()
                .map(e -> new EstadoProyectoConConteo(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
    }

    /** Ocupación promedio (0-100+) entre los colaboradores con al menos una asignación activa. */
    public int ocupacionPromedio() {
        List<OcupacionColaboradorFila> ocupacion = ocupacionPorColaborador();
        if (ocupacion.isEmpty()) return 0;
        return (int) Math.round(ocupacion.stream().mapToInt(OcupacionColaboradorFila::getPromedio).average().orElse(0));
    }

    public List<OcupacionColaboradorFila> ocupacionPorColaborador() {
        Map<Long, Integer> cargaPorPerfil = new HashMap<>();
        for (Object[] fila : asignacionRepository.sumarCargaActivaAgrupadaPorPerfil()) {
            cargaPorPerfil.put((Long) fila[0], ((Number) fila[1]).intValue());
        }
        List<Perfil> perfiles = perfilRepository.listarActivosConUsuario();
        return perfiles.stream()
                .filter(p -> cargaPorPerfil.containsKey(p.getId()))
                .map(p -> new OcupacionColaboradorFila(p.getUsuario().getNombreCompleto(), p.getCargo(), cargaPorPerfil.get(p.getId())))
                .sorted((a, b) -> Integer.compare(b.getPromedio(), a.getPromedio()))
                .collect(Collectors.toList());
    }

    public List<OcupacionColaboradorFila> colaboradoresSobrecargados() {
        return ocupacionPorColaborador().stream()
                .filter(o -> o.getPromedio() > 100)
                .collect(Collectors.toList());
    }
}
