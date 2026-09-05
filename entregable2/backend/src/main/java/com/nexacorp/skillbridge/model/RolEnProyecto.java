package com.nexacorp.skillbridge.model;

/**
 * El rol SOLO dentro de un proyecto específico: liderarlo o aportar como
 * recurso. No confundir con {@link RolOrganizacional} — una misma persona
 * puede ser 'colaborador' en un proyecto y 'project_manager' en otro; eso
 * es precisamente lo que este campo permite representar, a diferencia de un
 * rol único y fijo por usuario.
 */
public enum RolEnProyecto {
    project_manager,
    colaborador
}
