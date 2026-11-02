package com.skillbridge.ai.service;

import com.skillbridge.ai.dto.CursoRequest;
import com.skillbridge.ai.model.Curso;
import com.skillbridge.ai.repository.CategoriaHabilidadRepository;
import com.skillbridge.ai.repository.CursoRepository;
import com.skillbridge.ai.repository.HabilidadRepository;
import com.skillbridge.ai.util.OperacionInvalidaException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.net.URI;
import java.util.Set;

@Service
public class CursoService {
    private static final Set<String> NIVELES = Set.of("BASICO", "INTERMEDIO", "AVANZADO", "EXPERTO");
    private final CursoRepository cursos;
    private final CategoriaHabilidadRepository categorias;
    private final HabilidadRepository habilidades;
    private final AuditoriaService auditoria;

    public CursoService(CursoRepository cursos, CategoriaHabilidadRepository categorias,
                        HabilidadRepository habilidades, AuditoriaService auditoria) {
        this.cursos = cursos;
        this.categorias = categorias;
        this.habilidades = habilidades;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public Page<Curso> buscar(String q, String estado, Pageable pageable) {
        String filtro = Set.of("PUBLICADO", "BORRADOR", "ARCHIVADO").contains(estado) ? estado : "";
        return cursos.buscar(q == null ? "" : q.trim(), filtro, pageable);
    }

    @Transactional
    public void guardar(Long id, CursoRequest entrada, Long actorId) {
        if (!NIVELES.contains(entrada.getNivel())) throw new OperacionInvalidaException("Nivel inválido.");
        String url = entrada.getUrl() == null ? "" : entrada.getUrl().trim();
        if (!url.isEmpty()) {
            try {
                URI uri = URI.create(url);
                if (!Set.of("https", "http").contains(uri.getScheme()) || uri.getHost() == null)
                    throw new IllegalArgumentException();
            } catch (IllegalArgumentException ex) {
                throw new OperacionInvalidaException("La URL debe ser HTTP o HTTPS válida.");
            }
        }
        Curso curso = id == null ? new Curso() : cursos.findById(id)
                .orElseThrow(() -> new OperacionInvalidaException("Curso no encontrado."));
        curso.setTitulo(entrada.getTitulo().trim());
        curso.setDescripcion(entrada.getDescripcion() == null ? null : entrada.getDescripcion().trim());
        curso.setUrl(url.isEmpty() ? null : url);
        curso.setNivel(entrada.getNivel());
        curso.setDuracionHoras(entrada.getDuracionHoras());
        curso.setCategoria(entrada.getCategoriaId() == null ? null : categorias.findById(entrada.getCategoriaId())
                .orElseThrow(() -> new OperacionInvalidaException("Categoría no encontrada.")));
        curso.setHabilidad(entrada.getHabilidadId() == null ? null : habilidades.findById(entrada.getHabilidadId())
                .orElseThrow(() -> new OperacionInvalidaException("Habilidad no encontrada.")));
        if (curso.getHabilidad() != null && curso.getCategoria() != null &&
                !curso.getHabilidad().getCategoria().getId().equals(curso.getCategoria().getId()))
            throw new OperacionInvalidaException("La habilidad debe pertenecer a la categoría seleccionada.");
        cursos.save(curso);
        auditoria.registrar(actorId, id == null ? "CURSO_CREADO" : "CURSO_EDITADO", "curso", curso.getId(), null, null, curso.getTitulo());
    }

    @Transactional
    public void cambiarEstado(Long id, String estado, Long actorId) {
        if (!Set.of("PUBLICADO", "BORRADOR", "ARCHIVADO").contains(estado))
            throw new OperacionInvalidaException("Estado inválido.");
        Curso curso = cursos.findById(id).orElseThrow(() -> new OperacionInvalidaException("Curso no encontrado."));
        curso.setEstado(estado);
        cursos.save(curso);
        auditoria.registrar(actorId, "CURSO_ESTADO_CAMBIADO", "curso", id, null, null, estado);
    }
}
