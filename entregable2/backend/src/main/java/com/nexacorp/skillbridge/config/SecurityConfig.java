package com.nexacorp.skillbridge.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuración TEMPORAL para el Entregable 2.
 *
 * RF01 (login con sesión, roles, CSRF) todavía no se implementa — eso
 * corresponde a la semana de "Sesión, security" del curso, más adelante en
 * el cronograma. Mientras tanto se deja todo abierto para poder verificar
 * en este entregable que la app conecta con la base de datos en la nube
 * (ver /api/estado) sin quedar bloqueada por el login por defecto que
 * agrega spring-boot-starter-security.
 *
 * IMPORTANTE: esta clase se reemplaza por la configuración real de
 * Spring Security (sesión + roles + CSRF) en el sprint donde se construya
 * RF01. No desplegar esta versión más allá de este entregable.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            .csrf(csrf -> csrf.disable());
        return http.build();
    }
}
