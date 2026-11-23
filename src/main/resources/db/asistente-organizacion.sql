CREATE TABLE IF NOT EXISTS asistente_organizacion (
    conversacion_id BIGINT UNSIGNED PRIMARY KEY,
    proyecto_id BIGINT UNSIGNED NULL,
    archivada BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_asistente_org_conversacion FOREIGN KEY (conversacion_id)
        REFERENCES asistente_conversaciones(id) ON DELETE CASCADE,
    CONSTRAINT fk_asistente_org_proyecto FOREIGN KEY (proyecto_id)
        REFERENCES proyectos(id) ON DELETE SET NULL
) ENGINE=InnoDB;
