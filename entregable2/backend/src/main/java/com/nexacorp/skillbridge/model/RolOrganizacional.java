package com.nexacorp.skillbridge.model;

/**
 * Puesto fijo de gestión global, independiente de cualquier proyecto.
 * NULL en `usuarios.rol_organizacional` es el caso normal ("esta persona no
 * tiene un puesto fijo; lo que hace depende de en qué proyecto la mires" —
 * ver {@link RolEnProyecto}).
 *
 * Nota de estilo: las constantes se nombran en minúscula-con-guion-bajo,
 * calcando exactamente los valores del ENUM nativo de MySQL
 * (rol_organizacional ENUM('administrador','resource_manager')), para que
 * @Enumerated(EnumType.STRING) no necesite un conversor aparte. No es la
 * convención habitual de Java (ALL_CAPS), es una decisión deliberada de
 * paridad 1:1 con la base de datos.
 */
public enum RolOrganizacional {
    administrador,
    resource_manager
}
