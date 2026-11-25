package com.skillbridge.ai.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;

@Configuration(proxyBeanMethods = false)
@Profile("prod")
public class HttpsProduccionConfig {

    @Bean
    FilterRegistrationBean<OncePerRequestFilter> exigirHttps(@Value("${app.base-url}") String baseUrl) {
        validarOrigenPublico(baseUrl);

        OncePerRequestFilter filtro = new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                            FilterChain chain) throws ServletException, IOException {
                if (!request.isSecure()) {
                    response.setHeader("Cache-Control", "no-store");
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    return;
                }
                chain.doFilter(request, response);
            }
        };
        var registro = new FilterRegistrationBean<>(filtro);
        registro.setName("httpsProduccion");
        registro.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registro;
    }

    private static void validarOrigenPublico(String baseUrl) {
        URI uri;
        try {
            uri = URI.create(baseUrl);
        } catch (IllegalArgumentException error) {
            throw origenInvalido();
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                || uri.getRawAuthority().endsWith(":") || uri.getPort() == 0 || uri.getPort() > 65535
                || uri.getRawUserInfo() != null || !uri.getRawPath().isEmpty()
                || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw origenInvalido();
        }
    }

    private static IllegalArgumentException origenInvalido() {
        return new IllegalArgumentException(
                "APP_BASE_URL debe ser un origen HTTPS sin ruta, parámetros ni barra final. Ejemplo: https://skillbridge.example.com");
    }
}
