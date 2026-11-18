package com.skillbridge.ai.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Activa las tareas programadas de Spring (@Scheduled). Hoy la única es el
 * resumen semanal por correo (ResumenSemanalService.enviarATodos), cada
 * lunes a las 8:00 de Perú.
 */
@Configuration
@EnableScheduling
public class ProgramacionConfig {
}
