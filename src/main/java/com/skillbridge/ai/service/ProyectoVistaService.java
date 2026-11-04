package com.skillbridge.ai.service;

import com.skillbridge.ai.dto.EntregableFila;
import com.skillbridge.ai.dto.InicialesUtil;
import com.skillbridge.ai.dto.MatchingCandidato;
import com.skillbridge.ai.dto.ProyectoDetalle;
import com.skillbridge.ai.model.Asignacion;
import com.skillbridge.ai.model.Habilidad;
import com.skillbridge.ai.model.PerfilHabilidad;
import com.skillbridge.ai.model.Proyecto;
import com.skillbridge.ai.repository.AsignacionRepository;
import com.skillbridge.ai.repository.ChatSalaRepository;
import com.skillbridge.ai.repository.ForoPublicacionRepository;
import com.skillbridge.ai.repository.HabilidadRepository;
import com.skillbridge.ai.repository.PerfilHabilidadRepository;
import com.skillbridge.ai.repository.PropuestaAsignacionRepository;
import com.skillbridge.ai.repository.ProyectoRepository;
import com.skillbridge.ai.util.OperacionInvalidaException;
import com.skillbridge.ai.util.Roles;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Vista de detalle de un proyecto para el Project Manager y el Resource
 * Manager (compartido/proyecto-detalle.html): cabecera, stack, cobertura de
 * habilidades requeridas por el equipo actual, equipo con su carga total,
 * entregables, archivos, foro/chat y candidatos sugeridos (mismo ranking de
 * AI Talent Matching).
 */
@Service
public class ProyectoVistaService {

    /** Dedicación de referencia para calcular los candidatos sugeridos. */
    public static final int DEDICACION_REFERENCIA = 50;

    public record Cobertura(String habilidad, int nivelRequerido, String nivelLabel,
                            List<String> cubren, List<String> parcial, String estado) {
        public boolean cubierta() { return "cubierta".equals(estado); }
        public boolean hueco() { return "hueco".equals(estado); }
    }

    public record Miembro(Long asignacionId, Long perfilId, String nombre, String iniciales, String cargo,
                          boolean esPm, int cargaProyecto, int cargaTotal, int limite) {
        public String tono() {
            if (cargaTotal > limite) return "red";
            if (cargaTotal >= limite - 10) return "amber";
            if (cargaTotal < 60) return "blue";
            return "green";
        }
    }

    public record Vista(ProyectoDetalle detalle, String pmNombre, int vacantes, long propuestasPendientes,
                        int integrantes, String plazoLabel, String plazoTono,
                        List<Cobertura> cobertura, long coberturaCompleta,
                        List<Miembro> equipo, List<EntregableFila> entregables, long entregablesAbiertos,
                        long entregablesVencidos, long hilosForo, Long salaChatId,
                        List<MatchingCandidato> sugeridos, boolean admiteIntegrantes, int dedicacionReferencia,
                        String duracionLabel) {
        public long huecos() { return cobertura.stream().filter(Cobertura::hueco).count(); }
    }

    private final ProyectoService proyectoService;
    private final TalentMatchingService talentMatchingService;
    private final EntregableService entregableService;
    private final DemandaHabilidadesService demandaService;
    private final ProyectoRepository proyectoRepository;
    private final AsignacionRepository asignacionRepository;
    private final HabilidadRepository habilidadRepository;
    private final PerfilHabilidadRepository perfilHabilidadRepository;
    private final PropuestaAsignacionRepository propuestaRepository;
    private final ForoPublicacionRepository foroRepository;
    private final ChatSalaRepository chatSalaRepository;

    public ProyectoVistaService(ProyectoService proyectoService, TalentMatchingService talentMatchingService,
                                EntregableService entregableService, DemandaHabilidadesService demandaService,
                                ProyectoRepository proyectoRepository, AsignacionRepository asignacionRepository,
                                HabilidadRepository habilidadRepository, PerfilHabilidadRepository perfilHabilidadRepository,
                                PropuestaAsignacionRepository propuestaRepository, ForoPublicacionRepository foroRepository,
                                ChatSalaRepository chatSalaRepository) {
        this.proyectoService = proyectoService;
        this.talentMatchingService = talentMatchingService;
        this.entregableService = entregableService;
        this.demandaService = demandaService;
        this.proyectoRepository = proyectoRepository;
        this.asignacionRepository = asignacionRepository;
        this.habilidadRepository = habilidadRepository;
        this.perfilHabilidadRepository = perfilHabilidadRepository;
        this.propuestaRepository = propuestaRepository;
        this.foroRepository = foroRepository;
        this.chatSalaRepository = chatSalaRepository;
    }

    /** true si el perfil es el Project Manager activo del proyecto. */
    public boolean esPmDe(Long perfilId, Long proyectoId) {
        return perfilId != null && asignacionRepository.buscarResponsablesActivos(proyectoId, Roles.PROJECT_MANAGER).stream()
                .anyMatch(a -> a.getPerfilId().equals(perfilId));
    }

    @Transactional(readOnly = true)
    public Vista vista(Long proyectoId) {
        Proyecto p = proyectoRepository.findById(proyectoId)
                .orElseThrow(() -> new OperacionInvalidaException("El proyecto ya no existe."));
        ProyectoDetalle detalle = proyectoService.obtenerDetalle(proyectoId);
        int limite = proyectoService.limiteCargaConfigurado();

        // Equipo activo (PM primero) con la carga total de cada persona
        List<Asignacion> activas = asignacionRepository.listarEquipoDeProyecto(proyectoId, "activa");
        List<Miembro> equipo = activas.stream()
                .map(a -> new Miembro(a.getId(), a.getPerfilId(), a.getPerfil().getUsuario().getNombreCompleto(),
                        InicialesUtil.de(a.getPerfil().getUsuario().getNombreCompleto()),
                        Optional.ofNullable(a.getPerfil().getCargo()).orElse("Colaborador"),
                        Roles.PROJECT_MANAGER.equals(a.getRolEnProyecto()), a.getCargaPorcentaje(),
                        proyectoService.cargaActivaDe(a.getPerfilId()), limite))
                .sorted(Comparator.comparing(Miembro::esPm).reversed().thenComparing(Miembro::nombre))
                .toList();
        String pmNombre = equipo.stream().filter(Miembro::esPm).map(Miembro::nombre).findFirst().orElse("Sin asignar");
        long colaboradores = equipo.stream().filter(m -> !m.esPm()).count();
        int vacantes = (int) Math.max(0, Optional.ofNullable(p.getColaboradoresRequeridos()).orElse(0) - colaboradores);

        // Cobertura: quién del equipo cumple cada habilidad requerida
        Map<Long, Habilidad> catalogo = habilidadRepository.findAll().stream()
                .collect(Collectors.toMap(Habilidad::getId, Function.identity()));
        Map<Long, Map<Long, Integer>> nivelesPorPerfil = new java.util.HashMap<>();
        for (Miembro m : equipo) {
            nivelesPorPerfil.put(m.perfilId(), perfilHabilidadRepository.findById_PerfilId(m.perfilId()).stream()
                    .collect(Collectors.toMap(ph -> ph.getId().getHabilidadId(), PerfilHabilidad::getNivel, Math::max)));
        }
        List<Cobertura> cobertura = new ArrayList<>();
        demandaService.requisitos(p, catalogo.values()).forEach((habId, nivel) -> {
            List<String> cubren = new ArrayList<>();
            List<String> parcial = new ArrayList<>();
            for (Miembro m : equipo) {
                int tiene = nivelesPorPerfil.getOrDefault(m.perfilId(), Map.of()).getOrDefault(habId, 0);
                if (tiene >= nivel) cubren.add(m.nombre());
                else if (tiene > 0) parcial.add(m.nombre());
            }
            String estado = !cubren.isEmpty() ? "cubierta" : !parcial.isEmpty() ? "parcial" : "hueco";
            Habilidad h = catalogo.get(habId);
            cobertura.add(new Cobertura(h != null ? h.getNombre() : "Habilidad", nivel, nivelLabel(nivel), cubren, parcial, estado));
        });
        cobertura.sort(Comparator.comparingInt((Cobertura c) -> switch (c.estado()) { case "hueco" -> 0; case "parcial" -> 1; default -> 2; })
                .thenComparing(Cobertura::habilidad));
        long completa = cobertura.stream().filter(Cobertura::cubierta).count();

        // Entregables vigentes primero
        List<EntregableFila> entregables = entregableService.listarParaGestion(proyectoId);
        long abiertos = entregables.stream().filter(e -> "activo".equals(e.getEstadoCrudo()) && !e.isVencido()).count();
        long vencidos = entregables.stream().filter(EntregableFila::isVencido).count();

        long hilos = foroRepository.listarHilosDeProyectos(List.of(proyectoId)).size();
        Long salaId = chatSalaRepository.findFirstByProyectoIdOrderByIdAsc(proyectoId).map(s -> s.getId()).orElse(null);

        boolean admite = ProyectoService.admiteNuevosIntegrantes(p);
        List<MatchingCandidato> sugeridos = admite && vacantes > 0
                ? talentMatchingService.sugeridosPara(proyectoId, DEDICACION_REFERENCIA, 5) : List.of();

        String[] plazo = plazo(p);
        return new Vista(detalle, pmNombre, vacantes, propuestaRepository.countByProyectoIdAndEstado(proyectoId, "pendiente"),
                equipo.size(), plazo[0], plazo[1], cobertura, completa, equipo, entregables, abiertos, vencidos,
                hilos, salaId, sugeridos, admite, DEDICACION_REFERENCIA, duracion(p));
    }

    /** "Faltan 42 días", "Venció hace 3 días", "Empieza en 5 días"... y su tono. */
    private static String[] plazo(Proyecto p) {
        LocalDate hoy = LocalDate.now();
        String estado = p.getEstado();
        if ("completado".equals(estado)) return new String[]{"Proyecto completado", "green"};
        if ("cancelado".equals(estado)) return new String[]{"Proyecto cancelado", "muted"};
        if ("en_pausa".equals(estado)) return new String[]{"En pausa", "amber"};
        if (p.getFechaInicio() != null && p.getFechaInicio().isAfter(hoy)) {
            long d = ChronoUnit.DAYS.between(hoy, p.getFechaInicio());
            return new String[]{"Empieza en " + d + (d == 1 ? " día" : " días"), "blue"};
        }
        if (p.getFechaFinEstimada() == null) return new String[]{"Sin fecha de fin", "muted"};
        long d = ChronoUnit.DAYS.between(hoy, p.getFechaFinEstimada());
        if (d < 0) return new String[]{"Venció hace " + (-d) + (d == -1 ? " día" : " días"), "red"};
        if (d == 0) return new String[]{"Vence hoy", "red"};
        return new String[]{"Faltan " + d + (d == 1 ? " día" : " días"), d <= 14 ? "amber" : "green"};
    }

    /** "3 semanas", "5 meses", "1 año y 2 meses"... o "—" si falta una fecha. */
    private static String duracion(Proyecto p) {
        if (p.getFechaInicio() == null || p.getFechaFinEstimada() == null) return "—";
        long dias = ChronoUnit.DAYS.between(p.getFechaInicio(), p.getFechaFinEstimada());
        if (dias < 60) { long sem = Math.max(1, Math.round(dias / 7.0)); return sem + (sem == 1 ? " semana" : " semanas"); }
        long meses = Math.round(dias / 30.44);
        if (meses < 12) return meses + " meses";
        long anios = meses / 12, resto = meses % 12;
        return anios + (anios == 1 ? " año" : " años") + (resto == 0 ? "" : " y " + resto + (resto == 1 ? " mes" : " meses"));
    }

    private static String nivelLabel(int n) {
        return switch (n) { case 1, 2 -> "Básico"; case 3 -> "Intermedio"; case 4 -> "Avanzado"; case 5 -> "Experto"; default -> "—"; };
    }
}
