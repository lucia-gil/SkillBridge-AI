package com.skillbridge.ai.service;

import com.skillbridge.ai.dto.CertificadoFila;
import com.skillbridge.ai.dto.HabilidadPerfilFila;
import com.skillbridge.ai.dto.InicialesUtil;
import com.skillbridge.ai.dto.MiProyectoFila;
import com.skillbridge.ai.dto.OcupacionColaboradorFila;
import com.skillbridge.ai.dto.PerfilColaboradorVista;
import com.skillbridge.ai.dto.PerfilColaboradorVista.Certificacion;
import com.skillbridge.ai.dto.PerfilColaboradorVista.Enlace;
import com.skillbridge.ai.dto.PerfilColaboradorVista.GrupoHabilidades;
import com.skillbridge.ai.dto.PerfilColaboradorVista.TecnologiaUso;
import com.skillbridge.ai.model.Habilidad;
import com.skillbridge.ai.model.Perfil;
import com.skillbridge.ai.model.Usuario;
import com.skillbridge.ai.repository.AsignacionRepository;
import com.skillbridge.ai.repository.CertificadoHabilidadRepository;
import com.skillbridge.ai.repository.HabilidadRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Arma el perfil profesional de un colaborador (vista del Resource Manager):
 * identidad y contacto, presentación, carga actual contra el límite,
 * experiencia en proyectos, habilidades por categoría y constancias.
 * Todo sale de la base; aquí solo se agrupa y se cuenta.
 */
@Service
public class PerfilVistaService {

    private static final int MAX_TECNOLOGIAS = 12;
    private static final Locale ES = Locale.forLanguageTag("es-PE");
    private static final DateTimeFormatter MES_ANIO = DateTimeFormatter.ofPattern("MMMM yyyy", ES);
    private static final DateTimeFormatter FECHA_CORTA = DateTimeFormatter.ofPattern("d MMM yyyy", ES);

    private final HabilidadService habilidadService;
    private final ProyectoService proyectoService;
    private final ConfiguracionService configuracionService;
    private final AsignacionRepository asignacionRepository;
    private final HabilidadRepository habilidadRepository;
    private final CertificadoHabilidadRepository certificadoRepository;

    public PerfilVistaService(HabilidadService habilidadService, ProyectoService proyectoService,
                              ConfiguracionService configuracionService, AsignacionRepository asignacionRepository,
                              HabilidadRepository habilidadRepository,
                              CertificadoHabilidadRepository certificadoRepository) {
        this.habilidadService = habilidadService;
        this.proyectoService = proyectoService;
        this.configuracionService = configuracionService;
        this.asignacionRepository = asignacionRepository;
        this.habilidadRepository = habilidadRepository;
        this.certificadoRepository = certificadoRepository;
    }

    /** @param p perfil con su usuario ya cargado (PerfilRepository.buscarConUsuario) */
    public PerfilColaboradorVista armar(Perfil p) {
        Usuario u = p.getUsuario();
        PerfilColaboradorVista v = new PerfilColaboradorVista();

        // ── Identidad y contacto ──
        v.setPerfilId(p.getId());
        v.setUsuarioId(u.getId());
        v.setNombre(u.getNombreCompleto());
        v.setIniciales(InicialesUtil.de(u.getNombreCompleto()));
        v.setTieneFoto(u.tieneFotoPerfil());
        v.setCorreo(u.getCorreo());
        v.setCargo(p.getCargo());
        v.setTelefono(p.getTelefono());
        v.setTelefonoHref(p.getTelefono() != null ? "tel:" + p.getTelefono().replaceAll("[^0-9+]", "") : null);
        v.setUbicacion(p.getUbicacion());
        v.setMiembroDesde(mesAnio(u.getFechaCreacion() != null ? u.getFechaCreacion() : p.getFechaCreacion()));
        v.setCuentaActiva(u.isActivo());
        if (!u.isActivo()) {
            v.setEstadoLabel("Cuenta suspendida");
            v.setEstadoBadgeClass("badge-red");
        } else if ("activo".equals(p.getEstado())) {
            v.setEstadoLabel("Disponible");
            v.setEstadoBadgeClass("badge-green");
        } else {
            v.setEstadoLabel("No disponible");
            v.setEstadoBadgeClass("badge-amber");
        }

        // ── Presentación ──
        v.setBiografia(p.getBiografia() != null ? p.getBiografia().trim() : null);
        v.setEtiquetas(PerfilDatosUtil.leerEtiquetas(p.getEtiquetas()));
        v.setEnlaces(enlaces(p));
        v.setExperienciaAnios(p.getExperienciaAnios() != null ? p.getExperienciaAnios() : 0);
        v.setDisponibilidad(p.getDisponibilidadPorcentaje() != null ? p.getDisponibilidadPorcentaje() : 0);

        // ── Carga actual contra el límite configurado ──
        Integer suma = asignacionRepository.sumarCargaActivaDePerfil(p.getId());
        int ocupacion = suma != null ? suma : 0;
        int limite = configuracionService.limiteCarga();
        OcupacionColaboradorFila estado = ocupacion == 0
                ? OcupacionColaboradorFila.sinAsignacion(p.getId(), u.getNombreCompleto(), p.getCargo(), limite)
                : new OcupacionColaboradorFila(p.getId(), u.getNombreCompleto(), p.getCargo(), ocupacion, limite);
        v.setOcupacion(ocupacion);
        v.setLimite(limite);
        v.setOcupacionEstado(estado.getEstadoLabel());
        v.setOcupacionBadgeClass(estado.getEstadoBadgeClass());
        v.setOcupacionClave(clave(estado.getEstadoLabel()));

        // ── Proyectos ──
        List<MiProyectoFila> historial = proyectoService.misProyectosHistorial(p.getId());
        List<MiProyectoFila> actuales = historial.stream()
                .filter(m -> "activa".equals(m.getAsignacionEstadoCrudo()))
                .collect(Collectors.toList());
        // Primero lo actual, luego lo anterior; dentro, como viene (más reciente primero).
        List<MiProyectoFila> ordenados = new ArrayList<>(actuales);
        historial.stream().filter(m -> !"activa".equals(m.getAsignacionEstadoCrudo())).forEach(ordenados::add);
        v.setProyectos(ordenados);
        v.setProyectosActuales(actuales);
        v.setProyectosDistintos((int) historial.stream().map(MiProyectoFila::getProyectoId).distinct().count());
        v.setTecnologias(tecnologias(historial));

        // ── Habilidades y constancias ──
        List<HabilidadPerfilFila> habilidades = habilidadService.habilidadesConValidacion(p.getId());
        Map<Long, Habilidad> catalogo = habilidadRepository.findAllConCategoriaOrderByNombre().stream()
                .collect(Collectors.toMap(Habilidad::getId, h -> h, (a, b) -> a));
        Map<String, Integer> tonoPorCategoria = new HashMap<>();
        for (HabilidadPerfilFila h : habilidades) {
            Habilidad hab = catalogo.get(h.getHabilidadId());
            long catId = hab != null && hab.getCategoria() != null && hab.getCategoria().getId() != null
                    ? hab.getCategoria().getId() : 0L;
            tonoPorCategoria.putIfAbsent(h.getCategoriaNombre(), (int) (catId % 8));
        }

        Map<String, List<HabilidadPerfilFila>> porCategoria = new LinkedHashMap<>();
        habilidades.stream()
                .sorted(Comparator.comparing(HabilidadPerfilFila::getCategoriaNombre, String.CASE_INSENSITIVE_ORDER))
                .forEach(h -> porCategoria.computeIfAbsent(h.getCategoriaNombre(), k -> new ArrayList<>()).add(h));
        List<GrupoHabilidades> grupos = new ArrayList<>();
        porCategoria.forEach((cat, lista) -> {
            lista.sort(Comparator.comparingInt(HabilidadPerfilFila::getNivelNumero).reversed()
                    .thenComparing(HabilidadPerfilFila::getNombre, String.CASE_INSENSITIVE_ORDER));
            grupos.add(new GrupoHabilidades(cat, tonoPorCategoria.getOrDefault(cat, 0), lista));
        });
        // Las categorías con más habilidades declaradas, primero.
        grupos.sort(Comparator.comparingInt((GrupoHabilidades g) -> g.getHabilidades().size()).reversed());
        v.setGrupos(grupos);
        v.setHabilidadesTotal(habilidades.size());
        int validadas = (int) habilidades.stream().filter(HabilidadPerfilFila::isValidada).count();
        v.setHabilidadesValidadas(validadas);
        v.setHabilidadesPendientes(habilidades.size() - validadas);

        Map<Long, LocalDateTime> fechas = new HashMap<>();
        for (Object[] fila : certificadoRepository.fechasDePerfil(p.getId())) {
            if (fila[0] != null && fila[1] instanceof LocalDateTime f) fechas.put((Long) fila[0], f);
        }
        List<Certificacion> certificaciones = new ArrayList<>();
        for (HabilidadPerfilFila h : habilidades) {
            for (CertificadoFila c : h.getCertificados()) {
                LocalDateTime f = fechas.get(c.id());
                certificaciones.add(new Certificacion(c, h.getNombre(), h.getNivelLabel(), h.isValidada(),
                        tonoPorCategoria.getOrDefault(h.getCategoriaNombre(), 0),
                        f != null ? f.format(FECHA_CORTA) : null));
            }
        }
        v.setCertificaciones(certificaciones);
        return v;
    }

    private static List<Enlace> enlaces(Perfil p) {
        List<Enlace> lista = new ArrayList<>();
        agregar(lista, "linkedin", "LinkedIn", p.getLinkedinUrl());
        agregar(lista, "github", "GitHub", p.getGithubUrl());
        agregar(lista, "portafolio", "Portafolio", p.getPortafolioUrl());
        return lista;
    }

    private static void agregar(List<Enlace> lista, String tipo, String nombre, String url) {
        String segura = PerfilDatosUtil.urlSegura(url);
        if (segura != null) lista.add(new Enlace(tipo, nombre, segura, PerfilDatosUtil.urlLegible(segura)));
    }

    /** Tecnologías de los proyectos del historial, contadas una vez por proyecto. */
    private static List<TecnologiaUso> tecnologias(List<MiProyectoFila> historial) {
        Map<String, String> nombres = new LinkedHashMap<>();
        Map<String, Set<Long>> proyectos = new HashMap<>();
        for (MiProyectoFila m : historial) {
            if (m.getTecnologias() == null) continue;
            for (String t : m.getTecnologias()) {
                if (t == null || t.isBlank()) continue;
                String k = t.trim().toLowerCase(Locale.ROOT);
                nombres.putIfAbsent(k, t.trim());
                proyectos.computeIfAbsent(k, x -> new HashSet<>()).add(m.getProyectoId());
            }
        }
        return nombres.entrySet().stream()
                .map(e -> new TecnologiaUso(e.getValue(), proyectos.get(e.getKey()).size()))
                .sorted(Comparator.comparingInt(TecnologiaUso::getProyectos).reversed()
                        .thenComparing(TecnologiaUso::getNombre, String.CASE_INSENSITIVE_ORDER))
                .limit(MAX_TECNOLOGIAS)
                .collect(Collectors.toList());
    }

    private static String clave(String estadoLabel) {
        return switch (estadoLabel) {
            case "Sin asignación" -> "banca";
            case "Subutilizado" -> "sub";
            case "Al límite" -> "lim";
            case "Sobre-asignado" -> "sobre";
            default -> "opt";
        };
    }

    private static String mesAnio(LocalDateTime f) {
        if (f == null) return null;
        String s = f.format(MES_ANIO);
        return s.substring(0, 1).toUpperCase(ES) + s.substring(1);
    }
}
