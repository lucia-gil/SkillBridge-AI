package com.skillbridge.ai.service;

import com.skillbridge.ai.model.Usuario;
import com.skillbridge.ai.model.CorreoAutorizado;
import com.skillbridge.ai.util.OperacionInvalidaException;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.Base64;
import java.util.Collection;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class IdentidadDniService {

    public static final String ERROR_DISPONIBILIDAD = "La consulta de DNI no está disponible en este momento.";
    public static final String ERROR_VINCULO = "Ese DNI ya está asociado a una cuenta o invitación.";
    private static final String ERROR_REGISTRO = "Ese DNI no corresponde a esta invitación o ya está vinculado. Consulta al administrador.";
    private static final String SELECT = """
            SELECT huella, usuario_id, invitacion_id, terminacion, verificado_en FROM identidad_dni
            """;
    private static final RowMapper<Vinculo> VINCULO = (row, i) -> new Vinculo(
            row.getBytes("huella"), row.getObject("usuario_id", Long.class), row.getObject("invitacion_id", Long.class),
            row.getString("terminacion"), row.getObject("verificado_en", LocalDateTime.class));

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate listaJdbc;
    private final ConsultaPeruDniService proveedor;
    private final AuditoriaService auditoria;
    private final SecretKeySpec clave;
    private final Map<String, EntradaCache> cache = new LinkedHashMap<>();

    private record EntradaCache(ConsultaPeruDniService.Persona persona, long expira) {}

    private static final int MAX_CACHE = 256;
    private static final long TTL_CACHE = Duration.ofMinutes(2).toMillis();
    private static final long VIGENCIA_INVITACION_DIAS = 7;

    public record Estado(String dniEnmascarado, LocalDateTime fecha) {}
    public record Consulta(String nombres, String apellidos, String estado) {}

    private record Vinculo(byte[] huella, Long usuarioId, Long invitacionId, String terminacion, LocalDateTime fecha) {}

    public IdentidadDniService(JdbcTemplate jdbc, ConsultaPeruDniService proveedor, AuditoriaService auditoria,
                              @Value("${app.registro.identidad-clave:}") String claveBase64) {
        this.jdbc = jdbc;
        this.listaJdbc = new NamedParameterJdbcTemplate(jdbc);
        this.proveedor = proveedor;
        this.auditoria = auditoria;
        this.clave = habilitarClave(cargarClave(claveBase64));
    }

    public boolean disponible() {
        return clave != null && proveedor.disponible();
    }

    public Optional<String> firmarInvitacion(CorreoAutorizado invitacion) {
        if (clave == null || invitacion.getId() == null || invitacion.getUltimoEnvio() == null) return Optional.empty();
        return Optional.of(Base64.getUrlEncoder().withoutPadding().encodeToString(macInvitacion(invitacion)));
    }

    public boolean firmaValida(CorreoAutorizado invitacion, String firma) {
        if (clave == null || firma == null || firma.length() != 43 || invitacion.getUltimoEnvio() == null
                || Boolean.TRUE.equals(invitacion.getUtilizado())
                || !LocalDateTime.now().isBefore(invitacion.getUltimoEnvio().plusDays(VIGENCIA_INVITACION_DIAS))) return false;
        try {
            return MessageDigest.isEqual(macInvitacion(invitacion), Base64.getUrlDecoder().decode(firma));
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private byte[] macInvitacion(CorreoAutorizado invitacion) {
        return mac("invitacion:" + invitacion.getId() + ":" + invitacion.getUltimoEnvio() + ":" + invitacion.getCorreo());
    }

    public Consulta consultar(String dni) {
        byte[] huella = huellaValida(dni);
        var persona = buscarPersona(dni, huella, false);
        String estado = porHuella(huella).map(v -> v.usuarioId() != null ? "vinculado" : "reservado")
                .orElse("disponible");
        return new Consulta(persona.nombres(), persona.apellidos(), estado);
    }

    @Transactional
    public void reservar(String dni, Long invitacionId, Long actorId) {
        byte[] huella = huellaValida(dni);
        buscarPersona(dni, huella, false);
        if (porInvitacion(invitacionId).isPresent()) {
            throw new OperacionInvalidaException("La invitación ya tiene un DNI asociado.");
        }
        insertar(huella, null, invitacionId, terminacion(dni), null);
        auditoria.registrar(actorId, "IDENTIDAD_INVITACION_ASOCIADA", "correo_autorizado", invitacionId,
                null, null, "Se asoció la invitación a un DNI comprobado.");
    }

    public ConsultaPeruDniService.Persona validarRegistro(String dni, Long invitacionId) {
        byte[] huella = huellaValida(dni);
        if (porInvitacion(invitacionId).filter(v -> !MessageDigest.isEqual(v.huella(), huella)).isPresent()
                || porHuella(huella).filter(v -> !invitacionId.equals(v.invitacionId())).isPresent()) {
            throw new OperacionInvalidaException(ERROR_REGISTRO);
        }
        return buscarPersona(dni, huella, false);
    }

    @Transactional
    public void registrar(String dni, Long invitacionId, Long usuarioId) {
        var huella = huellaValida(dni);
        int actualizadas = jdbc.update("""
                UPDATE identidad_dni SET usuario_id = ?, invitacion_id = NULL, verificado_en = CURRENT_TIMESTAMP
                WHERE huella = ? AND invitacion_id = ?
                """, usuarioId, huella, invitacionId);
        if (actualizadas == 0) insertar(huella, usuarioId, null, terminacion(dni), LocalDateTime.now());
        auditoria.registrar(usuarioId, "IDENTIDAD_REGISTRO_VALIDADA", "usuario", usuarioId,
                null, null, "Coincidencia confirmada con el servicio de DNI durante el registro.");
    }

    @Transactional
    public void vincularCuenta(Usuario usuario, String dni, Long actorId) {
        byte[] huella = huellaValida(dni);
        var persona = buscarPersona(dni, huella, false);
        validarNombre(usuario, persona);
        insertar(huella, usuario.getId(), null, terminacion(dni), LocalDateTime.now());
        auditoria.registrar(actorId, "IDENTIDAD_CUENTA_VINCULADA", "usuario", usuario.getId(),
                null, null, "El administrador confirmó la coincidencia con el servicio de DNI.");
    }

    @Transactional
    public void revalidar(Usuario usuario, String dni) {
        byte[] huella = huellaValida(dni);
        Vinculo actual = porUsuario(usuario.getId())
                .filter(v -> MessageDigest.isEqual(v.huella(), huella))
                .orElseThrow(() -> new OperacionInvalidaException("El DNI no coincide con el vinculado a tu cuenta."));
        validarNombre(usuario, buscarPersona(dni, huella, true));
        int actualizadas = jdbc.update("""
                UPDATE identidad_dni SET verificado_en = CURRENT_TIMESTAMP WHERE huella = ? AND usuario_id = ?
                """, actual.huella(), usuario.getId());
        if (actualizadas == 0) throw new OperacionInvalidaException("El vínculo de tu cuenta cambió. Vuelve a revisar el estado.");
        auditoria.registrar(usuario.getId(), "IDENTIDAD_CUENTA_REVALIDADA", "usuario", usuario.getId(),
                null, null, "Se renovó la comprobación de la cuenta con el servicio de DNI.");
    }

    public Optional<Estado> estadoPropio(Long usuarioId) {
        return porUsuario(usuarioId).map(v -> new Estado("••••" + v.terminacion(), v.fecha()));
    }

    public boolean verificada(Long usuarioId) {
        return clave != null && Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM identidad_dni WHERE usuario_id = ?)", Boolean.class, usuarioId));
    }

    public Set<Long> usuariosVerificados(Collection<Long> ids) {
        return consultarIds(ids, """
                SELECT usuario_id FROM identidad_dni WHERE usuario_id IN (:ids)
                """);
    }

    public Set<Long> perfilesVerificados(Collection<Long> ids) {
        return consultarIds(ids, """
                SELECT p.id FROM perfiles p JOIN identidad_dni d ON d.usuario_id = p.usuario_id WHERE p.id IN (:ids)
                """);
    }

    public Set<Long> invitacionesReservadas(Collection<Long> ids) {
        return consultarIds(ids, """
                SELECT invitacion_id FROM identidad_dni WHERE invitacion_id IN (:ids)
                """);
    }

    private Set<Long> consultarIds(Collection<Long> ids, String sql) {
        if (clave == null || ids.isEmpty()) return Set.of();
        return new HashSet<>(listaJdbc.queryForList(sql, Map.of("ids", ids), Long.class));
    }

    private Optional<Vinculo> porUsuario(Long id) {
        if (clave == null) return Optional.empty();
        return jdbc.query(SELECT + " WHERE usuario_id = ?", VINCULO, id).stream().findFirst();
    }

    private Optional<Vinculo> porInvitacion(Long id) {
        return jdbc.query(SELECT + " WHERE invitacion_id = ?", VINCULO, id).stream().findFirst();
    }

    private Optional<Vinculo> porHuella(byte[] huella) {
        return jdbc.query(SELECT + " WHERE huella = ?", VINCULO, huella).stream().findFirst();
    }

    private void insertar(byte[] huella, Long usuario, Long invitacion, String terminacion, LocalDateTime fecha) {
        try {
            jdbc.update("""
                    INSERT INTO identidad_dni (huella, usuario_id, invitacion_id, terminacion, verificado_en)
                    VALUES (?, ?, ?, ?, ?)
                    """, huella, usuario, invitacion, terminacion, fecha);
        } catch (DuplicateKeyException ex) {
            throw new OperacionInvalidaException(ERROR_VINCULO);
        }
    }

    private ConsultaPeruDniService.Persona buscarPersona(String dni, byte[] huella, boolean actualizar) {
        String indice = HexFormat.of().formatHex(huella);
        long ahora = System.currentTimeMillis();
        synchronized (cache) {
            cache.entrySet().removeIf(e -> e.getValue().expira() <= ahora);
            var anterior = cache.get(indice);
            if (!actualizar && anterior != null) return anterior.persona();
            if (actualizar) cache.remove(indice);
        }
        var persona = proveedor.buscar(dni)
                .orElseThrow(() -> new OperacionInvalidaException(ConsultaPeruDniService.NO_ENCONTRADO));
        synchronized (cache) {
            if (cache.size() >= MAX_CACHE) cache.remove(cache.keySet().iterator().next());
            cache.put(indice, new EntradaCache(persona, System.currentTimeMillis() + TTL_CACHE));
        }
        return persona;
    }

    private byte[] huellaValida(String dni) {
        if (!disponible()) throw new ConsultaPeruDniService.ConsultaNoDisponible(false);
        if (!proveedor.formatoValido(dni)) throw new OperacionInvalidaException("Escribe un DNI de 8 dígitos.");
        // Se mantiene el dominio de la huella separado de las firmas de correo.
        return mac("dni:" + dni);
    }

    private byte[] mac(String valor) {
        try {
            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(clave);
            return hmac.doFinal(valor.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("No se pudo calcular la firma de identidad", ex);
        }
    }

    private void validarNombre(Usuario usuario, ConsultaPeruDniService.Persona persona) {
        String nombrePadron = persona.nombres() + " " + persona.apellidos();
        if (!normalizarNombre(usuario.getNombreCompleto()).equals(normalizarNombre(nombrePadron))) {
            throw new OperacionInvalidaException("El nombre de la cuenta no coincide con el servicio de DNI.");
        }
    }

    private String normalizarNombre(String nombre) {
        return Normalizer.normalize(nombre, Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
                .replaceAll("[\\p{P}\\s]+", " ").strip().toLowerCase(Locale.ROOT);
    }

    private String terminacion(String dni) {
        return dni.substring(4);
    }

    private SecretKeySpec habilitarClave(SecretKeySpec candidata) {
        if (candidata == null) return null;
        var log = LoggerFactory.getLogger(IdentidadDniService.class);
        try {
            byte[] huellaClave = MessageDigest.getInstance("SHA-256").digest(candidata.getEncoded());
            jdbc.update("""
                    INSERT INTO identidad_dni_config (id, huella_clave) VALUES (1, ?)
                    ON DUPLICATE KEY UPDATE id = 1
                    """, huellaClave);
            byte[] esperada = jdbc.queryForObject("SELECT huella_clave FROM identidad_dni_config WHERE id = 1", byte[].class);
            if (esperada != null && MessageDigest.isEqual(huellaClave, esperada)) return candidata;
            log.warn("IDENTIDAD_DNI_CLAVE no coincide con la clave de esta instalación.");
        } catch (DataAccessException ex) {
            log.warn("No se pudo iniciar la identidad por DNI. Revisa db/migracion_identidad_dni.sql y los permisos configurados.");
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("No se pudo validar la clave de identidad", ex);
        }
        return null;
    }

    private SecretKeySpec cargarClave(String valor) {
        try {
            byte[] bytes = Base64.getDecoder().decode(valor.strip());
            if (bytes.length >= 32) return new SecretKeySpec(bytes, "HmacSHA256");
        } catch (IllegalArgumentException ex) {
            // La configuración puede corregirse sin revelar la clave.
        }
        LoggerFactory.getLogger(IdentidadDniService.class)
                .warn("La identidad por DNI requiere IDENTIDAD_DNI_CLAVE en Base64 con al menos 32 bytes.");
        return null;
    }
}
