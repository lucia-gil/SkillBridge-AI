package com.skillbridge.ai.service;

import com.skillbridge.ai.dto.CursoRequest;
import com.skillbridge.ai.dto.CursoVista;
import com.skillbridge.ai.model.Curso;
import com.skillbridge.ai.model.CursoPaso;
import com.skillbridge.ai.repository.CategoriaHabilidadRepository;
import com.skillbridge.ai.repository.CursoRepository;
import com.skillbridge.ai.repository.HabilidadRepository;
import com.skillbridge.ai.util.OperacionInvalidaException;
import com.skillbridge.ai.util.PlataformaCurso;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Catálogo de formación: cursos, videos, rutas (roadmaps) y especializaciones
 * que el Administrador publica y que ven Colaborador, Project Manager y
 * Resource Manager en su vista "Cursos".
 */
@Service
public class CursoService {

    public static final Set<String> NIVELES = Set.of("BASICO", "INTERMEDIO", "AVANZADO", "EXPERTO");
    public static final Set<String> TIPOS = Set.of("CURSO", "VIDEO", "RUTA", "ESPECIALIZACION");
    public static final Set<String> IDIOMAS = Set.of("ES", "EN", "PT");
    private static final Set<String> ESTADOS = Set.of("PUBLICADO", "BORRADOR", "ARCHIVADO");
    private static final int MAX_PASOS = 30;

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
        String filtro = ESTADOS.contains(estado) ? estado : "";
        return cursos.buscar(q == null ? "" : q.trim(), filtro, pageable);
    }

    /** Todo el catálogo (vista de administración). */
    @Transactional(readOnly = true)
    public List<CursoVista> catalogoCompleto() {
        return cursos.listarConRelaciones().stream().map(CursoService::aVista).toList();
    }

    /** Solo lo publicado (Colaborador / PM / RM). */
    @Transactional(readOnly = true)
    public List<CursoVista> catalogoPublicado() {
        return cursos.listarPublicados().stream().map(CursoService::aVista).toList();
    }

    @Transactional
    public void guardar(Long id, CursoRequest entrada, Long actorId) {
        if (!NIVELES.contains(entrada.getNivel())) throw new OperacionInvalidaException("Nivel inválido.");
        String tipo = entrada.getTipo() == null ? "CURSO" : entrada.getTipo().trim().toUpperCase();
        if (!TIPOS.contains(tipo)) throw new OperacionInvalidaException("Tipo de recurso inválido.");
        String idioma = entrada.getIdioma() == null ? "ES" : entrada.getIdioma().trim().toUpperCase();
        if (!IDIOMAS.contains(idioma)) throw new OperacionInvalidaException("Idioma inválido.");

        String url = validarUrl(entrada.getUrl(), "El enlace del recurso");
        boolean esRuta = "RUTA".equals(tipo) || "ESPECIALIZACION".equals(tipo);
        if (!esRuta && url == null) throw new OperacionInvalidaException("Agrega el enlace del recurso (YouTube, plataforma, documentación...).");

        String plataforma = null;
        if (entrada.getPlataforma() != null && !entrada.getPlataforma().isBlank()) {
            PlataformaCurso.Plataforma p = PlataformaCurso.porClave(entrada.getPlataforma());
            if (p == null) throw new OperacionInvalidaException("Plataforma inválida.");
            plataforma = p.clave();
        }

        List<CursoPaso> pasos = new ArrayList<>();
        if (esRuta) {
            List<String> titulos = entrada.getPasoTitulo() == null ? List.of() : entrada.getPasoTitulo();
            List<String> urls = entrada.getPasoUrl() == null ? List.of() : entrada.getPasoUrl();
            for (int i = 0; i < titulos.size(); i++) {
                String t = titulos.get(i) == null ? "" : titulos.get(i).trim();
                String u = i < urls.size() ? urls.get(i) : null;
                if (t.isEmpty() && (u == null || u.isBlank())) continue;   // fila vacía
                if (t.isEmpty()) throw new OperacionInvalidaException("Cada paso necesita un título (paso " + (pasos.size() + 1) + ").");
                if (t.length() > 150) throw new OperacionInvalidaException("El título de un paso no puede superar 150 caracteres.");
                pasos.add(new CursoPaso(t, validarUrl(u, "El enlace del paso " + (pasos.size() + 1))));
            }
            if (pasos.isEmpty()) throw new OperacionInvalidaException("Una ruta o especialización necesita al menos un paso.");
            if (pasos.size() > MAX_PASOS) throw new OperacionInvalidaException("Máximo " + MAX_PASOS + " pasos por ruta.");
        }

        Curso curso = id == null ? new Curso() : cursos.findById(id)
                .orElseThrow(() -> new OperacionInvalidaException("Curso no encontrado."));
        curso.setTitulo(entrada.getTitulo().trim());
        curso.setDescripcion(entrada.getDescripcion() == null || entrada.getDescripcion().isBlank() ? null : entrada.getDescripcion().trim());
        curso.setUrl(url);
        curso.setNivel(entrada.getNivel());
        curso.setDuracionHoras(entrada.getDuracionHoras());
        curso.setTipo(tipo);
        curso.setPlataforma(plataforma);
        curso.setGratuito(entrada.isGratuito());
        curso.setConCertificado(entrada.isConCertificado());
        curso.setIdioma(idioma);
        curso.setDestacado(entrada.isDestacado());
        curso.getPasos().clear();
        curso.getPasos().addAll(pasos);
        curso.setCategoria(entrada.getCategoriaId() == null ? null : categorias.findById(entrada.getCategoriaId())
                .orElseThrow(() -> new OperacionInvalidaException("Categoría no encontrada.")));
        curso.setHabilidad(entrada.getHabilidadId() == null ? null : habilidades.findById(entrada.getHabilidadId())
                .orElseThrow(() -> new OperacionInvalidaException("Habilidad no encontrada.")));
        if (curso.getHabilidad() != null && curso.getCategoria() != null &&
                !curso.getHabilidad().getCategoria().getId().equals(curso.getCategoria().getId()))
            throw new OperacionInvalidaException("La habilidad debe pertenecer a la categoría seleccionada.");
        // Si eligió una habilidad pero no la categoría, se completa sola.
        if (curso.getHabilidad() != null && curso.getCategoria() == null) curso.setCategoria(curso.getHabilidad().getCategoria());
        cursos.save(curso);
        auditoria.registrar(actorId, id == null ? "CURSO_CREADO" : "CURSO_EDITADO", "curso", curso.getId(), null, null, curso.getTitulo());
    }

    @Transactional
    public void cambiarEstado(Long id, String estado, Long actorId) {
        if (!ESTADOS.contains(estado)) throw new OperacionInvalidaException("Estado inválido.");
        Curso curso = cursos.findById(id).orElseThrow(() -> new OperacionInvalidaException("Curso no encontrado."));
        curso.setEstado(estado);
        cursos.save(curso);
        auditoria.registrar(actorId, "CURSO_ESTADO_CAMBIADO", "curso", id, null, null, estado);
    }

    /** Marca / desmarca un recurso como destacado (aparece primero en el catálogo). */
    @Transactional
    public boolean alternarDestacado(Long id, Long actorId) {
        Curso curso = cursos.findById(id).orElseThrow(() -> new OperacionInvalidaException("Curso no encontrado."));
        curso.setDestacado(!curso.isDestacado());
        cursos.save(curso);
        auditoria.registrar(actorId, "CURSO_DESTACADO", "curso", id, null, null,
                (curso.isDestacado() ? "Destacado: " : "Ya no destacado: ") + curso.getTitulo());
        return curso.isDestacado();
    }

    // ─────────────────────────── helpers ───────────────────────────

    /** null si viene vacía; error si no es http(s) válida. */
    private static String validarUrl(String valor, String etiqueta) {
        String url = valor == null ? "" : valor.trim();
        if (url.isEmpty()) return null;
        if (url.length() > 500) throw new OperacionInvalidaException(etiqueta + " no puede superar 500 caracteres.");
        try {
            URI uri = URI.create(url);
            if (!Set.of("https", "http").contains(uri.getScheme()) || uri.getHost() == null)
                throw new IllegalArgumentException();
        } catch (IllegalArgumentException ex) {
            throw new OperacionInvalidaException(etiqueta + " debe ser una URL HTTP o HTTPS válida.");
        }
        return url;
    }

    static CursoVista aVista(Curso c) {
        PlataformaCurso.Plataforma elegida = PlataformaCurso.porClave(c.getPlataforma());
        PlataformaCurso.Plataforma plataforma = elegida != null ? elegida : PlataformaCurso.detectar(c.getUrl());
        List<CursoVista.Paso> pasos = c.getPasos().stream()
                .map(p -> new CursoVista.Paso(p.getTitulo(), p.getUrl(),
                        PlataformaCurso.detectar(p.getUrl()).clave(), PlataformaCurso.youtubeId(p.getUrl())))
                .toList();
        // Ruta sin enlace propio: toma la plataforma de su primer paso para el logo.
        if (elegida == null && c.getUrl() == null && !pasos.isEmpty()) plataforma = PlataformaCurso.porClave(pasos.get(0).plataforma());
        long creado = c.getFechaCreacion() == null ? 0 : c.getFechaCreacion().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        return new CursoVista(c.getId(), c.getTitulo(), c.getDescripcion(), c.getUrl(), c.getTipo(),
                plataforma.clave(), plataforma.nombre(), elegida != null, PlataformaCurso.youtubeId(c.getUrl()),
                c.getCategoria() != null ? c.getCategoria().getId() : null,
                c.getCategoria() != null ? c.getCategoria().getNombre() : null,
                c.getHabilidad() != null ? c.getHabilidad().getId() : null,
                c.getHabilidad() != null ? c.getHabilidad().getNombre() : null,
                c.getNivel(), c.getDuracionHoras(), c.isGratuito(), c.isConCertificado(), c.getIdioma(),
                c.isDestacado(), c.getEstado(), creado, pasos);
    }
}
