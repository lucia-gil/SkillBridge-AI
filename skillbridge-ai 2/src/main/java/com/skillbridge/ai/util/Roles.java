package com.skillbridge.ai.util;

/**
 * Valores validos de usuarios.rol_organizacional (ENUM de MySQL) mas los
 * "roles efectivos" que la aplicacion calcula para navegacion/plantillas,
 * que no viven en esa columna (son contextuales por proyecto via
 * asignaciones.rol_en_proyecto, ver comentario de la tabla en el SQL).
 */
public final class Roles {

    private Roles() {
    }

    // Valores reales de usuarios.rol_organizacional
    public static final String ADMINISTRADOR = "administrador";
    public static final String RESOURCE_MANAGER = "resource_manager";

    // "Roles efectivos" (solo para decidir a que carpeta de plantillas
    // redirigir tras el login y que sidebar mostrar). NINGUNO de estos dos
    // se guarda en usuarios.rol_organizacional.
    public static final String PROJECT_MANAGER = "project_manager";
    public static final String COLABORADOR = "colaborador";

    /** Slug de URL/carpeta de plantillas para cada rol efectivo. */
    public static String slug(String rolEfectivo) {
        if (ADMINISTRADOR.equals(rolEfectivo)) return "administrador";
        if (RESOURCE_MANAGER.equals(rolEfectivo)) return "resource-manager";
        if (PROJECT_MANAGER.equals(rolEfectivo)) return "project-manager";
        return "colaborador";
    }

    /** Etiqueta legible para el badge del sidebar. */
    public static String etiqueta(String rolEfectivo) {
        if (ADMINISTRADOR.equals(rolEfectivo)) return "Administrador";
        if (RESOURCE_MANAGER.equals(rolEfectivo)) return "Resource Manager";
        if (PROJECT_MANAGER.equals(rolEfectivo)) return "Project Manager";
        return "Colaborador";
    }
}
