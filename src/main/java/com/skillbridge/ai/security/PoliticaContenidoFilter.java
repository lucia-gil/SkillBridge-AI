package com.skillbridge.ai.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Content-Security-Policy de toda la aplicación (hallazgo H03).
 *
 * <p>Inventario de orígenes (todo lo demás queda bloqueado):
 * <ul>
 *   <li>Scripts: solo archivos propios ('self', jQuery y el generador de QR
 *       también son locales) y los &lt;script&gt; en línea de las plantillas,
 *       que llevan {@code th:nonce="${cspNonce}"}: un nonce aleatorio por
 *       respuesta. Un script inyectado no conoce el nonce y no se ejecuta.
 *       Sin atributos onclick/onerror en línea (no se permiten).</li>
 *   <li>Imágenes: propias, data:/blob: (QR, vista previa de fotos y
 *       adjuntos) y miniaturas de YouTube (i.ytimg.com).</li>
 *   <li>Marcos: propios (vistas previas) y el reproductor de YouTube.</li>
 *   <li>Estilos: propios y atributos style en línea (las vistas los usan).</li>
 * </ul>
 *
 * <p>{@code app.seguridad.csp.modo}: {@code report-only} solo informa las
 * infracciones a {@code /csp-report} (se registran en el log) sin bloquear;
 * {@code enforce} (por defecto) las bloquea.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class PoliticaContenidoFilter extends OncePerRequestFilter {

    /** Nombre del atributo de request que leen las plantillas (th:nonce="${cspNonce}"). */
    public static final String ATRIBUTO_NONCE = "cspNonce";
    public static final String RUTA_INFORME = "/csp-report";

    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final boolean soloInforme;

    public PoliticaContenidoFilter(@Value("${app.seguridad.csp.modo:enforce}") String modo) {
        this.soloInforme = "report-only".equalsIgnoreCase(modo.trim());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        byte[] bytes = new byte[18];
        ALEATORIO.nextBytes(bytes);
        String nonce = Base64.getEncoder().encodeToString(bytes);
        request.setAttribute(ATRIBUTO_NONCE, nonce);

        String politica = String.join("; ",
                "default-src 'self'",
                "script-src 'self' 'nonce-" + nonce + "'",
                "script-src-attr 'none'",
                "style-src 'self' 'unsafe-inline'",
                "img-src 'self' data: blob: https://i.ytimg.com",
                "font-src 'self'",
                "connect-src 'self'",
                "media-src 'self' blob:",
                "frame-src 'self' https://www.youtube-nocookie.com",
                "worker-src 'self' blob:",
                "manifest-src 'self'",
                "object-src 'none'",
                "base-uri 'self'",
                "form-action 'self'",
                "frame-ancestors 'self'",
                "report-uri " + request.getContextPath() + RUTA_INFORME);
        response.setHeader(soloInforme ? "Content-Security-Policy-Report-Only" : "Content-Security-Policy", politica);
        chain.doFilter(request, response);
    }
}
