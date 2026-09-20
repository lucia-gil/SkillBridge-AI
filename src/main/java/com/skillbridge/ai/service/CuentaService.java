package com.skillbridge.ai.service;

import com.skillbridge.ai.dto.CuentaResumen;
import com.skillbridge.ai.model.Perfil;
import com.skillbridge.ai.model.Usuario;
import com.skillbridge.ai.repository.PerfilRepository;
import com.skillbridge.ai.repository.UsuarioRepository;
import com.skillbridge.ai.util.OperacionInvalidaException;
import com.skillbridge.ai.util.PasswordPolicy;
import com.skillbridge.ai.util.Roles;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * "Mi cuenta" (administrador/mi-cuenta.html y colaborador/mi-cuenta.html).
 *
 * Gestiona los datos reales de la cuenta:
 * - Datos profesionales del perfil.
 * - Contraseña.
 * - Fotografía de perfil.
 */
@Service
public class CuentaService {

    private static final DateTimeFormatter FORMATO_MES_ANIO =
            DateTimeFormatter.ofPattern(
                    "MMMM yyyy",
                    new Locale("es", "ES")
            );

    /**
     * Tamaño máximo permitido para una fotografía.
     * 5 MB.
     */
    private static final long MAX_FOTO_BYTES = 5L * 1024L * 1024L;

    private final PerfilRepository perfilRepository;
    private final UsuarioRepository usuarioRepository;
    private final AuthService authService;
    private final AuditoriaService auditoriaService;

    public CuentaService(
            PerfilRepository perfilRepository,
            UsuarioRepository usuarioRepository,
            AuthService authService,
            AuditoriaService auditoriaService) {

        this.perfilRepository = perfilRepository;
        this.usuarioRepository = usuarioRepository;
        this.authService = authService;
        this.auditoriaService = auditoriaService;
    }

    /**
     * Garantiza que el usuario tenga una fila en "perfiles" y devuelve su id.
     *
     * Todo usuario DEBERÍA tener una: el auto-registro (AuthService.registrar())
     * crea usuario+perfil juntos, siempre. Pero si por algún motivo externo a
     * ese flujo (datos cargados a mano, un seed viejo con un id fijo mal
     * resuelto) un usuario quedó sin perfil, "Mi cuenta" quedaba rota para
     * siempre con un 500 ("Tu perfil ya no existe") - en vez de eso, se crea
     * uno mínimo aquí la primera vez que hace falta.
     */
    @Transactional
    public Long asegurarPerfil(Long usuarioId) {
        return perfilRepository.findByUsuarioId(usuarioId)
                .map(Perfil::getId)
                .orElseGet(() -> {
                    Perfil perfil = new Perfil();
                    perfil.setUsuarioId(usuarioId);
                    perfil.setDisponibilidadPorcentaje(100);
                    perfil.setExperienciaAnios(0);
                    perfil.setEstado("activo");
                    return perfilRepository.save(perfil).getId();
                });
    }

    /** rolEfectivo se recibe de la sesión. */
    public CuentaResumen obtenerResumen(
            Long perfilId,
            String rolEfectivo) {

        Perfil perfil = perfilRepository.buscarConUsuario(perfilId)
                .orElseThrow(() ->
                        new OperacionInvalidaException(
                                "Tu perfil ya no existe."
                        )
                );

        Usuario usuario = perfil.getUsuario();

        String cuentaDesde = usuario.getFechaCreacion() != null
                ? usuario.getFechaCreacion().format(FORMATO_MES_ANIO)
                : "";

        return new CuentaResumen(
                usuario.getNombreCompleto(),
                usuario.getCorreo(),
                perfil.getCargo(),
                perfil.getBiografia(),
                perfil.getExperienciaAnios() != null
                        ? perfil.getExperienciaAnios()
                        : 0,
                perfil.getDisponibilidadPorcentaje() != null
                        ? perfil.getDisponibilidadPorcentaje()
                        : 0,
                Roles.etiqueta(rolEfectivo),
                cuentaDesde
        );
    }

    @Transactional
    public void actualizarDatos(
            Long perfilId,
            String cargo,
            String biografia,
            int experienciaAnios,
            Long actorUsuarioId) {

        Perfil perfil = perfilRepository.findById(perfilId)
                .orElseThrow(() ->
                        new OperacionInvalidaException(
                                "Tu perfil ya no existe."
                        )
                );

        if (experienciaAnios < 0 || experienciaAnios > 60) {
            throw new OperacionInvalidaException(
                    "Los años de experiencia deben ser un valor entre 0 y 60."
            );
        }

        String cargoAnterior = perfil.getCargo();

        perfil.setCargo(
                cargo != null
                        ? cargo.trim()
                        : null
        );

        perfil.setBiografia(
                biografia != null
                        ? biografia.trim()
                        : null
        );

        perfil.setExperienciaAnios(experienciaAnios);

        perfilRepository.save(perfil);

        auditoriaService.registrar(
                actorUsuarioId,
                "CUENTA_ACTUALIZADA",
                "perfil",
                perfilId,
                AuditoriaService.json(
                        "cargo",
                        cargoAnterior
                ),
                AuditoriaService.json(
                        "cargo",
                        perfil.getCargo()
                ),
                "El usuario actualizó los datos de su cuenta."
        );
    }

    @Transactional
    public void cambiarContrasena(
            Long usuarioId,
            String actual,
            String nueva,
            String nueva2,
            Long actorUsuarioId) {

        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() ->
                        new OperacionInvalidaException(
                                "Tu usuario ya no existe."
                        )
                );

        BCryptPasswordEncoder encoder =
                authService.passwordEncoder();

        if (actual == null
                || actual.isEmpty()
                || !encoder.matches(
                actual,
                usuario.getContrasenaHash())) {

            throw new OperacionInvalidaException(
                    "Tu contraseña actual no es correcta."
            );
        }

        String errorPwd = PasswordPolicy.validar(nueva);

        if (errorPwd != null) {
            throw new OperacionInvalidaException(errorPwd);
        }

        if (nueva2 == null
                || !nueva.equals(nueva2)) {

            throw new OperacionInvalidaException(
                    "Las contraseñas nuevas no coinciden."
            );
        }

        if (encoder.matches(
                nueva,
                usuario.getContrasenaHash())) {

            throw new OperacionInvalidaException(
                    "La nueva contraseña debe ser distinta de la actual."
            );
        }

        usuario.setContrasenaHash(
                encoder.encode(nueva)
        );

        usuarioRepository.save(usuario);

        auditoriaService.registrar(
                actorUsuarioId,
                "CONTRASENA_CAMBIADA",
                "usuario",
                usuarioId,
                null,
                null,
                "El usuario cambió su propia contraseña desde Mi cuenta."
        );
    }

    /**
     * Guarda una nueva fotografia de perfil.
     *
     * Formatos aceptados:
     * - JPG / JPEG
     * - PNG
     *
     * PDF no se acepta porque no es un formato de imagen de perfil.
     */
    @Transactional
    public void actualizarFoto(
            Long usuarioId,
            MultipartFile archivo) {

        if (archivo == null || archivo.isEmpty()) {
            throw new OperacionInvalidaException(
                    "Selecciona una fotografía antes de guardarla."
            );
        }

        if (archivo.getSize() > MAX_FOTO_BYTES) {
            throw new OperacionInvalidaException(
                    "La fotografía no puede superar los 5 MB."
            );
        }

        String contentType = archivo.getContentType();

        if (!esTipoImagenPermitido(contentType)) {
            throw new OperacionInvalidaException(
                    "Formato no permitido. Usa una imagen JPG, JPEG o PNG."
            );
        }

        /*
         * Además del MIME declarado por el navegador,
         * intentamos leer realmente la imagen.
         * Esto evita aceptar un archivo cualquiera renombrado como .jpg.
         */
        try {
            BufferedImage imagen =
                    ImageIO.read(archivo.getInputStream());

            if (imagen == null) {
                throw new OperacionInvalidaException(
                        "El archivo seleccionado no es una imagen válida."
                );
            }

        } catch (IOException ex) {
            throw new OperacionInvalidaException(
                    "No se pudo leer la fotografía seleccionada."
            );
        }

        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() ->
                        new OperacionInvalidaException(
                                "Tu usuario ya no existe."
                        )
                );

        try {
            usuario.setFotoPerfil(archivo.getBytes());
        } catch (IOException ex) {
            throw new OperacionInvalidaException(
                    "No se pudo procesar la fotografía."
            );
        }

        usuario.setFotoPerfilTipo(contentType);
        usuario.setFotoPerfilNombre(
                archivo.getOriginalFilename()
        );

        usuarioRepository.save(usuario);

        auditoriaService.registrar(
                usuarioId,
                "FOTO_PERFIL_ACTUALIZADA",
                "usuario",
                usuarioId,
                null,
                null,
                "El usuario actualizó su fotografía de perfil."
        );
    }

    /**
     * Elimina la fotografía de perfil actual.
     */
    @Transactional
    public void eliminarFoto(Long usuarioId) {

        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() ->
                        new OperacionInvalidaException(
                                "Tu usuario ya no existe."
                        )
                );

        usuario.setFotoPerfil(null);
        usuario.setFotoPerfilTipo(null);
        usuario.setFotoPerfilNombre(null);

        usuarioRepository.save(usuario);

        auditoriaService.registrar(
                usuarioId,
                "FOTO_PERFIL_ELIMINADA",
                "usuario",
                usuarioId,
                null,
                null,
                "El usuario eliminó su fotografía de perfil."
        );
    }

    /**
     * Devuelve la fotografia almacenada.
     */
    @Transactional(readOnly = true)
    public Usuario obtenerFoto(Long usuarioId) {

        return usuarioRepository.findById(usuarioId)
                .orElseThrow(() ->
                        new OperacionInvalidaException(
                                "Tu usuario no existe."
                        )
                );
    }

    private boolean esTipoImagenPermitido(String contentType) {

        return "image/jpeg".equalsIgnoreCase(contentType)
                || "image/jpg".equalsIgnoreCase(contentType)
                || "image/png".equalsIgnoreCase(contentType);
    }
}