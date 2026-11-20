package com.skillbridge.ai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillbridge.ai.dto.InicialesUtil;
import com.skillbridge.ai.dto.PaginaCandidatos;
import com.skillbridge.ai.dto.PaginaCandidatos.Candidato;
import com.skillbridge.ai.dto.PaginaCandidatos.Chip;
import com.skillbridge.ai.dto.PaginaCandidatos.Opcion;
import com.skillbridge.ai.model.Asignacion;
import com.skillbridge.ai.model.Habilidad;
import com.skillbridge.ai.model.Perfil;
import com.skillbridge.ai.model.PerfilHabilidad;
import com.skillbridge.ai.model.PropuestaAsignacion;
import com.skillbridge.ai.model.Proyecto;
import com.skillbridge.ai.model.ProyectoHabilidadRequerida;
import com.skillbridge.ai.repository.AsignacionRepository;
import com.skillbridge.ai.repository.HabilidadRepository;
import com.skillbridge.ai.repository.PerfilHabilidadRepository;
import com.skillbridge.ai.repository.PerfilRepository;
import com.skillbridge.ai.repository.PropuestaAsignacionRepository;
import com.skillbridge.ai.repository.ProyectoHabilidadRequeridaRepository;
import com.skillbridge.ai.repository.ProyectoRepository;
import com.skillbridge.ai.util.OperacionInvalidaException;
import com.skillbridge.ai.util.Roles;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Selector de personas (modelo v7, fase 4): búsqueda en el servidor de a quién
 * asignar (Resource Manager) o proponer (Project Manager) en un proyecto.
 *
 * Quién aparece: solo Miembros asignables (cuenta y perfil activos, sin rol
 * de Admin/RM) que no estén ya en el equipo. Quien no tiene capacidad para
 * la dedicación pedida (o está en el máximo de proyectos vigentes) aparece
 * deshabilitado con su motivo, salvo que se pida "solo con capacidad".
 *
 * Rendimiento: todo se carga con un puñado de consultas agrupadas (carga y
 * proyectos vigentes por perfil, habilidades declaradas, catálogo) y se
 * filtra/ordena/pagina en memoria: no hay una consulta por persona.
 */
@Service
public class SelectorPersonasService {

    public static final int TAMANIO_PAGINA = 12;

    private final ProyectoRepository proyectoRepository;
    private final PerfilRepository perfilRepository;
    private final AsignacionRepository asignacionRepository;
    private final PerfilHabilidadRepository perfilHabilidadRepository;
    private final HabilidadRepository habilidadRepository;
    private final ProyectoHabilidadRequeridaRepository requeridaRepository;
    private final PropuestaAsignacionRepository propuestaRepository;
    private final ProyectoService proyectoService;
    private final ObjectMapper objectMapper;

    public SelectorPersonasService(ProyectoRepository proyectoRepository, PerfilRepository perfilRepository,
                                   AsignacionRepository asignacionRepository, PerfilHabilidadRepository perfilHabilidadRepository,
                                   HabilidadRepository habilidadRepository, ProyectoHabilidadRequeridaRepository requeridaRepository,
                                   PropuestaAsignacionRepository propuestaRepository, ProyectoService proyectoService,
                                   ObjectMapper objectMapper) {
        this.proyectoRepository = proyectoRepository;
        this.perfilRepository = perfilRepository;
        this.asignacionRepository = asignacionRepository;
        this.perfilHabilidadRepository = perfilHabilidadRepository;
        this.habilidadRepository = habilidadRepository;
        this.requeridaRepository = requeridaRepository;
        this.propuestaRepository = propuestaRepository;
        this.proyectoService = proyectoService;
        this.objectMapper = objectMapper;
    }

    /** Filtros que llegan desde el selector (todos opcionales). */
    public record Filtros(String q, List<Long> habilidades, String cargo, Integer disponibleMin, Integer experienciaMin,
                          boolean soloConCapacidad, int dedicacion, String orden, int pagina) {
    }

    @Transactional(readOnly = true)
    public PaginaCandidatos buscar(Long proyectoId, Filtros f) {
        Proyecto proyecto = proyectoRepository.findById(proyectoId)
                .orElseThrow(() -> new OperacionInvalidaException("El proyecto ya no existe."));
        return construir(proyecto, f, false);
    }

    /**
     * Candidatos para ser PROJECT MANAGER (modelo v7, fase 5): al crear un
     * proyecto (proyectoId null, Administrador) o en "Cambiar PM" (Resource
     * Manager). Un colaborador del equipo puede ascender conservando su
     * dedicación; quien viene de fuera entra con 20% y necesita capacidad
     * (salvo en un proyecto cerrado, donde su carga no cuenta hasta reabrir).
     */
    @Transactional(readOnly = true)
    public PaginaCandidatos buscarParaPm(Long proyectoId, Filtros f) {
        Proyecto proyecto = proyectoId == null ? null : proyectoRepository.findById(proyectoId)
                .orElseThrow(() -> new OperacionInvalidaException("El proyecto ya no existe."));
        Filtros conPm = new Filtros(f.q(), f.habilidades(), f.cargo(), f.disponibleMin(), f.experienciaMin(),
                f.soloConCapacidad(), proyectoId == null ? f.dedicacion() : ProyectoService.CARGA_PM_POR_DEFECTO, f.orden(), f.pagina());
        return construir(proyecto, conPm, true);
    }

    private PaginaCandidatos construir(Proyecto proyecto, Filtros f, boolean modoPm) {
        Long proyectoId = proyecto != null ? proyecto.getId() : null;
        int limite = proyectoService.limiteCargaConfigurado();
        int maximo = modoPm ? ProyectoService.MAXIMO_PROYECTOS_PM : proyectoService.maximoProyectosConfigurado();
        int dedicacion = Math.max(1, Math.min(limite, f.dedicacion() > 0 ? f.dedicacion() : 50));
        boolean cerrado = proyecto != null && ProyectoService.estaCerrado(proyecto);

        Map<Long, Habilidad> habilidades = habilidadRepository.findAll().stream()
                .collect(Collectors.toMap(Habilidad::getId, h -> h, (a, b) -> a, LinkedHashMap::new));
        Map<Long, Integer> requeridas = proyecto != null ? requisitos(proyecto, habilidades) : new LinkedHashMap<>();

        Map<Long, Integer> cargaPorPerfil = new HashMap<>();
        for (Object[] fila : asignacionRepository.sumarCargaActivaAgrupadaPorPerfil()) {
            cargaPorPerfil.put((Long) fila[0], ((Number) fila[1]).intValue());
        }
        Map<Long, Long> vigentesPorPerfil = new HashMap<>();
        for (Object[] fila : asignacionRepository.contarProyectosVigentesAgrupadoPorPerfil()) {
            vigentesPorPerfil.put((Long) fila[0], ((Number) fila[1]).longValue());
        }
        Map<Long, Map<Long, Integer>> nivelesPorPerfil = new HashMap<>();
        for (PerfilHabilidad ph : perfilHabilidadRepository.findAll()) {
            nivelesPorPerfil.computeIfAbsent(ph.getId().getPerfilId(), k -> new HashMap<>())
                    .put(ph.getId().getHabilidadId(), ph.getNivel() != null ? ph.getNivel() : 0);
        }
        // Equipo actual: perfilId -> fila activa (para excluir, o en modo PM para ascender).
        Map<Long, Asignacion> equipo = proyectoId == null ? Map.of()
                : asignacionRepository.listarEquipoDeProyecto(proyectoId, "activa").stream()
                .collect(Collectors.toMap(Asignacion::getPerfilId, a -> a, (a, b) -> a));
        Set<Long> conPropuesta = proyectoId == null ? Set.of()
                : propuestaRepository.findByProyectoIdAndEstado(proyectoId, "pendiente").stream()
                .map(PropuestaAsignacion::getCandidatoPerfilId).collect(Collectors.toSet());

        String q = normalizar(f.q());
        List<Long> filtroHabilidades = f.habilidades() != null ? f.habilidades() : List.of();

        List<Candidato> todos = new ArrayList<>();
        List<String> cargos = new ArrayList<>();
        for (Perfil p : perfilRepository.listarActivosConUsuario()) {
            if (!ProyectoService.esMiembroAsignable(p)) continue;
            Asignacion filaEquipo = equipo.get(p.getId());
            if (filaEquipo != null && (!modoPm || Roles.PROJECT_MANAGER.equals(filaEquipo.getRolEnProyecto()))) continue;
            String cargo = p.getCargo() != null && !p.getCargo().isBlank() ? p.getCargo().trim() : "";
            if (!cargo.isEmpty() && !cargos.contains(cargo)) cargos.add(cargo);

            String nombre = p.getUsuario().getNombreCompleto();
            String correo = p.getUsuario().getCorreo();
            if (!q.isEmpty() && !normalizar(nombre + " " + correo + " " + cargo).contains(q)) continue;
            if (f.cargo() != null && !f.cargo().isBlank() && !f.cargo().equalsIgnoreCase(cargo)) continue;

            Map<Long, Integer> niveles = nivelesPorPerfil.getOrDefault(p.getId(), Map.of());
            if (!filtroHabilidades.isEmpty() && !filtroHabilidades.stream().allMatch(id -> niveles.getOrDefault(id, 0) > 0)) continue;

            int experiencia = p.getExperienciaAnios() != null ? p.getExperienciaAnios() : 0;
            if (f.experienciaMin() != null && experiencia < f.experienciaMin()) continue;

            int carga = cargaPorPerfil.getOrDefault(p.getId(), 0);
            int disponible = Math.max(0, limite - carga);
            if (f.disponibleMin() != null && disponible < f.disponibleMin()) continue;
            long vigentes = vigentesPorPerfil.getOrDefault(p.getId(), 0L);

            String motivo = null;
            String nota = null;
            if (filaEquipo != null) {
                // Modo PM: ya colabora en el proyecto, asciende conservando su dedicación.
                nota = "En el equipo · conserva su " + (filaEquipo.getCargaPorcentaje() != null ? filaEquipo.getCargaPorcentaje() : 0) + "%";
            } else if (cerrado) {
                nota = "Proyecto cerrado: su carga se valida al reabrir";
            } else if (vigentes >= maximo) {
                motivo = "Está en el máximo de " + maximo + " proyectos vigentes";
            } else if (carga + dedicacion > limite) {
                motivo = "Con " + dedicacion + "% llegaría a " + (carga + dedicacion) + "% (límite " + limite + "%)";
            }
            if (motivo != null && f.soloConCapacidad()) continue;

            // Chips: primero las que pide el proyecto (cumpla o no), luego hasta 4 más de la persona.
            List<Chip> chips = new ArrayList<>();
            int coincidencias = 0;
            for (Map.Entry<Long, Integer> req : requeridas.entrySet()) {
                int tiene = niveles.getOrDefault(req.getKey(), 0);
                boolean cumple = tiene >= req.getValue();
                if (cumple) coincidencias++;
                if (tiene > 0) chips.add(new Chip(nombreHabilidad(habilidades, req.getKey()), tiene, true, cumple));
            }
            niveles.entrySet().stream()
                    .filter(e -> !requeridas.containsKey(e.getKey()))
                    .sorted(Map.Entry.<Long, Integer>comparingByValue().reversed())
                    .limit(4)
                    .forEach(e -> chips.add(new Chip(nombreHabilidad(habilidades, e.getKey()), e.getValue(), false, false)));

            todos.add(new Candidato(p.getId(), nombre, correo, InicialesUtil.de(nombre), cargo.isEmpty() ? "Miembro" : cargo,
                    experiencia, carga, disponible, vigentes, coincidencias, requeridas.size(), chips,
                    motivo == null, motivo, conPropuesta.contains(p.getId()), filaEquipo != null, nota));
        }

        Comparator<Candidato> porNombre = Comparator.comparing(Candidato::nombre, String.CASE_INSENSITIVE_ORDER);
        Comparator<Candidato> orden = switch (f.orden() == null ? "" : f.orden()) {
            case "disponibilidad" -> Comparator.comparingInt(Candidato::disponible).reversed().thenComparing(porNombre);
            case "experiencia" -> Comparator.comparingInt(Candidato::experienciaAnios).reversed().thenComparing(porNombre);
            case "nombre" -> porNombre;
            // "match" (por defecto): quien cumple más habilidades pedidas, luego más disponible.
            default -> Comparator.comparingInt(Candidato::coincidencias).reversed()
                    .thenComparing(Comparator.comparingInt(Candidato::disponible).reversed())
                    .thenComparing(porNombre);
        };
        // Los seleccionables siempre primero: lo bloqueado queda al final, como referencia.
        Comparator<Candidato> base = Comparator.comparing((Candidato c) -> !c.seleccionable());
        if (modoPm) base = base.thenComparing((Candidato c) -> !c.enEquipo());
        todos.sort(base.thenComparing(orden));

        int total = todos.size();
        int paginas = Math.max(1, (int) Math.ceil(total / (double) TAMANIO_PAGINA));
        int pagina = Math.max(1, Math.min(f.pagina(), paginas));
        List<Candidato> items = todos.subList(Math.min(total, (pagina - 1) * TAMANIO_PAGINA), Math.min(total, pagina * TAMANIO_PAGINA));

        long vacantes = 0;
        if (proyecto != null) {
            long colaboradores = asignacionRepository.countByProyectoIdAndEstadoAndRolEnProyecto(proyectoId, "activa", Roles.COLABORADOR);
            vacantes = Math.max(0, (proyecto.getColaboradoresRequeridos() != null ? proyecto.getColaboradoresRequeridos() : 0) - colaboradores);
        }

        List<Opcion> deProyecto = requeridas.entrySet().stream()
                .map(e -> new Opcion(e.getKey(), nombreHabilidad(habilidades, e.getKey()), e.getValue())).toList();
        List<Opcion> catalogo = habilidades.values().stream()
                .sorted(Comparator.comparing(Habilidad::getNombre, String.CASE_INSENSITIVE_ORDER))
                .map(h -> new Opcion(h.getId(), h.getNombre(), 0)).toList();
        cargos.sort(String.CASE_INSENSITIVE_ORDER);

        return new PaginaCandidatos(new ArrayList<>(items), total, pagina, paginas, vacantes, limite, maximo,
                deProyecto, catalogo, cargos);
    }

    /** Habilidades que pide el proyecto; si no tiene, se deducen de sus tecnologías (igual que Talent Matching). */
    private Map<Long, Integer> requisitos(Proyecto p, Map<Long, Habilidad> habilidades) {
        Map<Long, Integer> r = new LinkedHashMap<>();
        for (ProyectoHabilidadRequerida req : requeridaRepository.findById_ProyectoId(p.getId())) {
            r.put(req.getId().getHabilidadId(), req.getNivelRequerido());
        }
        if (!r.isEmpty() || p.getTecnologias() == null || p.getTecnologias().isBlank()) return r;
        try {
            List<String> tecnologias = objectMapper.readValue(p.getTecnologias(), new TypeReference<List<String>>() {});
            for (String t : tecnologias) {
                habilidades.values().stream().filter(h -> normalizar(h.getNombre()).equals(normalizar(t)))
                        .findFirst().ifPresent(h -> r.put(h.getId(), 3));
            }
        } catch (Exception ignored) {
            // tecnologías mal formadas: simplemente no hay requisitos deducidos
        }
        return r;
    }

    private static String nombreHabilidad(Map<Long, Habilidad> habilidades, Long id) {
        Habilidad h = habilidades.get(id);
        return h != null ? h.getNombre() : "Habilidad";
    }

    private static String normalizar(String v) {
        return Normalizer.normalize(Objects.toString(v, ""), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase().trim();
    }
}
