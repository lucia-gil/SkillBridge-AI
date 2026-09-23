package com.skillbridge.ai.service;

import com.skillbridge.ai.dto.CategoriaConConteo;
import com.skillbridge.ai.dto.CertificadoFila;
import com.skillbridge.ai.dto.HabilidadFila;
import com.skillbridge.ai.dto.HabilidadPerfilFila;
import com.skillbridge.ai.model.CategoriaHabilidad;
import com.skillbridge.ai.model.CertificadoHabilidad;
import com.skillbridge.ai.model.Habilidad;
import com.skillbridge.ai.model.PerfilHabilidad;
import com.skillbridge.ai.model.PerfilHabilidadId;
import com.skillbridge.ai.repository.CategoriaHabilidadRepository;
import com.skillbridge.ai.repository.CertificadoHabilidadRepository;
import com.skillbridge.ai.repository.HabilidadRepository;
import com.skillbridge.ai.repository.PerfilHabilidadRepository;
import com.skillbridge.ai.repository.ProyectoHabilidadRequeridaRepository;
import com.skillbridge.ai.util.OperacionInvalidaException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
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
    private final CertificadoHabilidadRepository certificadoHabilidadRepository;
    private final AuditoriaService auditoriaService;

    /** Mismo límite que spring.servlet.multipart.max-file-size (5 MB). */
    private static final long MAX_CERTIFICADO_BYTES = 5L * 1024 * 1024;

    public HabilidadService(HabilidadRepository habilidadRepository,
                            CategoriaHabilidadRepository categoriaHabilidadRepository,
                            PerfilHabilidadRepository perfilHabilidadRepository,
                            ProyectoHabilidadRequeridaRepository proyectoHabilidadRequeridaRepository,
                            CertificadoHabilidadRepository certificadoHabilidadRepository,
                            AuditoriaService auditoriaService) {
        this.habilidadRepository = habilidadRepository;
        this.categoriaHabilidadRepository = categoriaHabilidadRepository;
        this.perfilHabilidadRepository = perfilHabilidadRepository;
        this.proyectoHabilidadRequeridaRepository = proyectoHabilidadRequeridaRepository;
        this.certificadoHabilidadRepository = certificadoHabilidadRepository;
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

    // ─────────────── CRUD de Categorías de habilidad (administrador/habilidades.html) ───────────────

    @Transactional
    public void crearCategoria(String nombre, String descripcion, Long actorId) {
        String limpio = validarNombreCategoria(nombre);
        if (categoriaHabilidadRepository.existsByNombreIgnoreCase(limpio)) {
            throw new OperacionInvalidaException("Ya existe una categoría con ese nombre.");
        }
        CategoriaHabilidad c = new CategoriaHabilidad();
        c.setNombre(limpio);
        c.setDescripcion(descripcion != null && !descripcion.isBlank() ? descripcion.trim() : null);
        c = categoriaHabilidadRepository.save(c);

        auditoriaService.registrar(actorId, "CATEGORIA_HABILIDAD_CREADA", "categoria_habilidad", c.getId(), null, null, limpio);
    }

    @Transactional
    public void editarCategoria(Long id, String nombre, String descripcion, Long actorId) {
        String limpio = validarNombreCategoria(nombre);
        CategoriaHabilidad c = categoriaHabilidadRepository.findById(id)
                .orElseThrow(() -> new OperacionInvalidaException("La categoría ya no existe."));

        if (!c.getNombre().equalsIgnoreCase(limpio) && categoriaHabilidadRepository.existsByNombreIgnoreCase(limpio)) {
            throw new OperacionInvalidaException("Ya existe otra categoría con ese nombre.");
        }
        String anterior = c.getNombre();
        c.setNombre(limpio);
        c.setDescripcion(descripcion != null && !descripcion.isBlank() ? descripcion.trim() : null);
        categoriaHabilidadRepository.save(c);

        auditoriaService.registrar(actorId, "CATEGORIA_HABILIDAD_EDITADA", "categoria_habilidad", id,
                AuditoriaService.json("nombre", anterior), AuditoriaService.json("nombre", limpio), null);
    }

    @Transactional
    public void eliminarCategoria(Long id, Long actorId) {
        CategoriaHabilidad c = categoriaHabilidadRepository.findById(id)
                .orElseThrow(() -> new OperacionInvalidaException("La categoría ya no existe."));
        long enUso = habilidadRepository.countByCategoriaId(id);
        if (enUso > 0) {
            throw new OperacionInvalidaException(
                    "No se puede eliminar \"" + c.getNombre() + "\": tiene " + enUso + " habilidad(es) asignada(s).");
        }
        String nombre = c.getNombre();
        categoriaHabilidadRepository.delete(c);
        auditoriaService.registrar(actorId, "CATEGORIA_HABILIDAD_ELIMINADA", "categoria_habilidad", id, null, null, nombre);
    }

    private String validarNombreCategoria(String nombre) {
        if (nombre == null || nombre.trim().isEmpty()) {
            throw new OperacionInvalidaException("Escribe un nombre para la categoría.");
        }
        String limpio = nombre.trim();
        if (limpio.length() > 60) {
            throw new OperacionInvalidaException("El nombre no puede superar los 60 caracteres.");
        }
        return limpio;
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
                    boolean validada = ph.getValidadoPorId() != null;
                    List<CertificadoFila> certificados = certificadoHabilidadRepository
                            .findByPerfilIdAndHabilidadId(perfilId, h.getId()).stream()
                            .map(c -> new CertificadoFila(c.getId(), c.getNombreArchivo(), c.getUrlArchivo(), c.tieneArchivo()))
                            .collect(Collectors.toList());
                    return new HabilidadPerfilFila(h.getId(), h.getNombre(), h.getCategoria().getNombre(), ph.getNivel(), desde, validada, certificados);
                })
                .filter(java.util.Objects::nonNull)
                .sorted((a, b) -> a.getNombre().compareToIgnoreCase(b.getNombre()))
                .collect(Collectors.toList());
    }

    /**
     * Agrega, reemplaza o quita la constancia de UNA habilidad ya
     * declarada. Como es "como máximo un certificado por habilidad"
     * (Opción A), primero se borra el que hubiera antes de guardar el
     * nuevo - así "editar" y "quitar" (dejando urlArchivo vacío) usan el
     * mismo método, sin necesitar un endpoint de DELETE aparte.
     */
    @Transactional
    public void actualizarCertificado(Long perfilId, Long habilidadId, String nombreArchivo, String urlArchivo,
                                      MultipartFile archivo, Long actorUsuarioId) {
        PerfilHabilidadId id = new PerfilHabilidadId(perfilId, habilidadId);
        if (!perfilHabilidadRepository.existsById(id)) {
            throw new OperacionInvalidaException("Esa habilidad ya no está declarada en tu perfil.");
        }
        certificadoHabilidadRepository.deleteByPerfilIdAndHabilidadId(perfilId, habilidadId);
        CertificadoHabilidad cert = construirCertificadoSiCorresponde(perfilId, habilidadId, nombreArchivo, urlArchivo, archivo);
        if (cert != null) {
            certificadoHabilidadRepository.save(cert);
        }
        auditoriaService.registrar(actorUsuarioId, "CERTIFICADO_ACTUALIZADO", "perfil_habilidad", perfilId, null, null,
                "Constancia actualizada para habilidad " + habilidadId + ".");
    }

    /**
     * Agrega (o, si ya existía, actualiza el nivel de) una habilidad en el
     * perfil del colaborador que hace la petición - misma escala Básico(1)/
     * Intermedio(3)/Avanzado(4)/Experto(5) que auth/registro.html usa al
     * registrarse (ver AuthService.nivelDesdeTexto), reproducida aquí porque
     * ese método de AuthService es privado y este flujo corre fuera del
     * registro.
     *
     * nombreArchivo/urlArchivo son OPCIONALES (Opción A: link externo, no
     * subida de archivo real - ver certificados_habilidad). Si el
     * colaborador los deja vacíos, la habilidad igual se guarda, solo que
     * sin constancia de respaldo (el Resource Manager la verá marcada como
     * "sin constancia adjunta" al momento de validarla).
     */
    @Transactional
    public void agregarAlPerfil(Long perfilId, Long habilidadId, String nivelTexto, Long actorUsuarioId,
                                String nombreArchivo, String urlArchivo, MultipartFile archivo) {
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

        CertificadoHabilidad cert = construirCertificadoSiCorresponde(perfilId, habilidadId, nombreArchivo, urlArchivo, archivo);
        if (cert != null) {
            certificadoHabilidadRepository.deleteByPerfilIdAndHabilidadId(perfilId, habilidadId);
            certificadoHabilidadRepository.save(cert);
        }

        auditoriaService.registrar(actorUsuarioId, yaExistia ? "PERFIL_HABILIDAD_ACTUALIZADA" : "PERFIL_HABILIDAD_AGREGADA",
                "perfil_habilidad", perfilId, null, null, h.getNombre() + " · " + nivelTexto);
    }

    /**
     * Construye el CertificadoHabilidad a guardar a partir de lo que mandó
     * el formulario: si viene un archivo real (PDF), tiene prioridad sobre
     * el link. Si no viene ni archivo ni link, no hay nada que guardar
     * (devuelve null - la habilidad queda "sin constancia adjunta").
     */
    private CertificadoHabilidad construirCertificadoSiCorresponde(Long perfilId, Long habilidadId,
                                                                   String nombreArchivo, String urlArchivo,
                                                                   MultipartFile archivo) {
        boolean hayArchivo = archivo != null && !archivo.isEmpty();
        boolean hayLink = urlArchivo != null && !urlArchivo.isBlank();
        if (!hayArchivo && !hayLink) {
            return null;
        }

        CertificadoHabilidad cert = new CertificadoHabilidad();
        cert.setPerfilId(perfilId);
        cert.setHabilidadId(habilidadId);

        if (hayArchivo) {
            validarPdf(archivo);
            try {
                cert.setContenidoArchivo(archivo.getBytes());
            } catch (IOException ex) {
                throw new OperacionInvalidaException("No se pudo procesar el archivo del certificado.");
            }
            cert.setTipoArchivo("application/pdf");
            String nombreOriginal = archivo.getOriginalFilename();
            cert.setNombreArchivo(nombreArchivo != null && !nombreArchivo.isBlank() ? nombreArchivo
                    : (nombreOriginal != null && !nombreOriginal.isBlank() ? nombreOriginal : "certificado.pdf"));
        } else {
            cert.setUrlArchivo(urlArchivo);
            cert.setNombreArchivo(nombreArchivo != null && !nombreArchivo.isBlank() ? nombreArchivo : "constancia");
        }
        return cert;
    }

    private void validarPdf(MultipartFile archivo) {
        if (archivo.getSize() > MAX_CERTIFICADO_BYTES) {
            throw new OperacionInvalidaException("El certificado no puede superar los 5 MB.");
        }
        String contentType = archivo.getContentType();
        if (contentType == null || !contentType.equalsIgnoreCase("application/pdf")) {
            throw new OperacionInvalidaException("El certificado debe ser un archivo PDF.");
        }
        try {
            byte[] cabecera = archivo.getInputStream().readNBytes(5);
            String firma = new String(cabecera, java.nio.charset.StandardCharsets.US_ASCII);
            if (!firma.startsWith("%PDF-")) {
                throw new OperacionInvalidaException("El archivo seleccionado no es un PDF válido.");
            }
        } catch (IOException ex) {
            throw new OperacionInvalidaException("No se pudo leer el certificado seleccionado.");
        }
    }

    /**
     * Igual que misHabilidades(), pero incluye si cada habilidad ya fue
     * validada - lo usa resource-manager/colaboradores.html para saber a
     * cuáles ponerles el botón "Validar" y a cuáles no.
     */
    public List<HabilidadPerfilFila> habilidadesConValidacion(Long perfilId) {
        Map<Long, Habilidad> catalogo = habilidadRepository.findAllConCategoriaOrderByNombre().stream()
                .collect(Collectors.toMap(Habilidad::getId, h -> h));
        return perfilHabilidadRepository.findById_PerfilId(perfilId).stream()
                .map(ph -> {
                    Habilidad h = catalogo.get(ph.getId().getHabilidadId());
                    if (h == null) return null;
                    String desde = ph.getFechaDeclaracion() != null ? ph.getFechaDeclaracion().format(FORMATO_FECHA) : "—";
                    boolean validada = ph.getValidadoPorId() != null;
                    List<CertificadoFila> certificados = certificadoHabilidadRepository
                            .findByPerfilIdAndHabilidadId(perfilId, h.getId()).stream()
                            .map(c -> new CertificadoFila(c.getId(), c.getNombreArchivo(), c.getUrlArchivo(), c.tieneArchivo()))
                            .collect(Collectors.toList());
                    return new HabilidadPerfilFila(h.getId(), h.getNombre(), h.getCategoria().getNombre(), ph.getNivel(), desde, validada, certificados);
                })
                .filter(java.util.Objects::nonNull)
                .sorted((a, b) -> a.getNombre().compareToIgnoreCase(b.getNombre()))
                .collect(Collectors.toList());
    }

    /**
     * Edita SOLO el nivel de una habilidad ya declarada por el propio
     * colaborador (completa el CRUD de "Mi perfil": crear ya existía,
     * faltaba editar y eliminar). Si esa habilidad ya estaba validada por
     * un Resource Manager, la validación queda sin efecto: el RM aprobó un
     * nivel puntual, y ese nivel ya no es el que el colaborador declara -
     * debe volver a revisarla.
     */
    @Transactional
    public void editarNivelDePerfil(Long perfilId, Long habilidadId, String nivelTexto, Long actorUsuarioId) {
        PerfilHabilidadId id = new PerfilHabilidadId(perfilId, habilidadId);
        PerfilHabilidad ph = perfilHabilidadRepository.findById(id)
                .orElseThrow(() -> new OperacionInvalidaException("Esa habilidad ya no está declarada en tu perfil."));
        int nivelAnterior = ph.getNivel() != null ? ph.getNivel() : 0;
        int nivel = nivelDesdeTexto(nivelTexto);
        ph.setNivel(nivel);
        if (nivel != nivelAnterior) {
            ph.setValidadoPorId(null);
        }
        perfilHabilidadRepository.save(ph);

        String nombreHabilidad = habilidadRepository.findById(habilidadId).map(Habilidad::getNombre).orElse("habilidad " + habilidadId);
        auditoriaService.registrar(actorUsuarioId, "PERFIL_HABILIDAD_NIVEL_EDITADO", "perfil_habilidad", perfilId, null, null,
                nombreHabilidad + " · nuevo nivel " + nivelTexto);
    }

    /**
     * Quita una habilidad declarada del perfil del colaborador. El
     * certificado que tuviera se borra en cascada (FK ON DELETE CASCADE
     * de certificados_habilidad hacia perfil_habilidad), sin necesidad de
     * borrarlo aparte aquí.
     */
    @Transactional
    public void eliminarDePerfil(Long perfilId, Long habilidadId, Long actorUsuarioId) {
        PerfilHabilidadId id = new PerfilHabilidadId(perfilId, habilidadId);
        PerfilHabilidad ph = perfilHabilidadRepository.findById(id)
                .orElseThrow(() -> new OperacionInvalidaException("Esa habilidad ya no está declarada en tu perfil."));
        String nombreHabilidad = habilidadRepository.findById(habilidadId).map(Habilidad::getNombre).orElse("habilidad " + habilidadId);
        perfilHabilidadRepository.delete(ph);
        auditoriaService.registrar(actorUsuarioId, "PERFIL_HABILIDAD_ELIMINADA", "perfil_habilidad", perfilId, null, null,
                nombreHabilidad + " eliminada del perfil.");
    }

    /**
     * RF02: "nivel autodeclarado, editable/aprobable por el Resource
     * Manager". Marcar validado_por_id es lo único que hace falta - el
     * nivel en sí lo declara y ajusta el propio colaborador, el RM solo
     * confirma que le parece correcto.
     */
    @Transactional
    public void validarHabilidadDeColaborador(Long perfilId, Long habilidadId, Long actorPerfilId) {
        PerfilHabilidadId id = new PerfilHabilidadId(perfilId, habilidadId);
        PerfilHabilidad ph = perfilHabilidadRepository.findById(id)
                .orElseThrow(() -> new OperacionInvalidaException("Esa habilidad ya no está declarada en el perfil."));
        ph.setValidadoPorId(actorPerfilId);
        perfilHabilidadRepository.save(ph);
        auditoriaService.registrar(actorPerfilId, "HABILIDAD_VALIDADA", "perfil_habilidad", perfilId, null, null,
                "Habilidad validada para el perfil " + perfilId + ".");
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