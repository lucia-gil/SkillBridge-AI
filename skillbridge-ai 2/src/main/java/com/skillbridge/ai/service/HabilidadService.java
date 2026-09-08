package com.skillbridge.ai.service;

import com.skillbridge.ai.dto.CategoriaConConteo;
import com.skillbridge.ai.dto.HabilidadFila;
import com.skillbridge.ai.model.CategoriaHabilidad;
import com.skillbridge.ai.model.Habilidad;
import com.skillbridge.ai.repository.CategoriaHabilidadRepository;
import com.skillbridge.ai.repository.HabilidadRepository;
import com.skillbridge.ai.repository.PerfilHabilidadRepository;
import com.skillbridge.ai.repository.ProyectoHabilidadRequeridaRepository;
import com.skillbridge.ai.util.OperacionInvalidaException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
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
