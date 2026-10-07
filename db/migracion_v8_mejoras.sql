-- ============================================================
-- Migración v8: Archivos de proyecto, enlaces de perfil,
--               enlace GitHub en proyectos.
-- Ejecutar sobre la base de datos existente (ddl-auto=none).
-- ============================================================

-- 1. Tabla de archivos adjuntos de un proyecto (contexto)
CREATE TABLE IF NOT EXISTS archivos_proyecto (
    id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    proyecto_id BIGINT UNSIGNED NOT NULL,
    nombre      VARCHAR(255) NOT NULL,
    tipo        VARCHAR(100),
    datos       MEDIUMBLOB NOT NULL,
    fecha_subida DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_archivo_proyecto FOREIGN KEY (proyecto_id)
        REFERENCES proyectos(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2. Enlace GitHub en proyectos
ALTER TABLE proyectos
    ADD COLUMN github_url VARCHAR(500) DEFAULT NULL;

-- 3. LinkedIn y GitHub en perfiles
ALTER TABLE perfiles
    ADD COLUMN linkedin_url VARCHAR(500) DEFAULT NULL,
    ADD COLUMN github_url   VARCHAR(500) DEFAULT NULL;
