package com.skillbridge.ai.config;

import com.skillbridge.ai.interceptor.SesionInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final SesionInterceptor sesionInterceptor;

    public WebConfig(SesionInterceptor sesionInterceptor) {
        this.sesionInterceptor = sesionInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(sesionInterceptor)
                .addPathPatterns(
                        "/administrador/**",
                        "/resource-manager/**",
                        "/project-manager/**",
                        "/colaborador/**",
                        // "Mi cuenta" y "Notificaciones" son un único controlador
                        // compartido por los 4 roles (CuentaController,
                        // NotificacionesController) y viven fuera de esos 4
                        // prefijos - sin esto, un POST directo a /cuenta/** o
                        // /notificaciones/** sin sesión no se redirige a login,
                        // revienta con NullPointerException (ver SesionInterceptor).
                        "/cuenta/**",
                        "/notificaciones/**"
                );
    }
}
