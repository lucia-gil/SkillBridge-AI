package com.skillbridge.ai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;

import java.net.URI;
import java.util.Locale;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.auth.google.enabled", havingValue = "true")
class GoogleOAuthConfig {

    static final String INICIO = "/auth/google/iniciar";
    static final String RETORNO = "/auth/google/callback";

    @Bean
    ClientRegistrationRepository googleOAuthClients(
            @Value("${app.auth.google.base-url}") String baseUrl,
            @Value("${app.auth.google.client-id}") String clientId,
            @Value("${app.auth.google.client-secret}") String clientSecret) {
        if (clientId.isBlank() || clientSecret.isBlank()) {
            throw new IllegalArgumentException("Configura GOOGLE_CLIENT_ID y GOOGLE_CLIENT_SECRET para el acceso con Google.");
        }

        return new InMemoryClientRegistrationRepository(CommonOAuth2Provider.GOOGLE.getBuilder("google")
                .clientId(clientId.strip())
                .clientSecret(clientSecret.strip())
                .scope("openid", "email")
                .tokenUri("https://oauth2.googleapis.com/token")
                .redirectUri(validarOrigen(baseUrl) + RETORNO)
                .build());
    }

    static String validarOrigen(String baseUrl) {
        URI uri;
        try {
            uri = URI.create(baseUrl);
        } catch (IllegalArgumentException ex) {
            throw origenInvalido();
        }
        String host = uri.getHost();
        boolean local = "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host);
        String esquema = uri.getScheme();
        if (host == null || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                || uri.getPort() == 0 || uri.getPort() > 65535 || uri.getRawAuthority().endsWith(":")
                || !uri.getRawPath().isEmpty()) {
            throw origenInvalido();
        }
        if (local && ("http".equals(esquema) || "https".equals(esquema))) {
            return baseUrl;
        }
        String dominio = host.toLowerCase(Locale.ROOT);
        if (!"https".equals(esquema) || !dominio.matches("[a-z0-9.-]+[.][a-z][a-z0-9-]*")) {
            throw origenInvalido();
        }
        return baseUrl;
    }

    private static IllegalArgumentException origenInvalido() {
        return new IllegalArgumentException(
                "Para Google, APP_BASE_URL debe ser un origen HTTPS con dominio, sin ruta ni barra final. Prueba local: http://localhost:8080.");
    }
}
