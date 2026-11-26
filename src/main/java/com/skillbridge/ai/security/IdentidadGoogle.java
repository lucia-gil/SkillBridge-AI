package com.skillbridge.ai.security;

import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;

import java.io.Serializable;
import java.time.Instant;
import java.util.Locale;
import java.util.regex.Pattern;

public record IdentidadGoogle(String sub, String correo, boolean correoGestionado) implements Serializable {

    public static final String ATRIBUTO_PENDIENTE = IdentidadGoogle.class.getName() + ".pendiente";
    public static final String ATRIBUTO_SEGUNDO_FACTOR = IdentidadGoogle.class.getName() + ".segundoFactor";
    private static final Pattern SUBJECT = Pattern.compile("[!-~]{1,255}");

    private static final Pattern CORREO = Pattern.compile("[^@\\s]+@[^@\\s]+");

    static IdentidadGoogle desde(OidcIdToken token) {
        String sub = token.getSubject();
        String correo = normalizar(token.getEmail());
        if (sub == null || !SUBJECT.matcher(sub).matches()
                || !Boolean.TRUE.equals(token.getEmailVerified()) || correo.length() > 150
                || !CORREO.matcher(correo).matches()) {
            throw new BadCredentialsException(
                    "Google debe confirmar tu correo. Elige otra cuenta o entra con tu contraseña.");
        }
        String dominio = correo.substring(correo.lastIndexOf('@') + 1);
        String hd = token.getClaimAsString("hd");
        return new IdentidadGoogle(sub, correo, dominio.equals("gmail.com") || dominio.equals(normalizar(hd)));
    }

    public boolean coincideCon(String otroCorreo) {
        return correo.equals(normalizar(otroCorreo));
    }

    private static String normalizar(String texto) {
        return texto == null ? "" : texto.strip().toLowerCase(Locale.ROOT);
    }

    public record Pendiente(IdentidadGoogle identidad, Instant expira) implements Serializable {
        public static Pendiente crear(IdentidadGoogle identidad) {
            return new Pendiente(identidad, Instant.now().plusSeconds(300));
        }

        public static Pendiente leer(HttpSession sesion, String atributo) {
            if (sesion == null || !(sesion.getAttribute(atributo) instanceof Pendiente pendiente)) {
                return null;
            }
            if (!pendiente.expira().isAfter(Instant.now())) {
                sesion.removeAttribute(atributo);
                return null;
            }
            return pendiente;
        }
    }
}
