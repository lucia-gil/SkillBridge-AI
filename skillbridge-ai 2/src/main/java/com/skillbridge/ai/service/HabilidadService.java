package com.skillbridge.ai.service;

import com.skillbridge.ai.dto.CategoriaConConteo;
import com.skillbridge.ai.dto.HabilidadFila;
import com.skillbridge.ai.dto.HabilidadPerfilFila;
import com.skillbridge.ai.model.CategoriaHabilidad;
import com.skillbridge.ai.model.Habilidad;
import com.skillbridge.ai.model.PerfilHabilidad;
import com.skillbridge.ai.model.PerfilHabilidadId;
import com.skillbridge.ai.repository.CategoriaHabilidadRepository;
import com.skillbridge.ai.repository.HabilidadRepository;
import com.skillbridge.ai.repository.PerfilHabilidadRepository;
import com.skillbridge.ai.repository.ProyectoHabilidadRequeridaRepository;
import com.skillbridge.ai.util.OperacionInvalidaException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * CRUD completo del catalogo de habilidades (administrador/habilidades.html):
 * listar (con categorias y estadisticas reales), crear, editar, eliminar.
 *
 * "Personas" y "Proyectos" son conteos reales (perfil_habilidad /
 * proyecto_habilidad_requerida). "Demanda" NO tiene columna en el esquema
 * v4 provisto - se deriva como una heuristica simple a partir de
 * "Proyectos" (ver HabilidadFila); es una referencia visual, no un dato
 * almacenado, y se declara asi para no confabular una fuente de verdad que
 * no existe.
 */
@Service
public class HabilidadService {

    private final HabilidadRepository habilidadRepository;
    private final CategoriaHabilidadRepository categoriaHabilidadRepository;
    private final PerfilHabilidadRepository perfilHabilidadRepository;
    private final ProyectoHabilidadRequeridaRepository proyectoHabilidadRequeridaRepository;
    private final AuditoriaService auditoriaService;

    public HabilidadService(HabilidadRepository habilidadRepository,
                             CategoriaHabilidadRepository categoriaHabilidadRepository,
                             PerfilHabilidadRepository perfilHabilidadRepository,
                             ProyectoHabilidadRequeridaRepository proyectoHabilidadRequeridaRepository,
                             AuditoriaService auditoriaService) {
        this.habilidadRepository = habilidadRepository;
        this.categoriaHabilidadRepository = categoriaHabilidadRepository;
        this.perfilHabilidadRepository = perfilHabilidadRepository;
        this.proyectoHabilidadRequeridaRepository = proyectoHabilidadRequeridaRepository;
        this.auditoriaService = auditoriaService;
    }

    public List<HabilidadFila> listar() {
        return habilidadRepository.findAllConCategoriaOrderByNombre().stream()
                .map(h -> new HabilidadFila(
                        h.getId(), h.getNombre(), h.getCategoria().getId(), h.getCategoria().getNombre(),
                        perfilHabilidadRepository.countById_HabilidadId(h.getId()),
                        proyectoHabilidadRequeridaRepository.countById_HabilidadId(h.getId()),
                        perfilHabilidadRepository.promedioNivel(h.getId())))
                .collect(Collectors.toList());
    }

    public List<CategoriaConConteo> categoriasConConteo() {
        return categoriaHabilidadRepository.findAll().stream()
                .map(c -> new CategoriaConConteo(c.getId(), c.getNombre(), habilidadRepository.countByCategoriaId(c.getId())))
                .collect(Collectors.toList());
    }

    public List<CategoriaHabilidad> categorias() {
        return categoriaHabilidadRepository.findAll();
    }

    @Transactional
    public void crear(String nombre, Long categoriaId, Long actorId) {
        String limpio = validarNombre(nombre);
        if (habilidadRepository.existsByNombreIgnoreCase(limpio)) {
            throw new OperacionInvalidaException("Ya existe una habilidad con ese nombre.");
        }
        CategoriaHabilidad categoria = categoriaHabilidadRepository.findById(categoriaId)
                .orElseThrow(() -> new OperacionInvalidaException("Selecciona una categoría válida."));

        Habilidad h = new Habilidad();
        h.setNombre(limpio);
        h.setCategoria(categoria);
        h = habilidadRepository.save(h);

        auditoriaService.registrar(actorId, "HABILIDAD_CREADA", "habilidad", h.getId(), null, null,
                limpio + " (" + categoria.getNombre() + ")");
    }

    @Transactional
    public void editar(Long id, String nombre, Long categoriaId, Long actorId) {
        String limpio = validarNombre(nombre);
        Habilidad h = habilidadRepository.findById(id)
                .orElseThrow(() -> new OperacionInvalidaException("La habilidad ya no existe."));

        if (!h.getNombre().equalsIgnoreCase(limpio) && habilidadRepository.existsByNombreIgnoreCase(limpio)) {
            throw new OperacionInvalidaException("Ya existe otra habilidad con ese nombre.");
        }
        CategoriaHabilidad categoria = categoriaHabilidadRepository.findById(categoriaId)
                .orElseThrow(() -> new OperacionInvalidaException("Selecciona una categoría válida."));

        String anterior = h.getNombre() + " / " + h.getCategoria().getNombre();
        h.setNombre(limpio);
        h.setCategoria(categoria);
        habilidadRepository.save(h);

        auditoriaService.registrar(actorId, "HABILIDAD_EDITADA", "habilidad", id,
                AuditoriaService.json("valor", anterior),
                AuditoriaService.json("valor", limpio + " / " + categoria.getNombre()), null);
    }

    @Transactional
    public void eliminar(Long id, Long actorId) {
        Habilidad h = habilidadRepository.findById(id)
                .orElseThrow(() -> new OperacionInvalidaException("La habilidad ya no existe."));
        String nombre = h.getNombre();
        try {
            habilidadRepository.delete(h);
            habilidadRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new OperacionInvalidaException(
                    "No se puede eliminar \"" + nombre + "\": está en uso en perfiles o requisitos de proyecto.");
        }
        auditoriaService.registrar(actorId, "HABILIDAD_ELIMINADA", "habilidad", id, null, null, nombre);
    }

    // ─────────────── Habilidades declaradas por un colaborador (colaborador/perfil.html) ───────────────

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public List<HabilidadPerfilFila> misHabilidades(Long perfilId) {
        Map<Long, Habilidad> catalogo = habilidadRepository.findAllConCategoriaOrderByNombre().stream()
                .collect(Collectors.toMap(Habilidad::getId, h -> h));
        return perfilHabilidadRepository.findById_PerfilId(perfilId).stream()
                .map(ph -> {
                    Habilidad h = catalogo.get(ph.getId().getHabilidadId());
                    if (h == null) return null; // habilidad borrada del catálogo desde que se declaró: se omite
                    String desde = ph.getFechaDeclaracion() != null ? ph.getFechaDeclaracion().format(FORMATO_FECHA) : "—";
                    return new HabilidadPerfilFila(h.getId(), h.getNombre(), h.getCategoria().getNombre(), ph.getNivel(), desde);
                })
                .filter(java.util.Objects::nonNull)
                .sorted((a, b) -> a.getNombre().compareToIgnoreCase(b.getNombre()))
                .collect(Collectors.toList());
    }

    /**
     * Agrega (o, si ya existía, actualiza el nivel de) una habilidad en el
     * perfil del colaborador que hace la petición - misma escala Básico(1)/
     * Intermedio(3)/Avanzado(4)/Experto(5) que auth/registro.html usa al
     * registrarse (ver AuthService.nivelDesdeTexto), reproducida aquí porque
     * ese método de AuthService es privado y este flujo corre fuera del
     * registro.
     */
    @Transactional
    public void agregarAlPerfil(Long perfilId, Long habilidadId, String nivelTexto, Long actorUsuarioId) {
        Habilidad h = habilidadRepository.findById(habilidadId)
                .orElseThrow(() -> new OperacionInvalidaException("Selecciona una habilidad válida del catálogo."));
        int nivel = nivelDesdeTexto(nivelTexto);
        PerfilHabilidadId id = new PerfilHabilidadId(perfilId, habilidadId);
        PerfilHabilidad ph = perfilHabilidadRepository.findById(id).orElse(null);
        boolean yaExistia = ph != null;
        if (ph == null) {
            ph = new PerfilHabilidad(perfilId, habilidadId, nivel);
        } else {
            ph.setNivel(nivel);
        }
        perfilHabilidadRepository.save(ph);
        auditoriaService.registrar(actorUsuarioId, yaExistia ? "PERFIL_HABILIDAD_ACTUALIZADA" : "PERFIL_HABILIDAD_AGREGADA",
                "perfil_habilidad", perfilId, null, null, h.getNombre() + " · " + nivelTexto);
    }

    private int nivelDesdeTexto(String nivelTexto) {
        if (nivelTexto == null) return 1;
        return switch (nivelTexto.trim().toLowerCase()) {
            case "básico", "basico" -> 1;
            case "intermedio" -> 3;
            case "avanzado" -> 4;
            case "experto" -> 5;
            default -> 1;
        };
    }

    private String validarNombre(String nombre) {
        if (nombre == null || nombre.trim().isEmpty()) {
            throw new OperacionInvalidaException("Escribe un nombre para la habilidad.");
        }
        String limpio = nombre.trim();
        if (limpio.length() > 80) {
            throw new OperacionInvalidaException("El nombre no puede superar los 80 caracteres.");
        }
        return limpio;
    }
}
