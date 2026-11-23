-- Ejecutar sobre skillbridge_db antes de desplegar la actualización.
CREATE TABLE IF NOT EXISTS identidad_dni (
    huella BINARY(32) PRIMARY KEY,
    usuario_id BIGINT UNSIGNED NULL UNIQUE,
    invitacion_id BIGINT UNSIGNED NULL UNIQUE,
    terminacion CHAR(4) CHARACTER SET ascii NOT NULL,
    verificado_en DATETIME NULL,
    CONSTRAINT fk_identidad_dni_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE,
    CONSTRAINT fk_identidad_dni_invitacion FOREIGN KEY (invitacion_id) REFERENCES correos_autorizados(id) ON DELETE CASCADE,
    CONSTRAINT chk_identidad_dni_estado CHECK (
        (usuario_id IS NULL AND invitacion_id IS NOT NULL AND verificado_en IS NULL)
        OR (usuario_id IS NOT NULL AND invitacion_id IS NULL AND verificado_en IS NOT NULL)
    )
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS identidad_dni_config (
    id TINYINT UNSIGNED PRIMARY KEY,
    huella_clave BINARY(32) NOT NULL,
    CONSTRAINT chk_identidad_dni_config CHECK (id = 1)
) ENGINE=InnoDB;
