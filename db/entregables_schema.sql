-- =====================================================================
-- SkillBridge AI — Módulo de ENTREGABLES (v4.2)
-- Ejecutar DESPUÉS de skillbridge_db_v4_actualizado.sql.
-- =====================================================================
--
-- Por qué existe: "% Avance" en proyectos.html es heurístico (tiempo
-- transcurrido entre fecha_inicio y fecha_fin_estimada), no mide trabajo
-- real. Este módulo agrega la fuente de verdad: el PM (o el Admin) crea
-- ENTREGABLES sobre un proyecto (como las tareas de Moodle: título,
-- descripción, apertura/cierre); cada colaborador del equipo sube su
-- ENTREGA (texto, link y/o archivo); el PM la revisa, califica (0-20,
-- escala PUCP) y comenta. Con eso, "avance real" = entregas calificadas
-- / entregables totales, no una fecha.
--
-- Diseño:
--  - Un entregable pertenece a UN proyecto (no a un colaborador puntual):
--    todo el equipo activo del proyecto lo ve y puede entregar.
--  - Una entrega pertenece a UN entregable + UN perfil (colaborador). Es
--    UNIQUE (entregable_id, perfil_id): "editar entrega" y "borrar
--    entrega" (como en tu captura de Moodle) actualizan/borran esa misma
--    fila, no acumulan versiones — igual de simple que
--    certificados_habilidad (como máximo uno por combinación).
--  - Contenido de la entrega: texto, url_entrega y archivo (blob) son
--    todos NULL-ables; se exige al menos uno en la capa de servicio
--    (Java), igual que ya hacen con certificados_habilidad. Soporta
--    cualquier tipo de archivo razonable (PDF, imagen, .txt, .zip...),
--    no solo PDF — CertificadoHabilidad solo pedía PDF; acá se valida
--    una lista blanca más amplia en el servicio.
--  - "Atrasado" NO se guarda como columna: se calcula comparando
--    entregas.fecha_entrega contra entregables.fecha_cierre (así nunca
--    queda desincronizado si alguien cambia la fecha de cierre después).
--  - calificacion es DECIMAL(4,2) en escala 0.00–20.00 (PUCP), igual
--    que perfil_habilidad.nivel usa su propia escala 1-5: cada tabla
--    documenta la suya.
-- =====================================================================

SET FOREIGN_KEY_CHECKS = 0;

-- ---------------------------------------------------------------------
-- 1. ENTREGABLES — creados por el PM del proyecto (o el Administrador)
-- ---------------------------------------------------------------------
CREATE TABLE entregables (
    id                  BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    proyecto_id         BIGINT UNSIGNED NOT NULL,
    creado_por_id       BIGINT UNSIGNED NOT NULL,
    titulo              VARCHAR(200) NOT NULL,
    descripcion         TEXT NULL,
    fecha_apertura      DATETIME NOT NULL,
    fecha_cierre        DATETIME NOT NULL,
    puntaje_maximo      DECIMAL(4,2) NOT NULL DEFAULT 20.00,
    estado              ENUM('activo','cancelado') NOT NULL DEFAULT 'activo',
    fecha_creacion      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_entregable_proyecto FOREIGN KEY (proyecto_id) REFERENCES proyectos(id) ON DELETE CASCADE,
    CONSTRAINT fk_entregable_creador FOREIGN KEY (creado_por_id) REFERENCES perfiles(id),
    CONSTRAINT ck_entregable_fechas CHECK (fecha_cierre > fecha_apertura),
    CONSTRAINT ck_entregable_puntaje CHECK (puntaje_maximo > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_entregables_proyecto ON entregables(proyecto_id);
CREATE INDEX idx_entregables_cierre ON entregables(fecha_cierre);

-- ---------------------------------------------------------------------
-- 2. ENTREGAS — lo que sube cada colaborador para UN entregable
-- ---------------------------------------------------------------------
CREATE TABLE entregas (
    id                  BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    entregable_id       BIGINT UNSIGNED NOT NULL,
    perfil_id           BIGINT UNSIGNED NOT NULL,
    texto               TEXT NULL,
    url_entrega         VARCHAR(500) NULL,
    archivo_contenido   LONGBLOB NULL,
    archivo_nombre      VARCHAR(200) NULL,
    archivo_tipo        VARCHAR(100) NULL,
    fecha_entrega       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    estado              ENUM('enviado','revisado') NOT NULL DEFAULT 'enviado',
    calificacion        DECIMAL(4,2) NULL,
    comentario_pm       TEXT NULL,
    revisado_por_id     BIGINT UNSIGNED NULL,
    fecha_revision      DATETIME NULL,
    CONSTRAINT fk_entrega_entregable FOREIGN KEY (entregable_id) REFERENCES entregables(id) ON DELETE CASCADE,
    CONSTRAINT fk_entrega_perfil FOREIGN KEY (perfil_id) REFERENCES perfiles(id),
    CONSTRAINT fk_entrega_revisor FOREIGN KEY (revisado_por_id) REFERENCES perfiles(id),
    CONSTRAINT uq_entrega_entregable_perfil UNIQUE (entregable_id, perfil_id),
    CONSTRAINT ck_entrega_calificacion CHECK (calificacion IS NULL OR calificacion >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_entregas_entregable ON entregas(entregable_id);
CREATE INDEX idx_entregas_perfil ON entregas(perfil_id);

-- ---------------------------------------------------------------------
-- 3. Catálogo de notificaciones: 2 códigos nuevos para este módulo
--    (reutiliza tipos_notificacion, ya sembrada por el script base).
-- ---------------------------------------------------------------------
INSERT INTO tipos_notificacion (codigo, nombre, descripcion)
SELECT * FROM (SELECT 'entregable_nuevo' AS codigo, 'Nuevo entregable' AS nombre,
        'Se creó un entregable nuevo en un proyecto tuyo' AS descripcion) AS tmp
WHERE NOT EXISTS (SELECT 1 FROM tipos_notificacion WHERE codigo = 'entregable_nuevo');

INSERT INTO tipos_notificacion (codigo, nombre, descripcion)
SELECT * FROM (SELECT 'entregable_revisado' AS codigo, 'Entrega revisada' AS nombre,
        'El PM calificó o comentó tu entrega' AS descripcion) AS tmp
WHERE NOT EXISTS (SELECT 1 FROM tipos_notificacion WHERE codigo = 'entregable_revisado');

SET FOREIGN_KEY_CHECKS = 1;
