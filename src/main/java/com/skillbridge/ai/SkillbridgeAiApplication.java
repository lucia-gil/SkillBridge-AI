package com.skillbridge.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

import java.util.TimeZone;

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

    /** Zona horaria de la plataforma (Perú, UTC-5 todo el año: no hay horario de verano). */
    public static final String ZONA_HORARIA = "America/Lima";

    // Toda la app trabaja en hora de Perú, aunque el servidor (EC2, Docker)
    // esté en UTC: LocalDateTime.now(), las horas que se muestran y las
    // fechas que se guardan en MySQL. Va en un bloque static para que se
    // aplique tanto con "java -jar" (main) como desplegada en Tomcat (WAR).
    static {
        TimeZone.setDefault(TimeZone.getTimeZone(ZONA_HORARIA));
    }

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return builder.sources(SkillbridgeAiApplication.class);
    }

    public static void main(String[] args) {
        SpringApplication.run(SkillbridgeAiApplication.class, args);
    }
}
