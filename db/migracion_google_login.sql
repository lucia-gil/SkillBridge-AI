-- Ejecutar sobre la base de SkillBridge antes de activar GOOGLE_LOGIN_ENABLED.
CREATE TABLE IF NOT EXISTS usuarios_google (
    usuario_id BIGINT UNSIGNED NOT NULL PRIMARY KEY,
    google_sub VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    CONSTRAINT uk_usuarios_google_sub UNIQUE (google_sub),
    CONSTRAINT fk_usuarios_google_usuario FOREIGN KEY (usuario_id)
        REFERENCES usuarios (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
