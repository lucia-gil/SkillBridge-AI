package com.skillbridge.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

/**
 * Punto de entrada de SkillBridge AI.
 *
 * Extiende SpringBootServletInitializer para que el mismo artefacto sirva
 * como WAR desplegable en un servidor externo (Tomcat, etc. - pedido
 * explicito) y, a la vez, se pueda seguir ejecutando en desarrollo con
 * "mvn spring-boot:run" (jar embebido gracias a spring-boot-starter-tomcat
 * en scope "provided": esta presente en el classpath de desarrollo pero
 * no se empaqueta dentro del WAR final).
 */
@SpringBootApplication
public class SkillbridgeAiApplication extends SpringBootServletInitializer {

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return builder.sources(SkillbridgeAiApplication.class);
    }

    public static void main(String[] args) {
        SpringApplication.run(SkillbridgeAiApplication.class, args);
    }
}
