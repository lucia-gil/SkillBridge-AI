-- =====================================================================
-- SkillBridge AI — BASE DE DATOS COMPLETA (único script oficial)
-- =====================================================================
-- Crea el esquema skillbridge_db con TODAS las tablas, la siembra base
-- (administrador, catálogos, configuración) y los datos de demo.
--
--   mysql -u root -p < db/skillbridge_db.sql
--   (o en Workbench: abrir el archivo y "Run All")
--
-- Si el esquema ya existe, falla a propósito para no borrarte datos.
-- Para empezar de cero, descomenta la línea siguiente:
-- DROP SCHEMA IF EXISTS skillbridge_db;
--
-- Contenido, en este orden:
--   1) Esquema base v4.1 + v7 (roles) + siembra
--   2) Entregables y entregas
--   3) Doble factor (usuarios_2fa)
--   4) Datos de demo
--   5) Foros v6 (tópicos, citas, destacados, adjuntos)
--   6) v8–v10: archivos de proyecto, perfil profesional, cursos,
--      resumen IA de foros y tablas de Spring Session
--
-- Reemplaza a los scripts sueltos anteriores (skillbridge_db_v4*.sql,
-- entregables_schema.sql, datos_demo_skillbridge.sql, migracion_v6..v10,
-- spring_session_mysql.sql, skillbridge_db_COMPLETA_con_demo.sql).
--
-- ¿Ya tienes una BD creada con la versión anterior? No hace falta volver
-- a crearla: al arrancar, la app agrega sola lo que le falte (columnas
-- nuevas, usuarios_2fa, archivos_proyecto, cursos, Spring Session). Solo
-- los datos demo del perfil profesional (sección 6) no se agregan solos.
--
-- Contraseña de todas las cuentas: admin123  (admin@nexacorp.com = Administrador)
-- =====================================================================

-- ################ 1) ESQUEMA BASE ################
-- =====================================================================
-- CAMBIOS v7 — modelo de permisos y consistencia de roles
--  * Dos niveles de rol: usuarios.rol_organizacional (administrador |
--    resource_manager | NULL = Miembro) y asignaciones.rol_en_proyecto
--    (project_manager | colaborador). Admin y RM NUNCA tienen filas en
--    asignaciones; un Miembro puede liderar unos proyectos y colaborar en
--    otros a la vez. Validado en el backend (sin triggers).
--  * Columnas nuevas (aditivas): proyectos.fecha_pausa / motivo_pausa /
--    fecha_reanudacion_estimada, correos_autorizados.rol_asignado y
--    propuestas_asignacion.origen.
--  * Datos demo: App Móvil Aurora la lidera Tomás Herrera (35%, total 95%);
--    Luis Peña queda solo como Resource Manager.
--  * Para una BD YA creada con una versión anterior, usar
--    db/migracion_v7_consistencia_roles.sql.
-- =====================================================================
-- SkillBridge AI
-- Modelo de datos v4.1 (v4 + fixes para que el código Java corra)
-- Motor: MySQL 8.0+  |  Charset: utf8mb4  |  Motor de tabla: InnoDB
-- =====================================================================
--
-- CAMBIOS v4 → v4.1 — verificados contra el código Java del proyecto
-- (no venían de una reunión con el JP, sino de correr el repo contra
-- este script y comparar columna por columna con las entidades):
--
--  A. Falta la tabla PROPUESTAS_ASIGNACION (nueva, sección 9-BIS).
--     TalentMatchingService.java / PropuestaAsignacion.java (AI Talent
--     Matching: "Proponer" desde el PM, "Aprobar/Rechazar" desde el RM)
--     hacen @Table(name = "propuestas_asignacion") y ese CREATE TABLE no
--     existía en ninguna parte del script. Con ddl-auto=none, ese módulo
--     fallaba en cuanto se usaba. Se agregó completa, con FKs a
--     proyectos/perfiles y los mismos nombres de columna que usa el
--     repositorio (PropuestaAsignacionRepository).
--  B. CERTIFICADOS_HABILIDAD estaba incompleta para lo que ya hace el
--     código: CertificadoHabilidad.java tiene contenido_archivo (el PDF
--     en sí) y tipo_archivo, que no existían como columnas -> subir un
--     PDF real (no un link) tronaba con "Unknown column". Además
--     url_archivo era NOT NULL, pero cuando se sube un archivo real el
--     código nunca llena url_archivo (deja esa opción para el caso
--     "link externo") -> también tronaba por NOT NULL. Se agregaron las
--     columnas faltantes y se relajó url_archivo a NULL.
--  C. USUARIOS le faltaban foto_perfil (MEDIUMBLOB), foto_perfil_tipo y
--     foto_perfil_nombre. Usuario.java las mapea y CuentaController usa
--     /cuenta/foto para subir/quitar la foto de perfil; sin estas
--     columnas, Hibernate no podía ni siquiera hacer un SELECT simple
--     por id (fallaba el login y cualquier página que cargue el usuario
--     de sesión — "Unknown column 'u1_0.foto_perfil'").
--  D. FORO_PUBLICACIONES le faltaba es_fijado (BOOLEAN). ForoPublicacion
--     .java la mapea y ForosPmController expone POST
--     /project-manager/foros/{id}/fijar para fijar un hilo; sin la
--     columna, cualquier SELECT sobre foro_publicaciones fallaba igual
--     que con foto_perfil.
--     (A, B, C y D se encontraron comparando, columna por columna, cada
--     @Entity de src/main/java/.../model contra este script — no solo
--     revisando a ojo, para no dejar pasar una quinta.)
--
-- CAMBIOS v3 → v4 — observaciones del Jefe de Práctica (reunión 3/9):
--
--  1. "No colocar creado_por / que sea por id"
--     → PROYECTOS pierde la columna creado_por_id. Es redundante: quién
--       lidera/creó el proyecto ya se puede identificar en ASIGNACIONES
--       (rol_en_proyecto='project_manager'), que es la fuente de verdad.
--       El resto del esquema ya identificaba "quién hizo X" con columnas
--       *_id hacia perfiles/usuarios (nunca con un nombre en texto); ese
--       mismo criterio se aplica también a las tablas nuevas de abajo.
--  2. "Falta tablita para comentarios"
--     → tabla COMENTARIOS_EVENTO (nueva, sección 12).
--  3. "Una tabla para dirigido_a, dependiendo a lo que quieras modelar"
--     → tabla TIPOS_AUDIENCIA (nueva, sección 24) +
--       EVENTOS_PROYECTO.audiencia_id.
--  4. "Falta logs para la parte de auditoria"
--     → AUDITORIA_LOGS ahora registra valor_anterior/valor_nuevo y su
--       alcance documentado deja de limitarse a eventos de sesión.
--  5. "Categoria, una tabla aparte"
--     → tabla CATEGORIAS_HABILIDAD (nueva, sección 4) +
--       HABILIDADES.categoria_id.
--  6. "Crear tabla para tipo de notificación"
--     → tabla TIPOS_NOTIFICACION (nueva, sección 16) +
--       NOTIFICACIONES.tipo_id.
--  7. "Crear una tabla para Tipo de evento"
--     → tabla TIPOS_EVENTO (nueva, sección 23) +
--       EVENTOS_PROYECTO.tipo_id.
--  8. "En perfiles no va nombre_completo, iría dentro de usuario"
--     → nombre_completo se mueve de PERFILES a USUARIOS (sección 1).
--
--  Supuestos declarados (las notas llegaron sueltas, de una reunión, sin
--  ejemplos de detalle): "comentarios" y "dirigido_a" no traen alcance
--  exacto, así que se modeló la interpretación más simple y consistente
--  con el resto del esquema, el criterio usado se explica en el
--  comentario de cada tabla nueva. Si el JP tenía otro entregable en
--  mente (p. ej. comentarios sobre el foro en vez de sobre eventos),
--  el cambio es acotado y fácil de mover.
-- =====================================================================
CREATE SCHEMA skillbridge_db
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;
USE skillbridge_db;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ---------------------------------------------------------------------
-- 1. USUARIOS — Autenticación (RF01)
--    Datos de login + identidad de la cuenta. nombre_completo vive acá
--    (no en perfiles, corrección del JP): es un dato de identidad ligado
--    a la cuenta, no un atributo de negocio que varíe por proyecto.
--    rol_organizacional es NULL para la mayoría de las personas: "esta
--    persona no tiene un puesto fijo de gestión global; lo que hace
--    depende de en qué proyecto la mires" (ver asignaciones.rol_en_proyecto
--    más abajo). Administrador y Resource Manager SÍ son puestos fijos,
--    independientes de cualquier proyecto, por eso viven aquí y no en
--    asignaciones.
-- ---------------------------------------------------------------------
CREATE TABLE usuarios (
                          id                      BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                          correo                  VARCHAR(150) NOT NULL UNIQUE,
                          contrasena_hash         VARCHAR(255) NOT NULL,
                          nombre_completo         VARCHAR(150) NOT NULL,
    -- AGREGADAS en v4.1: Usuario.java las mapea (foto de perfil, subida
    -- desde /cuenta/foto) y sin ellas Hibernate no podía ni hacer un
    -- SELECT por id (ver bitácora de cambios v4 -> v4.1 al inicio).
                          foto_perfil             MEDIUMBLOB NULL,
                          foto_perfil_tipo        VARCHAR(50) NULL,
                          foto_perfil_nombre      VARCHAR(255) NULL,
                          rol_organizacional      ENUM('administrador','resource_manager') NULL,
                          estado                  ENUM('activo','inactivo') NOT NULL DEFAULT 'activo',
                          fecha_creacion          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          fecha_actualizacion     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_usuarios_rol ON usuarios(rol_organizacional);

-- ---------------------------------------------------------------------
-- 2. CORREOS_AUTORIZADOS — Lista blanca de registro (RF01/RF02)
--    Nadie se auto-registra libremente: un Administrador autoriza el
--    correo antes (a mano o por carga masiva CSV), y recién con ese
--    correo en esta tabla la persona puede completar su registro.
-- ---------------------------------------------------------------------
-- Enlaces de "¿Olvidaste tu contraseña?" (AuthService.solicitarRecuperacion):
-- un token por pedido, de un solo uso y con vencimiento.
CREATE TABLE tokens_recuperacion_password (
                                              id                BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                                              usuario_id        BIGINT UNSIGNED NOT NULL,
                                              token             VARCHAR(64) NOT NULL,
                                              fecha_creacion    DATETIME NOT NULL,
                                              fecha_expiracion  DATETIME NOT NULL,
                                              usado             TINYINT(1) NOT NULL DEFAULT 0,
                                              UNIQUE KEY uq_token_recuperacion (token),
                                              INDEX idx_token_recuperacion_usuario (usuario_id),
                                              CONSTRAINT fk_token_recuperacion_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE correos_autorizados (
                                     id                  BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                                     correo              VARCHAR(150) NOT NULL UNIQUE,
                                     autorizado_por_id   BIGINT UNSIGNED NOT NULL,
                                     origen_carga        ENUM('individual','masiva_csv','masiva_excel') NOT NULL DEFAULT 'individual',
                                     utilizado           BOOLEAN NOT NULL DEFAULT FALSE,
                                     fecha_autorizacion  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                     fecha_uso           DATETIME NULL,
    -- v7: rol organizacional con el que se invitó (NULL = Miembro). Al
    -- registrarse, la cuenta nace con ese rol.
                                     rol_asignado        ENUM('administrador','resource_manager') NULL,
    -- Estado y fecha del último correo de invitación enviado (carga masiva y reenvíos).
                                     estado_envio        VARCHAR(32) NULL,
                                     ultimo_envio        DATETIME NULL,
                                     CONSTRAINT fk_correoaut_admin FOREIGN KEY (autorizado_por_id) REFERENCES usuarios(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 3. PERFILES — Datos de negocio de cada miembro (RF02)
--    1:1 con usuarios. Todo lo que es negocio/profesional (cargo,
--    disponibilidad...) vive aquí; la identidad de cuenta (correo,
--    nombre) vive en usuarios. nombre_completo se sacó de aquí por
--    pedido explícito del JP.
-- ---------------------------------------------------------------------
CREATE TABLE perfiles (
                          id                          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                          usuario_id                  BIGINT UNSIGNED NOT NULL UNIQUE,
                          cargo                       VARCHAR(100) NULL,
                          disponibilidad_porcentaje   TINYINT UNSIGNED NOT NULL DEFAULT 100,
                          experiencia_anios           TINYINT UNSIGNED NOT NULL DEFAULT 0,
                          biografia                   TEXT NULL,
                          estado                      ENUM('activo','inactivo') NOT NULL DEFAULT 'activo',
                          fecha_creacion              DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                          CONSTRAINT fk_perfil_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE,
                          CONSTRAINT ck_perfil_disponibilidad CHECK (disponibilidad_porcentaje BETWEEN 0 AND 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 4. CATEGORIAS_HABILIDAD — Catálogo de categorías (NUEVO, corrección JP)
--    En v3 era un VARCHAR libre dentro de habilidades. El JP pidió una
--    tabla aparte: agrupa visualmente el catálogo de habilidades
--    (Backend, Frontend, DevOps & Cloud, Datos, Diseño, QA...) y ahora
--    se puede administrar (crear/renombrar categoría) sin tocar filas de
--    habilidades una por una.
-- ---------------------------------------------------------------------
CREATE TABLE categorias_habilidad (
                                      id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                                      nombre      VARCHAR(60) NOT NULL UNIQUE,
                                      descripcion VARCHAR(255) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 5. HABILIDADES — Catálogo cerrado (RF08)
--    categoria ahora es categoria_id, FK a categorias_habilidad (antes
--    era VARCHAR libre; corrección del JP).
-- ---------------------------------------------------------------------
CREATE TABLE habilidades (
                             id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                             nombre          VARCHAR(80) NOT NULL UNIQUE,
                             categoria_id    BIGINT UNSIGNED NOT NULL,
                             CONSTRAINT fk_habilidad_categoria FOREIGN KEY (categoria_id) REFERENCES categorias_habilidad(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_habilidades_categoria ON habilidades(categoria_id);

-- ---------------------------------------------------------------------
-- 6. PERFIL_HABILIDAD — Relación colaborador ↔ habilidad (RF02/RF08)
--    Llave primaria compuesta (perfil_id, habilidad_id): no tiene sentido
--    que la misma persona declare la misma habilidad dos veces.
--    nivel: escala 1-5 (más fina que las 4 etiquetas Básico/Intermedio/
--    Avanzado/Experto de los mockups — ver informe, se reconcilia
--    mostrando en la UI un nivel textual calculado a partir del número).
-- ---------------------------------------------------------------------
CREATE TABLE perfil_habilidad (
                                  perfil_id           BIGINT UNSIGNED NOT NULL,
                                  habilidad_id        BIGINT UNSIGNED NOT NULL,
                                  nivel               TINYINT UNSIGNED NOT NULL,
                                  validado_por_id     BIGINT UNSIGNED NULL,
                                  fecha_declaracion   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                  PRIMARY KEY (perfil_id, habilidad_id),
                                  CONSTRAINT fk_ph_perfil FOREIGN KEY (perfil_id) REFERENCES perfiles(id) ON DELETE CASCADE,
                                  CONSTRAINT fk_ph_habilidad FOREIGN KEY (habilidad_id) REFERENCES habilidades(id),
                                  CONSTRAINT fk_ph_validador FOREIGN KEY (validado_por_id) REFERENCES perfiles(id) ON DELETE SET NULL,
                                  CONSTRAINT ck_ph_nivel CHECK (nivel BETWEEN 1 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 7. PROYECTOS (RF03)
--    tecnologias como JSON (lista de strings) — etiqueta libre, no impacta
--    el matching de habilidades, por eso no se normaliza a catálogo.
--
--    creado_por_id NO existe (corrección del JP: "no colocar creado_por,
--    que sea por id"). En v3 esta columna era redundante y necesitaba un
--    hack a nivel de aplicación para mantenerse sincronizada con
--    asignaciones (ver nota más abajo) — se elimina en vez de arreglarse,
--    porque el dato ya vive, por id, en asignaciones.
-- ---------------------------------------------------------------------
CREATE TABLE proyectos (
                           id                      BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                           nombre                  VARCHAR(150) NOT NULL,
                           descripcion             TEXT NULL,
                           tecnologias             JSON NULL,
                           estado                  ENUM('planificacion','activo','en_pausa','completado','cancelado') NOT NULL DEFAULT 'planificacion',
                           colaboradores_requeridos TINYINT UNSIGNED NOT NULL DEFAULT 0,
                           fecha_inicio            DATE NOT NULL,
                           fecha_fin_estimada      DATE NULL,
                           fecha_creacion          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- v7: pausar = el equipo sale del proyecto (solo queda el PM); se
    -- exigen motivo y fecha estimada de reanudación.
                           fecha_pausa             DATE NULL,
                           motivo_pausa            VARCHAR(500) NULL,
                           fecha_reanudacion_estimada DATE NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_proyectos_estado ON proyectos(estado);

-- Nota de integridad (documentada en el informe, no resuelta por la BD):
-- al crear un proyecto, la aplicación DEBE insertar en la misma transacción
-- una fila en `asignaciones` para el perfil creador, con rol_en_proyecto =
-- 'project_manager'. Si no, queda un proyecto sin PM formalmente asignado
-- y sin ninguna forma de saber quién lo creó — ya no hay columna
-- creado_por_id de respaldo. Implementado en backend/.../ProyectoService.java.
-- Para saber quién es el PM/creador de un proyecto:
--   SELECT perfil_id FROM asignaciones
--   WHERE proyecto_id = ? AND rol_en_proyecto = 'project_manager' AND estado = 'activa';

-- ---------------------------------------------------------------------
-- 8. PROYECTO_HABILIDAD_REQUERIDA (RF03, insumo de RF05)
-- ---------------------------------------------------------------------
CREATE TABLE proyecto_habilidad_requerida (
                                              proyecto_id     BIGINT UNSIGNED NOT NULL,
                                              habilidad_id    BIGINT UNSIGNED NOT NULL,
                                              nivel_requerido TINYINT UNSIGNED NOT NULL,
                                              PRIMARY KEY (proyecto_id, habilidad_id),
                                              CONSTRAINT fk_phr_proyecto FOREIGN KEY (proyecto_id) REFERENCES proyectos(id) ON DELETE CASCADE,
                                              CONSTRAINT fk_phr_habilidad FOREIGN KEY (habilidad_id) REFERENCES habilidades(id),
                                              CONSTRAINT ck_phr_nivel CHECK (nivel_requerido BETWEEN 1 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 9. ASIGNACIONES — Rol contextual por proyecto (RF03/RF04)
--    rol_en_proyecto SOLO puede ser 'project_manager' o 'colaborador':
--    liderar el proyecto o aportar como recurso. Describe la función de la
--    persona DENTRO de ese proyecto, no quién es en la empresa.
--
--    Modelo v7 (regla de negocio, validada en el backend): Administrador y
--    Resource Manager NUNCA tienen filas aquí — existen por encima de todos
--    los proyectos (el RM aprueba asignaciones; no puede aprobarse a sí
--    mismo). Solo los Miembros (usuarios.rol_organizacional IS NULL)
--    participan, y un mismo Miembro puede liderar unos proyectos y
--    colaborar en otros a la vez. La carga efectiva de una persona suma sus
--    filas activas en proyectos vigentes (planificacion/activo/en_pausa) y
--    nunca supera el 100%.
--
--    Desde v4, esta tabla también es la ÚNICA fuente de verdad de quién
--    creó/lidera cada proyecto (fila con rol_en_proyecto='project_manager'):
--    ya no existe proyectos.creado_por_id.
--
--    clave_activa (columna generada): combina proyecto_id-perfil_id SOLO
--    cuando estado='activa'; si no, es NULL. El UNIQUE KEY sobre esa
--    columna permite guardar historial completo de roles por proyecto
--    (útil para "consultar información histórica de proyectos") mientras
--    sigue bloqueando que la misma persona tenga DOS roles activos a la
--    vez en el mismo proyecto. MySQL permite múltiples NULL en una
--    columna UNIQUE, así que las filas finalizadas/canceladas no chocan
--    entre sí.
-- ---------------------------------------------------------------------
CREATE TABLE asignaciones (
                              id                  BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                              proyecto_id         BIGINT UNSIGNED NOT NULL,
                              perfil_id           BIGINT UNSIGNED NOT NULL,
                              rol_en_proyecto     ENUM('project_manager','colaborador') NOT NULL,
                              carga_porcentaje    TINYINT UNSIGNED NOT NULL,
                              estado              ENUM('activa','finalizada','cancelada') NOT NULL DEFAULT 'activa',
                              fecha_inicio        DATE NOT NULL,
                              fecha_fin           DATE NULL,
                              fecha_creacion      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                              clave_activa        VARCHAR(41) GENERATED ALWAYS AS (
                                  CASE WHEN estado = 'activa'
                                           THEN CONCAT(proyecto_id, '-', perfil_id)
                                       ELSE NULL END
                                  ) STORED,
    -- proyecto_id es columna base de clave_activa (columna GENERATED):
    -- InnoDB/MySQL prohíbe ON DELETE/UPDATE CASCADE, SET NULL o SET DEFAULT
    -- sobre una FK que use una columna base de una columna generada (error
    -- 1215 al crear la tabla en MySQL real, aunque MariaDB no lo exige).
    -- Por eso esta FK va sin acción explícita (RESTRICT por defecto): la
    -- aplicación no debe borrar físicamente un proyecto con asignaciones;
    -- usa proyectos.estado = 'cancelado' (borrado lógico, ya soportado).
                              CONSTRAINT fk_asig_proyecto FOREIGN KEY (proyecto_id) REFERENCES proyectos(id),
                              CONSTRAINT fk_asig_perfil FOREIGN KEY (perfil_id) REFERENCES perfiles(id),
                              CONSTRAINT uq_asig_clave_activa UNIQUE (clave_activa),
                              CONSTRAINT ck_asig_carga CHECK (carga_porcentaje BETWEEN 0 AND 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_asignaciones_perfil ON asignaciones(perfil_id);
CREATE INDEX idx_asignaciones_proyecto ON asignaciones(proyecto_id);
-- La carga total (%) de un colaborador (que se ve en los dashboards) NO se
-- almacena: se calcula sumando carga_porcentaje de sus filas con
-- estado='activa'. Igual que en la v1, para que nunca quede desincronizada.

-- ---------------------------------------------------------------------
-- 9-BIS. PROPUESTAS_ASIGNACION (RF05 — AI Talent Matching)
--     AGREGADA en v4.1: faltaba en el script v4 aunque el módulo de AI
--     Talent Matching (project-manager/ai-talent-matching.html "Proponer"
--     y resource-manager/ai-talent-matching.html "Aprobar/Rechazar") ya
--     estaba implementado sobre esta tabla. Guarda cada propuesta que un
--     PM envía a un RM para asignar un candidato recomendado por el
--     matching, con el snapshot de los scores calculados en ese momento
--     (si luego el colaborador declara nuevas habilidades, la propuesta
--     ya resuelta no debe "cambiar de opinión" retroactivamente).
--     Al aprobar, ProyectoService.asignarColaborador() crea la fila real
--     en `asignaciones`; esta tabla es solo el flujo de aprobación, no
--     reemplaza a asignaciones.
-- ---------------------------------------------------------------------
CREATE TABLE propuestas_asignacion (
                                       id                          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                                       proyecto_id                 BIGINT UNSIGNED NOT NULL,
                                       candidato_perfil_id         BIGINT UNSIGNED NOT NULL,
                                       solicitado_por_perfil_id    BIGINT UNSIGNED NOT NULL,
                                       revisado_por_perfil_id      BIGINT UNSIGNED NULL,
                                       dedicacion_porcentaje       TINYINT UNSIGNED NOT NULL,
                                       score_total                 TINYINT UNSIGNED NOT NULL,
                                       score_habilidades           TINYINT UNSIGNED NOT NULL,
                                       score_experiencia           TINYINT UNSIGNED NOT NULL,
                                       score_disponibilidad        TINYINT UNSIGNED NOT NULL,
                                       estado                      VARCHAR(20) NOT NULL DEFAULT 'pendiente',
                                       motivo_resolucion           VARCHAR(500) NULL,
                                       fecha_solicitud             DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                       fecha_resolucion            DATETIME NULL,
    -- v7: de dónde viene la propuesta (talent_matching | manual | reincorporacion).
                                       origen                      VARCHAR(20) NOT NULL DEFAULT 'talent_matching',
                                       CONSTRAINT fk_propasig_proyecto FOREIGN KEY (proyecto_id) REFERENCES proyectos(id) ON DELETE CASCADE,
                                       CONSTRAINT fk_propasig_candidato FOREIGN KEY (candidato_perfil_id) REFERENCES perfiles(id),
                                       CONSTRAINT fk_propasig_solicitante FOREIGN KEY (solicitado_por_perfil_id) REFERENCES perfiles(id),
                                       CONSTRAINT fk_propasig_revisor FOREIGN KEY (revisado_por_perfil_id) REFERENCES perfiles(id),
                                       CONSTRAINT ck_propasig_estado CHECK (estado IN ('pendiente','aprobada','rechazada')),
                                       CONSTRAINT ck_propasig_dedicacion CHECK (dedicacion_porcentaje BETWEEN 1 AND 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_propasig_proyecto ON propuestas_asignacion(proyecto_id);
CREATE INDEX idx_propasig_candidato ON propuestas_asignacion(candidato_perfil_id);
CREATE INDEX idx_propasig_estado ON propuestas_asignacion(estado);

-- ---------------------------------------------------------------------
-- 10. EXCEPCIONES_CARGA (RF04 — límite de carga por colaborador)
--     No está en el documento del JP, pero sí en RF04 ("evitar asignaciones
--     que superen los límites... ") y en los mockups (alerta "Tomás Herrera
--     (120%)... Solicitar excepción"). Se mantiene de la v1, adaptada para
--     apuntar a `perfiles` en vez de a `usuario`.
-- ---------------------------------------------------------------------
CREATE TABLE excepciones_carga (
                                   id                  BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                                   asignacion_id       BIGINT UNSIGNED NOT NULL,
                                   solicitado_por_id   BIGINT UNSIGNED NOT NULL,
                                   aprobado_por_id     BIGINT UNSIGNED NULL,
                                   porcentaje_aprobado TINYINT UNSIGNED NOT NULL,
                                   fecha_limite        DATE NOT NULL,
                                   estado              ENUM('pendiente','aprobada','rechazada') NOT NULL DEFAULT 'pendiente',
                                   motivo              TEXT NULL,
                                   fecha_solicitud     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                   fecha_resolucion    DATETIME NULL,
                                   CONSTRAINT fk_exc_asignacion FOREIGN KEY (asignacion_id) REFERENCES asignaciones(id) ON DELETE CASCADE,
                                   CONSTRAINT fk_exc_solicitante FOREIGN KEY (solicitado_por_id) REFERENCES perfiles(id),
                                   CONSTRAINT fk_exc_aprobador FOREIGN KEY (aprobado_por_id) REFERENCES perfiles(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 11. FORO_PUBLICACIONES (RF06)
--     Una sola tabla autorreferencial: publicacion_padre_id NULL = hilo
--     raíz (tiene titulo); no NULL = respuesta a ese hilo (o a otra
--     respuesta). etiquetas es JSON libre (no hay catálogo de categorías
--     de foro — se reemplaza el diseño de la v1 que sí tenía
--     foro_categoria, siguiendo el documento fuente).
--     num_vistas es una extensión propia (no está en el documento del JP)
--     para no perder esa funcionalidad, ya visible en los mockups.
--
--     Nota: las respuestas del foro YA cumplen el rol de "comentarios"
--     sobre un proyecto (discusión asíncrona, RF06); por eso la tabla de
--     comentarios nueva que pide el JP (sección 12) se modela sobre
--     eventos, no sobre proyectos — evita duplicar el mismo concepto dos
--     veces.
-- ---------------------------------------------------------------------
CREATE TABLE foro_publicaciones (
                                    id                      BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                                    proyecto_id             BIGINT UNSIGNED NOT NULL,
                                    autor_id                BIGINT UNSIGNED NOT NULL,
                                    publicacion_padre_id    BIGINT UNSIGNED NULL,
                                    titulo                  VARCHAR(200) NULL,
                                    contenido               TEXT NOT NULL,
                                    etiquetas               JSON NULL,
                                    es_solucion             BOOLEAN NOT NULL DEFAULT FALSE,
    -- AGREGADA en v4.1: ForoPublicacion.esFijado + POST
    -- /project-manager/foros/{id}/fijar (ForosPmController) la
    -- necesitan; sin ella, cualquier SELECT sobre esta tabla fallaba.
                                    es_fijado               BOOLEAN NOT NULL DEFAULT FALSE,
                                    num_vistas              INT UNSIGNED NOT NULL DEFAULT 0,
                                    fecha_publicacion       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                    CONSTRAINT fk_fp_proyecto FOREIGN KEY (proyecto_id) REFERENCES proyectos(id) ON DELETE CASCADE,
                                    CONSTRAINT fk_fp_autor FOREIGN KEY (autor_id) REFERENCES perfiles(id),
                                    CONSTRAINT fk_fp_padre FOREIGN KEY (publicacion_padre_id) REFERENCES foro_publicaciones(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_fp_proyecto ON foro_publicaciones(proyecto_id);
CREATE INDEX idx_fp_padre ON foro_publicaciones(publicacion_padre_id);

-- ---------------------------------------------------------------------
-- 12. COMENTARIOS_EVENTO — Comentarios sobre un evento del calendario
--     (NUEVO, corrección JP: "falta tablita para comentarios")
--
--     Supuesto declarado: la nota del JP no especifica sobre qué entidad
--     van los comentarios. Se descartó modelarlos sobre proyectos porque
--     ese rol ya lo cumple foro_publicaciones (RF06, discusión con
--     hilos). El hueco real está en eventos_proyecto (sección 25):
--     reuniones/entregables/hitos no tenían ninguna forma de que el
--     equipo comente ("confirmo asistencia", "se reprogramó", etc.). Por
--     eso se modela como una tabla chica con FK directa a un solo padre
--     (evento), en vez del patrón "genérico" (tipo_origen + referencia_id
--     sin FK real) que ya se usa en auditoria_logs/conocimiento_embeddings
--     — acá sí conviene una FK real porque hay un único padre posible.
-- ---------------------------------------------------------------------
CREATE TABLE comentarios_evento (
                                    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                                    evento_id       BIGINT UNSIGNED NOT NULL,
                                    autor_id        BIGINT UNSIGNED NOT NULL,
                                    contenido       TEXT NOT NULL,
                                    editado         BOOLEAN NOT NULL DEFAULT FALSE,
                                    fecha_creacion  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                    fecha_edicion   DATETIME NULL,
                                    CONSTRAINT fk_comevento_evento FOREIGN KEY (evento_id) REFERENCES eventos_proyecto(id) ON DELETE CASCADE,
                                    CONSTRAINT fk_comevento_autor FOREIGN KEY (autor_id) REFERENCES perfiles(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_comevento_evento ON comentarios_evento(evento_id);

-- ---------------------------------------------------------------------
-- 13. RESUMENES_IA (RF06 — diferenciador)
--     Un resumen "vigente" por hilo: se sobrescribe/actualiza cuando se
--     regenera (no se guarda historial de versiones, no lo pide el
--     documento fuente). Comparar fecha_generado contra la fecha de la
--     última respuesta del hilo (MAX(fecha_publicacion) en
--     foro_publicaciones) dice si el resumen quedó desactualizado.
-- ---------------------------------------------------------------------
CREATE TABLE resumenes_ia (
                              id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                              publicacion_id  BIGINT UNSIGNED NOT NULL UNIQUE,
                              resumen         TEXT NOT NULL,
                              modelo_utilizado VARCHAR(50) NULL,
                              fecha_generado  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                              CONSTRAINT fk_resumen_publicacion FOREIGN KEY (publicacion_id) REFERENCES foro_publicaciones(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 14-15. CHAT_SALAS / CHAT_MENSAJES (RF07)
--     Chat en tiempo (casi) real, separado del foro (foro = discusión
--     asíncrona y buscable; chat = conversación corta del equipo).
-- ---------------------------------------------------------------------
-- Salas de chat: una por proyecto (tipo 'proyecto') o una por pareja de
-- personas (tipo 'directo', mensajes directos; sin proyecto). clave_directa
-- ("perfilMenor-perfilMayor") impide crear dos salas para la misma pareja.
CREATE TABLE chat_salas (
                            id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                            tipo            VARCHAR(20) NOT NULL DEFAULT 'proyecto',
                            proyecto_id     BIGINT UNSIGNED NULL,
                            clave_directa   VARCHAR(41) NULL,
                            nombre          VARCHAR(100) NOT NULL,
                            fecha_creacion  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                            UNIQUE KEY uq_chat_directo (clave_directa),
                            CONSTRAINT fk_sala_proyecto FOREIGN KEY (proyecto_id) REFERENCES proyectos(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Las dos personas de cada mensaje directo.
CREATE TABLE chat_participantes (
                                    sala_id    BIGINT UNSIGNED NOT NULL,
                                    perfil_id  BIGINT UNSIGNED NOT NULL,
                                    PRIMARY KEY (sala_id, perfil_id),
                                    INDEX idx_chat_part_perfil (perfil_id),
                                    CONSTRAINT fk_part_sala FOREIGN KEY (sala_id) REFERENCES chat_salas(id) ON DELETE CASCADE,
                                    CONSTRAINT fk_part_perfil FOREIGN KEY (perfil_id) REFERENCES perfiles(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE chat_mensajes (
                               id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                               sala_id         BIGINT UNSIGNED NOT NULL,
                               autor_id        BIGINT UNSIGNED NOT NULL,
                               contenido       TEXT NOT NULL,
                               fecha_envio     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                               CONSTRAINT fk_chatmsg_sala FOREIGN KEY (sala_id) REFERENCES chat_salas(id) ON DELETE CASCADE,
                               CONSTRAINT fk_chatmsg_autor FOREIGN KEY (autor_id) REFERENCES perfiles(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Hasta qué mensaje leyó cada persona en cada sala (contador de no leídos del chat)
CREATE TABLE chat_lecturas (
                               sala_id            BIGINT UNSIGNED NOT NULL,
                               perfil_id          BIGINT UNSIGNED NOT NULL,
                               ultimo_mensaje_id  BIGINT UNSIGNED NOT NULL DEFAULT 0,
                               PRIMARY KEY (sala_id, perfil_id),
                               CONSTRAINT fk_lectura_sala FOREIGN KEY (sala_id) REFERENCES chat_salas(id) ON DELETE CASCADE,
                               CONSTRAINT fk_lectura_perfil FOREIGN KEY (perfil_id) REFERENCES perfiles(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_chatmsg_sala ON chat_mensajes(sala_id);

-- ---------------------------------------------------------------------
-- 16. TIPOS_NOTIFICACION — Catálogo de tipos de notificación (NUEVO,
--     corrección JP: "crear tabla para tipo de notificación")
--     En v3 era el ENUM notificaciones.tipo. Se deja separada de
--     preferencias_notificacion.tipo_evento a propósito: son conceptos
--     distintos (tipo de UNA notificación puntual vs. categoría de
--     evento a la que alguien se suscribe/silencia) y sus valores no
--     calzan 1 a 1 — unificarlas forzaría una relación que el documento
--     fuente no pide.
-- ---------------------------------------------------------------------
CREATE TABLE tipos_notificacion (
                                    id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                                    codigo      VARCHAR(40) NOT NULL UNIQUE,
                                    nombre      VARCHAR(80) NOT NULL,
                                    descripcion VARCHAR(255) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 17. NOTIFICACIONES (RF09, propuesto en el backlog del equipo)
--     No está en el documento del JP, se mantiene de la v1 (adaptada a
--     perfiles) porque ya está en las 3 pantallas de "Centro de
--     notificaciones" de los mockups y en RF09 del backlog. tipo ahora
--     es tipo_id, FK a tipos_notificacion (corrección JP).
-- ---------------------------------------------------------------------
CREATE TABLE notificaciones (
                                id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                                perfil_id       BIGINT UNSIGNED NOT NULL,
                                tipo_id         BIGINT UNSIGNED NOT NULL,
                                titulo          VARCHAR(150) NOT NULL,
                                detalle         TEXT NULL,
                                leida           BOOLEAN NOT NULL DEFAULT FALSE,
                                canal           ENUM('app','mail','app_mail') NOT NULL DEFAULT 'app',
                                enlace_accion   VARCHAR(255) NULL,
                                fecha_creacion  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                CONSTRAINT fk_notif_perfil FOREIGN KEY (perfil_id) REFERENCES perfiles(id) ON DELETE CASCADE,
                                CONSTRAINT fk_notif_tipo FOREIGN KEY (tipo_id) REFERENCES tipos_notificacion(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_notif_perfil_leida ON notificaciones(perfil_id, leida);
CREATE INDEX idx_notif_tipo ON notificaciones(tipo_id);

CREATE TABLE preferencias_notificacion (
                                           id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                                           perfil_id   BIGINT UNSIGNED NOT NULL,
                                           tipo_evento ENUM('alertas_criticas','solicitudes_aprobacion','resultados_ia','resumen_semanal') NOT NULL,
                                           canal       ENUM('app','mail','app_mail','solo_app','solo_mail') NOT NULL,
                                           CONSTRAINT fk_pref_perfil FOREIGN KEY (perfil_id) REFERENCES perfiles(id) ON DELETE CASCADE,
                                           CONSTRAINT uq_pref_perfil_evento UNIQUE (perfil_id, tipo_evento)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 18. AUDITORIA_LOGS — Trazabilidad (ampliada, corrección JP: "faltan
--     logs para la parte de auditoria")
--     usuario_id apunta a `usuarios` (no a perfiles): son eventos de
--     seguridad/sesión Y de negocio (creación, modificación, borrado
--     lógico, aprobaciones...), y pueden ocurrir antes de que exista un
--     perfil de negocio completo (ej. un intento de login). En v3 el
--     comentario limitaba el alcance de esta tabla a "sesión/seguridad";
--     en v4 se usa para cualquier acción auditable del sistema, y se le
--     agregan valor_anterior/valor_nuevo para que un registro de
--     auditoría realmente sirva para reconstruir qué cambió, no solo que
--     algo cambió.
--     entidad_id es una referencia "genérica" (no tiene FK real: puede
--     apuntar a cualquier tabla según entidad_afectada) — trade-off
--     consciente, la integridad de este campo depende del código, no de
--     la base de datos.
-- ---------------------------------------------------------------------
CREATE TABLE auditoria_logs (
                                id                  BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                                usuario_id          BIGINT UNSIGNED NULL,
                                accion              VARCHAR(100) NOT NULL,
                                entidad_afectada    VARCHAR(60) NULL,
                                entidad_id          BIGINT UNSIGNED NULL,
                                valor_anterior      JSON NULL,
                                valor_nuevo         JSON NULL,
                                detalle             TEXT NULL,
                                fecha               DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                CONSTRAINT fk_audit_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_audit_fecha ON auditoria_logs(fecha);
CREATE INDEX idx_audit_entidad ON auditoria_logs(entidad_afectada, entidad_id);

-- ---------------------------------------------------------------------
-- 19. CONFIGURACION_GLOBAL (RF08, panel de Administrador)
--     Tabla clave-valor: ~13 parámetros heterogéneos, una tabla por
--     parámetro sería sobre-ingeniería para este alcance. Se mantiene de
--     la v1, actualizado_por_id ahora apunta a perfiles.
-- ---------------------------------------------------------------------
CREATE TABLE configuracion_global (
                                      clave                VARCHAR(80) PRIMARY KEY,
                                      valor                VARCHAR(255) NOT NULL,
                                      descripcion          VARCHAR(255) NULL,
                                      actualizado_por_id   BIGINT UNSIGNED NULL,
                                      fecha_actualizacion  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                      CONSTRAINT fk_config_perfil FOREIGN KEY (actualizado_por_id) REFERENCES perfiles(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 20. CONOCIMIENTO_EMBEDDINGS — Diferenciador base de IA (búsqueda
--     semántica, insumo también de RF05)
--     tipo_origen + referencia_id es otra referencia "genérica" sin FK
--     real (puede apuntar a foro_publicaciones, perfiles o proyectos).
--
--     LIMITACIÓN DECLARADA: el documento fuente menciona pgvector, que es
--     una extensión de PostgreSQL — este proyecto usa MySQL. vector_embedding
--     se guarda como JSON (arreglo de números) tal como especifica el
--     documento; la similitud de coseno se calcula en la aplicación
--     (fuerza bruta), no con un índice nativo de vectores. Es viable para
--     el volumen de datos de un curso; si el catálogo de contenido crece
--     mucho, evaluar Vertex AI Vector Search, o migrar esta tabla a un
--     motor con soporte nativo de vectores (MySQL 9 HeatWave, o
--     PostgreSQL + pgvector).
-- ---------------------------------------------------------------------
CREATE TABLE conocimiento_embeddings (
                                         id                  BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                                         tipo_origen         ENUM('publicacion_foro','perfil','proyecto') NOT NULL,
                                         referencia_id       BIGINT UNSIGNED NOT NULL,
                                         contenido_indexado  TEXT NOT NULL,
                                         vector_embedding    JSON NOT NULL,
                                         fecha_indexado      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_embeddings_origen ON conocimiento_embeddings(tipo_origen, referencia_id);

-- ---------------------------------------------------------------------
-- ASISTENTE_CONVERSACIONES / ASISTENTE_MENSAJES — memoria del Asistente IA
--     (chatbot). Una conversación por tema, separada por la vista (rol) desde
--     la que se habló; autor = 'usuario' o 'asistente'.
-- ---------------------------------------------------------------------
CREATE TABLE asistente_conversaciones (
    id                   BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    usuario_id           BIGINT UNSIGNED NOT NULL,
    rol                  VARCHAR(30) NOT NULL,
    titulo               VARCHAR(120) NOT NULL,
    fijada               TINYINT(1) NOT NULL DEFAULT 0,
    fecha_creacion       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_asist_conv_usuario (usuario_id, rol, fecha_actualizacion),
    CONSTRAINT fk_asist_conv_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE asistente_mensajes (
    id               BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    conversacion_id  BIGINT UNSIGNED NOT NULL,
    autor            VARCHAR(10) NOT NULL,
    contenido        TEXT NOT NULL,
    fecha            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_asist_msg_conv (conversacion_id, id),
    CONSTRAINT fk_asist_msg_conv FOREIGN KEY (conversacion_id) REFERENCES asistente_conversaciones(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Archivos e imágenes que el usuario adjunta a un mensaje del Asistente IA.
CREATE TABLE asistente_adjuntos (
    id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    mensaje_id  BIGINT UNSIGNED NOT NULL,
    nombre      VARCHAR(255) NOT NULL,
    tipo        VARCHAR(100) NOT NULL,
    tamano      INT UNSIGNED NOT NULL,
    datos       MEDIUMBLOB NOT NULL,
    fecha       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_asist_adj_msg (mensaje_id),
    CONSTRAINT fk_asist_adj_msg FOREIGN KEY (mensaje_id) REFERENCES asistente_mensajes(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 21. RECOMENDACIONES_IA_LOG — Trazabilidad del AI Talent Matching (RF05)
-- ---------------------------------------------------------------------
CREATE TABLE recomendaciones_ia_log (
                                        id                      BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                                        proyecto_id             BIGINT UNSIGNED NOT NULL,
                                        perfil_recomendado_id   BIGINT UNSIGNED NOT NULL,
                                        solicitado_por_id       BIGINT UNSIGNED NOT NULL,
                                        puntaje_compatibilidad  DECIMAL(5,2) NOT NULL,
                                        explicacion             TEXT NOT NULL,
                                        fue_asignado            BOOLEAN NOT NULL DEFAULT FALSE,
                                        fecha_generado          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                        CONSTRAINT fk_recia_proyecto FOREIGN KEY (proyecto_id) REFERENCES proyectos(id) ON DELETE CASCADE,
                                        CONSTRAINT fk_recia_recomendado FOREIGN KEY (perfil_recomendado_id) REFERENCES perfiles(id),
                                        CONSTRAINT fk_recia_solicitante FOREIGN KEY (solicitado_por_id) REFERENCES perfiles(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 22. TIPOS_EVENTO — Catálogo de tipos de evento (NUEVO, corrección JP:
--     "crear una tabla para tipo de evento")
--     En v3 era el ENUM eventos_proyecto.tipo ('entregable','reunion',
--     'hito'). color queda como extensión propia opcional, útil para
--     pintar el calendario en el front sin lógica condicional por código.
-- ---------------------------------------------------------------------
CREATE TABLE tipos_evento (
                              id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                              codigo      VARCHAR(40) NOT NULL UNIQUE,
                              nombre      VARCHAR(80) NOT NULL,
                              descripcion VARCHAR(255) NULL,
                              color       VARCHAR(20) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 23. TIPOS_AUDIENCIA — Catálogo de "dirigido a" (NUEVO, corrección JP:
--     "una tabla para dirigido_a dependiendo a lo que quieras modelar")
--
--     Supuesto declarado: se eligió la versión más simple que cubre el
--     ENUM de v3 ('todos','solo_pm','solo_colaboradores') como catálogo
--     administrable en vez de valores fijos en código. No se modeló
--     como una tabla de destinatarios individuales (evento × perfil)
--     porque el documento fuente no pide segmentar por persona, solo por
--     rol dentro del proyecto; si más adelante se necesita dirigir un
--     evento a colaboradores puntuales, esto se resuelve con una tabla
--     puente evento_destinatario sin romper lo ya modelado aquí.
-- ---------------------------------------------------------------------
CREATE TABLE tipos_audiencia (
                                 id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                                 codigo      VARCHAR(40) NOT NULL UNIQUE,
                                 nombre      VARCHAR(80) NOT NULL,
                                 descripcion VARCHAR(255) NULL,
    -- Regla de visibilidad de una audiencia creada por el Administrador
    -- (las audiencias base usan su propio código como regla).
                                 politica    VARCHAR(40) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 24. EVENTOS_PROYECTO — Calendario automático por colaborador
--     No hay tabla de "calendario por persona": el evento se crea UNA
--     vez, ligado al proyecto. El calendario de cada colaborador se arma
--     automáticamente cruzando sus asignaciones activas con los eventos
--     del proyecto correspondiente (ver consulta de ejemplo más abajo) —
--     así, si alguien se une o sale de un proyecto, sus eventos aparecen
--     o desaparecen solos, sin ninguna acción manual adicional.
--
--     tipo (ENUM) → tipo_id, FK a tipos_evento (corrección JP).
--     dirigido_a (ENUM) → audiencia_id, FK a tipos_audiencia (corrección
--     JP). audiencia_id usa DEFAULT 1 asumiendo que la semilla de
--     tipos_audiencia inserta 'todos' primero (ver DATOS SEMILLA) — el
--     mismo trade-off de IDs fijos por orden de inserción que ya usa el
--     resto del script para catálogos chicos.
--
--     creado_por_id SÍ se mantiene acá (a diferencia de proyectos): no es
--     redundante, porque no hay ninguna otra tabla de la que se pueda
--     derivar quién creó puntualmente un evento (puede ser el PM o un
--     Administrador, ver regla de negocio abajo) — por eso la corrección
--     "no colocar creado_por, que sea por id" ya estaba aplicada acá
--     desde v3: es un id (creado_por_id), nunca un nombre en texto.
--
--     Quién puede crear/editar eventos: el Project Manager del proyecto
--     (verificado contra `asignaciones`, rol_en_proyecto='project_manager',
--     estado='activa') o el Administrador. Esta restricción se valida en
--     el backend (Spring Security), no en la base de datos.
-- ---------------------------------------------------------------------
CREATE TABLE eventos_proyecto (
                                  id                  BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                                  proyecto_id         BIGINT UNSIGNED NOT NULL,
                                  creado_por_id       BIGINT UNSIGNED NOT NULL,
                                  tipo_id             BIGINT UNSIGNED NOT NULL,
                                  titulo              VARCHAR(150) NOT NULL,
                                  descripcion         TEXT NULL,
                                  fecha_inicio        DATETIME NOT NULL,
                                  fecha_fin           DATETIME NULL,
                                  enlace_virtual      VARCHAR(255) NULL,
                                  ubicacion           VARCHAR(150) NULL,
                                  audiencia_id        BIGINT UNSIGNED NOT NULL DEFAULT 1,
                                  estado              ENUM('pendiente', 'cumplido', 'atrasado', 'cancelado') NOT NULL DEFAULT 'pendiente',
                                  fecha_creacion      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                  CONSTRAINT fk_evento_proyecto FOREIGN KEY (proyecto_id) REFERENCES proyectos(id) ON DELETE CASCADE,
                                  CONSTRAINT fk_evento_creador FOREIGN KEY (creado_por_id) REFERENCES perfiles(id),
                                  CONSTRAINT fk_evento_tipo FOREIGN KEY (tipo_id) REFERENCES tipos_evento(id),
                                  CONSTRAINT fk_evento_audiencia FOREIGN KEY (audiencia_id) REFERENCES tipos_audiencia(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Agenda personal (calendario del Resource Manager): eventos propios que no
-- pertenecen a un proyecto. Solo los ve y gestiona su dueño.
CREATE TABLE eventos_personales (
                                  id                  BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                                  usuario_id          BIGINT UNSIGNED NOT NULL,
                                  tipo_id             BIGINT UNSIGNED NOT NULL,
                                  titulo              VARCHAR(150) NOT NULL,
                                  descripcion         TEXT NULL,
                                  fecha_inicio        DATETIME NOT NULL,
                                  enlace_virtual      VARCHAR(255) NULL,
                                  ubicacion           VARCHAR(150) NULL,
                                  fecha_creacion      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                  INDEX idx_evento_personal_usuario (usuario_id, fecha_inicio),
                                  CONSTRAINT fk_evento_personal_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE,
                                  CONSTRAINT fk_evento_personal_tipo FOREIGN KEY (tipo_id) REFERENCES tipos_evento(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Identidad por DNI (Consulta Perú): huella del DNI verificado, ligada a la
-- invitación mientras se registra y luego al usuario.
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

-- Creacion de la tabla de certificados para que se puedan subir pdfs y mas para vlaidars
-- CORREGIDA en v4.1: CertificadoHabilidad.java soporta DOS formas de
-- respaldo, mutuamente excluyentes (Opción A: link externo -> url_archivo;
-- Opción B: PDF real subido -> contenido_archivo/tipo_archivo). Esta tabla
-- solo tenía columnas para la Opción A; subir un PDF real fallaba por
-- columna inexistente y, además, por el NOT NULL de url_archivo (que la
-- Opción B nunca llena). Se agregan las columnas de la Opción B y se
-- relaja url_archivo a NULL.

CREATE TABLE certificados_habilidad (
                                        id                  BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                                        perfil_id           BIGINT UNSIGNED NOT NULL,
                                        habilidad_id        BIGINT UNSIGNED NOT NULL,
                                        nombre_archivo      VARCHAR(200) NOT NULL,
                                        url_archivo         VARCHAR(500) NULL,
                                        contenido_archivo   LONGBLOB NULL,
                                        tipo_archivo        VARCHAR(100) NULL,
                                        fecha_subida        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                        CONSTRAINT fk_cert_perfil_habilidad FOREIGN KEY (perfil_id, habilidad_id)
                                            REFERENCES perfil_habilidad(perfil_id, habilidad_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;



CREATE INDEX idx_eventos_proyecto ON eventos_proyecto(proyecto_id);
CREATE INDEX idx_eventos_fecha ON eventos_proyecto(fecha_inicio);
CREATE INDEX idx_eventos_tipo ON eventos_proyecto(tipo_id);
CREATE INDEX idx_eventos_audiencia ON eventos_proyecto(audiencia_id);

-- Consulta de ejemplo — calendario de un colaborador (perfil_id = ?):
--   SELECT e.* FROM eventos_proyecto e
--   JOIN asignaciones a ON a.proyecto_id = e.proyecto_id
--   WHERE a.perfil_id = ? AND a.estado = 'activa'
--     AND (e.audiencia_id = (SELECT id FROM tipos_audiencia WHERE codigo='todos')
--          OR (a.rol_en_proyecto = 'project_manager' AND e.audiencia_id = (SELECT id FROM tipos_audiencia WHERE codigo='solo_pm'))
--          OR (a.rol_en_proyecto = 'colaborador' AND e.audiencia_id = (SELECT id FROM tipos_audiencia WHERE codigo='solo_colaboradores')));

SET FOREIGN_KEY_CHECKS = 1;

-- =====================================================================
-- DATOS SEMILLA
-- =====================================================================

-- Catálogos primero (los necesitan usuarios/habilidades/eventos más abajo).

INSERT INTO categorias_habilidad (nombre) VALUES
                                              ('Backend'), ('DevOps & Cloud'), ('Frontend'), ('Datos'), ('Diseño'), ('QA');

INSERT INTO tipos_notificacion (codigo, nombre, descripcion) VALUES
                                                                 ('alerta', 'Alerta', 'Aviso que requiere atención (ej. sobrecarga de un colaborador)'),
                                                                 ('solicitud', 'Solicitud', 'Pedido pendiente de aprobación (ej. excepción de carga)'),
                                                                 ('info', 'Información', 'Aviso informativo sin acción requerida'),
                                                                 ('resultado_ia', 'Resultado de IA', 'Resultado de un proceso de IA (matching, resumen de foro, etc.)');

INSERT INTO tipos_evento (codigo, nombre, descripcion, color) VALUES
                                                                  ('entregable', 'Entregable', 'Fecha límite de un entregable del proyecto', '#E53935'),
                                                                  ('reunion', 'Reunión', 'Reunión de coordinación o seguimiento', '#1E88E5'),
                                                                  ('hito', 'Hito', 'Hito o milestone relevante del proyecto', '#43A047');

-- id=1 queda como 'todos': es el DEFAULT de eventos_proyecto.audiencia_id.
INSERT INTO tipos_audiencia (codigo, nombre, descripcion) VALUES
                                                              ('todos', 'Todos', 'Visible para todo el equipo del proyecto'),
                                                              ('solo_pm', 'Solo Project Manager', 'Visible únicamente para el/los Project Manager del proyecto'),
                                                              ('solo_colaboradores', 'Solo colaboradores', 'Visible únicamente para los colaboradores (no PM) del proyecto');

-- Administrador semilla (para poder autorizar el primer lote de correos).
-- La contraseña real se hashea desde la aplicación (BCrypt); este valor es
-- Coloqué como clave admin123, esto se debe cambiar por seguridad, coloqué tmb su hash correpondiente

INSERT INTO usuarios (correo, contrasena_hash, nombre_completo, rol_organizacional, estado)
VALUES ('admin@nexacorp.com',
        '$2b$10$5VvawdnDpSRdkKvzrBKun.sAfXs.HHrisVGgIuPFDIYGA3vxVG0Oe',
        'Admin SkillBridge',
        'administrador', 'activo');

INSERT INTO perfiles (usuario_id, cargo, disponibilidad_porcentaje, estado)
VALUES (1, 'Administradora de plataforma', 100, 'activo');

-- El propio administrador se autoriza a sí mismo como registro fundacional.
INSERT INTO correos_autorizados (correo, autorizado_por_id, origen_carga, utilizado, fecha_uso)
VALUES ('admin@nexacorp.com', 1, 'individual', TRUE, NOW());

INSERT INTO configuracion_global (clave, valor, descripcion) VALUES
                                                                 ('limite_carga_colaborador', '100', 'Porcentaje máximo de dedicación total por colaborador'),
                                                                 ('maximo_proyectos_simultaneos', '3', 'Cantidad máxima de proyectos activos por colaborador'),
                                                                 ('auto_aprobar_asignaciones_menores_a', '20', 'Umbral (%) bajo el cual una asignación no requiere validación'),
                                                                 ('peso_matching_habilidades', '50', 'Peso (%) de habilidades en el score de AI Talent Matching'),
                                                                 ('peso_matching_experiencia', '30', 'Peso (%) de experiencia en el score de AI Talent Matching'),
                                                                 ('peso_matching_disponibilidad', '20', 'Peso (%) de disponibilidad en el score de AI Talent Matching'),
                                                                 ('resumenes_ia_foros_activos', 'true', 'Generar resumen automático en hilos con muchas respuestas'),
                                                                 ('asistente_ia_habilitado', 'true', 'Habilita el asistente conversacional (RF07)'),
                                                                 ('dominio_correo_permitido', 'nexacorp.com', 'Dominio corporativo aceptado en el registro'),
                                                                 ('expiracion_sesion_minutos', '45', 'Minutos de inactividad antes de cerrar sesión'),
                                                                 ('doble_factor_obligatorio_admin', 'true', 'Exige 2FA para el rol Administrador'),
                                                                 ('idioma_por_defecto', 'es-CO', 'Idioma por defecto de la plataforma'),
                                                                 ('zona_horaria_defecto', 'GMT-5', 'Zona horaria por defecto de la plataforma');

-- categoria_id se resuelve por subconsulta (no se hardcodean ids) para no
-- depender del orden exacto del INSERT de categorias_habilidad de arriba.
INSERT INTO habilidades (nombre, categoria_id) VALUES
                                                   ('Java', (SELECT id FROM categorias_habilidad WHERE nombre = 'Backend')),
                                                   ('Spring Boot', (SELECT id FROM categorias_habilidad WHERE nombre = 'Backend')),
                                                   ('Kubernetes', (SELECT id FROM categorias_habilidad WHERE nombre = 'DevOps & Cloud')),
                                                   ('Docker', (SELECT id FROM categorias_habilidad WHERE nombre = 'DevOps & Cloud')),
                                                   ('React', (SELECT id FROM categorias_habilidad WHERE nombre = 'Frontend')),
                                                   ('Thymeleaf', (SELECT id FROM categorias_habilidad WHERE nombre = 'Frontend')),
                                                   ('PostgreSQL', (SELECT id FROM categorias_habilidad WHERE nombre = 'Datos')),
                                                   ('MySQL', (SELECT id FROM categorias_habilidad WHERE nombre = 'Datos')),
                                                   ('Apache Kafka', (SELECT id FROM categorias_habilidad WHERE nombre = 'Backend')),
                                                   ('Terraform', (SELECT id FROM categorias_habilidad WHERE nombre = 'DevOps & Cloud')),
                                                   ('Figma', (SELECT id FROM categorias_habilidad WHERE nombre = 'Diseño')),
                                                   ('Selenium', (SELECT id FROM categorias_habilidad WHERE nombre = 'QA'));




-- ---------------------------------------------------------------------
-- Usuario de prueba: Resource Manager
-- Contraseña: admin123 (mismo hash que el admin semilla, misma clave)
-- ---------------------------------------------------------------------
INSERT INTO usuarios (correo, contrasena_hash, nombre_completo, rol_organizacional, estado)
VALUES ('luis.pena@nexacorp.com',
        '$2b$10$5VvawdnDpSRdkKvzrBKun.sAfXs.HHrisVGgIuPFDIYGA3vxVG0Oe',
        'Luis Peña',
        'resource_manager', 'activo');

-- Su perfil de negocio (usa LAST_INSERT_ID() para no hardcodear el id)
INSERT INTO perfiles (usuario_id, cargo, disponibilidad_porcentaje, estado)
VALUES (LAST_INSERT_ID(), 'Resource Manager', 100, 'activo');

-- Se autoriza siguiendo el mismo flujo que todos: alguien (el admin) lo autoriza
-- Usamos el correo para encontrar su propio id recién creado, y el id=1 del admin como autorizador
INSERT INTO correos_autorizados (correo, autorizado_por_id, origen_carga, utilizado, fecha_uso)
VALUES ('luis.pena@nexacorp.com', 1, 'individual', TRUE, NOW());

-- 1. Colaborador de prueba (Ana Ríos) — será tanto colaboradora como el
-- futuro "PM" del proyecto de prueba
INSERT INTO usuarios (correo, contrasena_hash, nombre_completo, rol_organizacional, estado)
VALUES ('ana.rios@nexacorp.com',
        '$2b$10$5VvawdnDpSRdkKvzrBKun.sAfXs.HHrisVGgIuPFDIYGA3vxVG0Oe',
        'Ana Ríos', NULL, 'activo');

INSERT INTO perfiles (usuario_id, cargo, disponibilidad_porcentaje, estado)
VALUES (LAST_INSERT_ID(), 'Backend Developer', 100, 'activo');

INSERT INTO correos_autorizados (correo, autorizado_por_id, origen_carga, utilizado, fecha_uso)
VALUES ('ana.rios@nexacorp.com', 1, 'individual', TRUE, NOW());

-- Guarda este id para el siguiente bloque
SET @ana_perfil_id = (SELECT id FROM perfiles WHERE usuario_id = (SELECT id FROM usuarios WHERE correo = 'ana.rios@nexacorp.com'));

-- 2. Proyecto de prueba (replicando exactamente lo que hace ProyectoService.crear())
INSERT INTO proyectos (nombre, descripcion, tecnologias, estado, colaboradores_requeridos, fecha_inicio)
VALUES ('Portal Andes', 'Plataforma de gestión interna para BanCredit',
        JSON_ARRAY('Spring Boot', 'MySQL', 'Docker'),
        'planificacion', 3, CURDATE());

SET @proyecto_id = LAST_INSERT_ID();

-- 3. La asignación de PM — Ana queda como Project Manager de este proyecto,
-- exactamente el patrón que confirmamos: rol_en_proyecto NO es fijo, vive
-- aquí en asignaciones, no en usuarios
INSERT INTO asignaciones (proyecto_id, perfil_id, rol_en_proyecto, carga_porcentaje, estado, fecha_inicio)
VALUES (@proyecto_id, @ana_perfil_id, 'project_manager', 20, 'activa', CURDATE());


-- Un segundo colaborador, para poder probar el Create de verdad

INSERT INTO usuarios (correo, contrasena_hash, nombre_completo, rol_organizacional, estado)
VALUES ('diego.salazar@nexacorp.com',
        '$2b$10$5VvawdnDpSRdkKvzrBKun.sAfXs.HHrisVGgIuPFDIYGA3vxVG0Oe',
        'Diego Salazar', NULL, 'activo');

INSERT INTO perfiles (usuario_id, cargo, disponibilidad_porcentaje, estado)
VALUES (LAST_INSERT_ID(), 'Frontend Developer', 100, 'activo');

INSERT INTO correos_autorizados (correo, autorizado_por_id, origen_carga, utilizado, fecha_uso)
VALUES ('diego.salazar@nexacorp.com', 1, 'individual', TRUE, NOW());

-- 1. Proyecto nuevo. Modelo v7: Luis Peña es Resource Manager (puesto fijo)
--    y por eso NO lidera ni participa en proyectos. El PM de Aurora es
--    Tomás Herrera, que se inserta más abajo (sección 4), cuando ya existe.
INSERT INTO proyectos (nombre, descripcion, tecnologias, estado, colaboradores_requeridos, fecha_inicio)
VALUES ('App Móvil Aurora', 'App móvil de logística para Aurora Telecom',
        JSON_ARRAY('Kotlin', 'Firebase', 'REST API'),
        'activo', 4, CURDATE());

SET @aurora_id = LAST_INSERT_ID();

-- 2. Diego (ya tiene 20% en Portal Andes) entra también a este proyecto
-- con 45% más → su carga total sube a 65% → debería categorizarse "Óptimo"
SET @diego_perfil_id = (SELECT id FROM perfiles WHERE usuario_id = (SELECT id FROM usuarios WHERE correo = 'diego.salazar@nexacorp.com'));

INSERT INTO asignaciones (proyecto_id, perfil_id, rol_en_proyecto, carga_porcentaje, estado, fecha_inicio)
VALUES (@aurora_id, @diego_perfil_id, 'colaborador', 45, 'activa', CURDATE());



SET @ana_id = (SELECT id FROM perfiles WHERE usuario_id = (SELECT id FROM usuarios WHERE correo = 'ana.rios@nexacorp.com'));
SET @diego_id = (SELECT id FROM perfiles WHERE usuario_id = (SELECT id FROM usuarios WHERE correo = 'diego.salazar@nexacorp.com'));

-- Ana declara 2 habilidades (sin validar todavía)
INSERT INTO perfil_habilidad (perfil_id, habilidad_id, nivel)
VALUES
    (@ana_id, (SELECT id FROM habilidades WHERE nombre='Java'), 4),
    (@ana_id, (SELECT id FROM habilidades WHERE nombre='Spring Boot'), 4);

-- Diego declara 2 habilidades distintas
INSERT INTO perfil_habilidad (perfil_id, habilidad_id, nivel)
VALUES
    (@diego_id, (SELECT id FROM habilidades WHERE nombre='React'), 3),
    (@diego_id, (SELECT id FROM habilidades WHERE nombre='Docker'), 2);

-- ################ 2) ENTREGABLES ################
USE skillbridge_db;
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
--  - No hay nota: la entrega se revisa por ESTADOS. entregas.estado es
--    'enviado' (por revisar) | 'completado' | 'requiere_ajustes' |
--    'rehacer', y el PM deja siempre un comentario (comentario_pm). Con
--    requiere_ajustes/rehacer el colaborador vuelve a entregar y la
--    entrega regresa a 'enviado'; 'completado' la deja cerrada.
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
                             estado              ENUM('activo','cancelado') NOT NULL DEFAULT 'activo',
                             fecha_creacion      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                             CONSTRAINT fk_entregable_proyecto FOREIGN KEY (proyecto_id) REFERENCES proyectos(id) ON DELETE CASCADE,
                             CONSTRAINT fk_entregable_creador FOREIGN KEY (creado_por_id) REFERENCES perfiles(id),
                             CONSTRAINT ck_entregable_fechas CHECK (fecha_cierre > fecha_apertura)
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
                          estado              ENUM('enviado','completado','requiere_ajustes','rehacer') NOT NULL DEFAULT 'enviado',
                          comentario_pm       TEXT NULL,
                          revisado_por_id     BIGINT UNSIGNED NULL,
                          fecha_revision      DATETIME NULL,
                          CONSTRAINT fk_entrega_entregable FOREIGN KEY (entregable_id) REFERENCES entregables(id) ON DELETE CASCADE,
                          CONSTRAINT fk_entrega_perfil FOREIGN KEY (perfil_id) REFERENCES perfiles(id),
                          CONSTRAINT fk_entrega_revisor FOREIGN KEY (revisado_por_id) REFERENCES perfiles(id),
                          CONSTRAINT uq_entrega_entregable_perfil UNIQUE (entregable_id, perfil_id)
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
                      'El PM revisó tu entrega: completado, requiere ajustes o rehacer' AS descripcion) AS tmp
WHERE NOT EXISTS (SELECT 1 FROM tipos_notificacion WHERE codigo = 'entregable_revisado');

SET FOREIGN_KEY_CHECKS = 1;

-- ################ 3) DOBLE FACTOR ################
USE skillbridge_db;
-- Doble factor de autenticacion (TOTP). La app tambien ejecuta este CREATE
-- al arrancar (idempotente, ver config/Inicializador2fa.java); se deja aqui
-- para quien prefiera aplicar el esquema a mano (spring.jpa.hibernate.ddl-auto=none).
-- Sin FOREIGN KEY a proposito: evita el Error 1215 por diferencia de tipo
-- entre motores; las filas huerfanas son inocuas.
CREATE TABLE IF NOT EXISTS usuarios_2fa (
                                            usuario_id BIGINT       NOT NULL,
                                            secreto    VARCHAR(64)  NOT NULL,
    activo     TINYINT(1)   NOT NULL DEFAULT 0,
    creado_en  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- Último paso TOTP (30 s) aceptado: cada código sirve una sola vez.
    ultimo_paso BIGINT      NULL,
    PRIMARY KEY (usuario_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ################ 4) DATOS DEMO ################
USE skillbridge_db;
-- =====================================================================
-- SkillBridge AI — Datos de DEMO para presentación al Jefe de Práctica
-- =====================================================================
-- Requisitos:
--   1) Ejecutar PRIMERO skillbridge_db_v4_actualizado.sql (crea el
--      esquema completo + siembra base: catálogos, admin, Luis Peña,
--      Ana Ríos, Diego Salazar, Portal Andes, App Móvil Aurora).
--   2) Ejecutar ESTE script después, sobre esa misma base.
--
-- Qué agrega este script (para que la app se vea "viva" en la demo):
--   - 8 habilidades nuevas al catálogo (20 en total, 6 categorías)
--   - 1 Resource Manager más, 2 Project Managers, 9 colaboradores más
--     (16 usuarios en total contando los ya sembrados)
--   - 5 proyectos más (7 en total), cubriendo los 5 estados posibles
--     (planificacion, activo, en_pausa, completado) y con
--     proyecto_habilidad_requerida real para que AI Talent Matching
--     tenga con qué calcular
--   - Asignaciones activas E historial (proyecto completado)
--   - Un colaborador (Tomás Herrera) deliberadamente SOBRECARGADO
--     (115%) para poder mostrar la alerta de ocupación del RM
--   - Habilidades declaradas por perfil (algunas validadas, otras no) +
--     certificados de respaldo (vía link, opción A)
--   - Propuestas de AI Talent Matching: 2 pendientes + 1 aprobada +
--     1 rechazada (para mostrar las 2 pantallas del módulo)
--   - Hilos de foro con respuestas y una marcada como solución
--   - Eventos de calendario pasados y futuros (reunión, entregable,
--     hito), incluyendo uno "atrasado" a propósito
--   - Notificaciones ya generadas para varios perfiles
--   - 2 invitaciones pendientes (correos_autorizados sin usar) para
--     mostrar el estado "invitado" en administrador/usuarios.html
--   - ~14 filas de auditoria_logs para que administrador/auditoria.html
--     no se vea vacío
--
-- Contraseña de TODAS las cuentas nuevas: admin123 (mismo hash BCrypt
-- que ya usa el script base, para no tener que recordar 15 claves
-- distintas en la demo).
--
-- Fuera de alcance a propósito (no tienen pantalla en la app, ver
-- conversación sobre el mapeo de CRUDs): excepciones_carga,
-- comentarios_evento, resumenes_ia, chat_salas, chat_mensajes,
-- conocimiento_embeddings, recomendaciones_ia_log. Sembrarlas no se
-- vería en ninguna pantalla y solo agregaría ruido al script.
-- =====================================================================

SET FOREIGN_KEY_CHECKS = 0;

-- =====================================================================
-- 1. CATÁLOGO DE HABILIDADES — 8 nuevas (quedan 20 en total)
-- =====================================================================
INSERT INTO habilidades (nombre, categoria_id) VALUES
                                                   ('Angular',   (SELECT id FROM categorias_habilidad WHERE nombre = 'Frontend')),
                                                   ('Node.js',   (SELECT id FROM categorias_habilidad WHERE nombre = 'Backend')),
                                                   ('Kotlin',    (SELECT id FROM categorias_habilidad WHERE nombre = 'Backend')),
                                                   ('Python',    (SELECT id FROM categorias_habilidad WHERE nombre = 'Backend')),
                                                   ('AWS',       (SELECT id FROM categorias_habilidad WHERE nombre = 'DevOps & Cloud')),
                                                   ('Power BI',  (SELECT id FROM categorias_habilidad WHERE nombre = 'Datos')),
                                                   ('Adobe XD',  (SELECT id FROM categorias_habilidad WHERE nombre = 'Diseño')),
                                                   ('JMeter',    (SELECT id FROM categorias_habilidad WHERE nombre = 'QA'));

-- =====================================================================
-- 2. USUARIOS NUEVOS (perfiles + autorización de correo en el mismo
--    patrón que ya usa el script base)
-- =====================================================================

-- --- Resource Manager #2 -----------------------------------------------
INSERT INTO usuarios (correo, contrasena_hash, nombre_completo, rol_organizacional, estado)
VALUES ('camila.vidal@nexacorp.com',
        '$2b$10$5VvawdnDpSRdkKvzrBKun.sAfXs.HHrisVGgIuPFDIYGA3vxVG0Oe',
        'Camila Vidal', 'resource_manager', 'activo');
INSERT INTO perfiles (usuario_id, cargo, disponibilidad_porcentaje, estado)
VALUES (LAST_INSERT_ID(), 'Resource Manager', 100, 'activo');
INSERT INTO correos_autorizados (correo, autorizado_por_id, origen_carga, utilizado, fecha_uso)
VALUES ('camila.vidal@nexacorp.com', 1, 'individual', TRUE, NOW());

-- --- Project Managers (rol_organizacional NULL: el rol de PM es
--     contextual, vive en asignaciones.rol_en_proyecto) --------------
INSERT INTO usuarios (correo, contrasena_hash, nombre_completo, rol_organizacional, estado)
VALUES ('jorge.paredes@nexacorp.com',
        '$2b$10$5VvawdnDpSRdkKvzrBKun.sAfXs.HHrisVGgIuPFDIYGA3vxVG0Oe',
        'Jorge Paredes', NULL, 'activo');
INSERT INTO perfiles (usuario_id, cargo, disponibilidad_porcentaje, experiencia_anios, estado)
VALUES (LAST_INSERT_ID(), 'Project Manager', 100, 6, 'activo');
INSERT INTO correos_autorizados (correo, autorizado_por_id, origen_carga, utilizado, fecha_uso)
VALUES ('jorge.paredes@nexacorp.com', 1, 'individual', TRUE, NOW());

INSERT INTO usuarios (correo, contrasena_hash, nombre_completo, rol_organizacional, estado)
VALUES ('mariana.solis@nexacorp.com',
        '$2b$10$5VvawdnDpSRdkKvzrBKun.sAfXs.HHrisVGgIuPFDIYGA3vxVG0Oe',
        'Mariana Solís', NULL, 'activo');
INSERT INTO perfiles (usuario_id, cargo, disponibilidad_porcentaje, experiencia_anios, estado)
VALUES (LAST_INSERT_ID(), 'Project Manager', 100, 5, 'activo');
INSERT INTO correos_autorizados (correo, autorizado_por_id, origen_carga, utilizado, fecha_uso)
VALUES ('mariana.solis@nexacorp.com', 1, 'individual', TRUE, NOW());

-- --- Colaboradores (9) --------------------------------------------------
INSERT INTO usuarios (correo, contrasena_hash, nombre_completo, rol_organizacional, estado) VALUES
                                                                                                ('brian.castro@nexacorp.com',   '$2b$10$5VvawdnDpSRdkKvzrBKun.sAfXs.HHrisVGgIuPFDIYGA3vxVG0Oe', 'Brian Castro',   NULL, 'activo'),
                                                                                                ('fiorella.torres@nexacorp.com','$2b$10$5VvawdnDpSRdkKvzrBKun.sAfXs.HHrisVGgIuPFDIYGA3vxVG0Oe', 'Fiorella Torres',NULL, 'activo'),
                                                                                                ('eduardo.campos@nexacorp.com', '$2b$10$5VvawdnDpSRdkKvzrBKun.sAfXs.HHrisVGgIuPFDIYGA3vxVG0Oe', 'Eduardo Campos', NULL, 'activo'),
                                                                                                ('gabriel.chavez@nexacorp.com', '$2b$10$5VvawdnDpSRdkKvzrBKun.sAfXs.HHrisVGgIuPFDIYGA3vxVG0Oe', 'Gabriel Chávez', NULL, 'activo'),
                                                                                                ('helena.ponce@nexacorp.com',   '$2b$10$5VvawdnDpSRdkKvzrBKun.sAfXs.HHrisVGgIuPFDIYGA3vxVG0Oe', 'Helena Ponce',   NULL, 'activo'),
                                                                                                ('ivan.rojas@nexacorp.com',     '$2b$10$5VvawdnDpSRdkKvzrBKun.sAfXs.HHrisVGgIuPFDIYGA3vxVG0Oe', 'Iván Rojas',     NULL, 'activo'),
                                                                                                ('karla.medina@nexacorp.com',   '$2b$10$5VvawdnDpSRdkKvzrBKun.sAfXs.HHrisVGgIuPFDIYGA3vxVG0Oe', 'Karla Medina',   NULL, 'activo'),
                                                                                                ('renzo.fernandez@nexacorp.com','$2b$10$5VvawdnDpSRdkKvzrBKun.sAfXs.HHrisVGgIuPFDIYGA3vxVG0Oe', 'Renzo Fernández',NULL, 'activo'),
                                                                                                ('tomas.herrera@nexacorp.com',  '$2b$10$5VvawdnDpSRdkKvzrBKun.sAfXs.HHrisVGgIuPFDIYGA3vxVG0Oe', 'Tomás Herrera',  NULL, 'activo');

INSERT INTO perfiles (usuario_id, cargo, disponibilidad_porcentaje, experiencia_anios, estado)
VALUES
    ((SELECT id FROM usuarios WHERE correo='brian.castro@nexacorp.com'),   'Frontend Developer',    100, 3, 'activo'),
    ((SELECT id FROM usuarios WHERE correo='fiorella.torres@nexacorp.com'),'DevOps Engineer',       100, 4, 'activo'),
    ((SELECT id FROM usuarios WHERE correo='eduardo.campos@nexacorp.com'), 'Data Analyst',          100, 2, 'activo'),
    ((SELECT id FROM usuarios WHERE correo='gabriel.chavez@nexacorp.com'), 'Backend Developer',     100, 5, 'activo'),
    ((SELECT id FROM usuarios WHERE correo='helena.ponce@nexacorp.com'),   'UX/UI Designer',        100, 3, 'activo'),
    ((SELECT id FROM usuarios WHERE correo='ivan.rojas@nexacorp.com'),     'Backend/DevOps Engineer',100,6, 'activo'),
    ((SELECT id FROM usuarios WHERE correo='karla.medina@nexacorp.com'),   'QA Engineer',           100, 2, 'activo'),
    ((SELECT id FROM usuarios WHERE correo='renzo.fernandez@nexacorp.com'),'Data Engineer',         100, 4, 'activo'),
    ((SELECT id FROM usuarios WHERE correo='tomas.herrera@nexacorp.com'),  'Backend Developer',     100, 7, 'activo');

INSERT INTO correos_autorizados (correo, autorizado_por_id, origen_carga, utilizado, fecha_uso)
SELECT correo, 1, 'masiva_csv', TRUE, NOW() FROM usuarios
WHERE correo IN ('brian.castro@nexacorp.com','fiorella.torres@nexacorp.com','eduardo.campos@nexacorp.com',
                 'gabriel.chavez@nexacorp.com','helena.ponce@nexacorp.com','ivan.rojas@nexacorp.com',
                 'karla.medina@nexacorp.com','renzo.fernandez@nexacorp.com','tomas.herrera@nexacorp.com');

-- --- Invitaciones pendientes (para ver el estado "invitado" en
--     administrador/usuarios.html — nadie completó su registro aún) ---
INSERT INTO correos_autorizados (correo, autorizado_por_id, origen_carga, utilizado, fecha_uso) VALUES
                                                                                                    ('nuevo.ingreso1@nexacorp.com', 1, 'individual', FALSE, NULL),
                                                                                                    ('nuevo.ingreso2@nexacorp.com', 1, 'masiva_csv', FALSE, NULL);

-- =====================================================================
-- 3. VARIABLES DE APOYO (ids de perfil por correo, para no repetir
--    subconsultas larguísimas en cada INSERT de abajo)
-- =====================================================================
SET @pid_admin   = (SELECT p.id FROM perfiles p JOIN usuarios u ON u.id=p.usuario_id WHERE u.correo='admin@nexacorp.com');
SET @pid_luis    = (SELECT p.id FROM perfiles p JOIN usuarios u ON u.id=p.usuario_id WHERE u.correo='luis.pena@nexacorp.com');
SET @pid_camila  = (SELECT p.id FROM perfiles p JOIN usuarios u ON u.id=p.usuario_id WHERE u.correo='camila.vidal@nexacorp.com');
SET @pid_ana     = (SELECT p.id FROM perfiles p JOIN usuarios u ON u.id=p.usuario_id WHERE u.correo='ana.rios@nexacorp.com');
SET @pid_diego   = (SELECT p.id FROM perfiles p JOIN usuarios u ON u.id=p.usuario_id WHERE u.correo='diego.salazar@nexacorp.com');
SET @pid_jorge   = (SELECT p.id FROM perfiles p JOIN usuarios u ON u.id=p.usuario_id WHERE u.correo='jorge.paredes@nexacorp.com');
SET @pid_mariana = (SELECT p.id FROM perfiles p JOIN usuarios u ON u.id=p.usuario_id WHERE u.correo='mariana.solis@nexacorp.com');
SET @pid_brian   = (SELECT p.id FROM perfiles p JOIN usuarios u ON u.id=p.usuario_id WHERE u.correo='brian.castro@nexacorp.com');
SET @pid_fiorella= (SELECT p.id FROM perfiles p JOIN usuarios u ON u.id=p.usuario_id WHERE u.correo='fiorella.torres@nexacorp.com');
SET @pid_eduardo = (SELECT p.id FROM perfiles p JOIN usuarios u ON u.id=p.usuario_id WHERE u.correo='eduardo.campos@nexacorp.com');
SET @pid_gabriel = (SELECT p.id FROM perfiles p JOIN usuarios u ON u.id=p.usuario_id WHERE u.correo='gabriel.chavez@nexacorp.com');
SET @pid_helena  = (SELECT p.id FROM perfiles p JOIN usuarios u ON u.id=p.usuario_id WHERE u.correo='helena.ponce@nexacorp.com');
SET @pid_ivan    = (SELECT p.id FROM perfiles p JOIN usuarios u ON u.id=p.usuario_id WHERE u.correo='ivan.rojas@nexacorp.com');
SET @pid_karla   = (SELECT p.id FROM perfiles p JOIN usuarios u ON u.id=p.usuario_id WHERE u.correo='karla.medina@nexacorp.com');
SET @pid_renzo   = (SELECT p.id FROM perfiles p JOIN usuarios u ON u.id=p.usuario_id WHERE u.correo='renzo.fernandez@nexacorp.com');
SET @pid_tomas   = (SELECT p.id FROM perfiles p JOIN usuarios u ON u.id=p.usuario_id WHERE u.correo='tomas.herrera@nexacorp.com');

SET @pid_portal_andes  = (SELECT id FROM proyectos WHERE nombre = 'Portal Andes');
SET @pid_app_aurora    = (SELECT id FROM proyectos WHERE nombre = 'App Móvil Aurora');

-- =====================================================================
-- 4. PROYECTOS NUEVOS (5) — cubren planificacion / activo / en_pausa /
--    completado, cada uno con proyecto_habilidad_requerida real para
--    que AI Talent Matching tenga con qué calcular (independiente del
--    JSON libre de "tecnologias")
-- =====================================================================

-- --- Proyecto 3: Migración a Kubernetes (activo) -----------------------
INSERT INTO proyectos (nombre, descripcion, tecnologias, estado, colaboradores_requeridos, fecha_inicio, fecha_fin_estimada)
VALUES ('Migración a Kubernetes',
        'Migrar los servicios monolíticos actuales a contenedores orquestados con Kubernetes en AWS.',
        JSON_ARRAY('Docker','Kubernetes','Terraform','AWS'),
        'activo', 2, CURDATE() - INTERVAL 45 DAY, CURDATE() + INTERVAL 30 DAY);
SET @pr_k8s = LAST_INSERT_ID();

INSERT INTO proyecto_habilidad_requerida (proyecto_id, habilidad_id, nivel_requerido) VALUES
                                                                                          (@pr_k8s, (SELECT id FROM habilidades WHERE nombre='Docker'), 4),
                                                                                          (@pr_k8s, (SELECT id FROM habilidades WHERE nombre='Kubernetes'), 4),
                                                                                          (@pr_k8s, (SELECT id FROM habilidades WHERE nombre='Terraform'), 3),
                                                                                          (@pr_k8s, (SELECT id FROM habilidades WHERE nombre='AWS'), 3);

INSERT INTO asignaciones (proyecto_id, perfil_id, rol_en_proyecto, carga_porcentaje, estado, fecha_inicio) VALUES
                                                                                                               (@pr_k8s, @pid_jorge,    'project_manager', 20, 'activa', CURDATE() - INTERVAL 45 DAY),
                                                                                                               (@pr_k8s, @pid_fiorella, 'colaborador',     60, 'activa', CURDATE() - INTERVAL 40 DAY),
                                                                                                               (@pr_k8s, @pid_ivan,     'colaborador',     55, 'activa', CURDATE() - INTERVAL 40 DAY),
                                                                                                               -- Tomás Herrera: además lidera App Móvil Aurora (35%, más abajo):
                                                                                                               -- 60 + 35 = 95% de carga total, para la demo de "cerca del límite".
                                                                                                               (@pr_k8s, @pid_tomas,    'colaborador',     60, 'activa', CURDATE() - INTERVAL 20 DAY);

-- --- Proyecto 4: Dashboard de Analítica de Ventas (en_pausa) -----------
INSERT INTO proyectos (nombre, descripcion, tecnologias, estado, colaboradores_requeridos, fecha_inicio, fecha_fin_estimada)
VALUES ('Dashboard de Analítica de Ventas',
        'Tablero ejecutivo con métricas de ventas por región, conectado a PostgreSQL, en pausa por prioridades del cliente.',
        JSON_ARRAY('React','PostgreSQL','Power BI'),
        'en_pausa', 2, CURDATE() - INTERVAL 60 DAY, CURDATE() + INTERVAL 15 DAY);
SET @pr_dash = LAST_INSERT_ID();

INSERT INTO proyecto_habilidad_requerida (proyecto_id, habilidad_id, nivel_requerido) VALUES
                                                                                          (@pr_dash, (SELECT id FROM habilidades WHERE nombre='React'), 3),
                                                                                          (@pr_dash, (SELECT id FROM habilidades WHERE nombre='PostgreSQL'), 3),
                                                                                          (@pr_dash, (SELECT id FROM habilidades WHERE nombre='Power BI'), 4);

INSERT INTO asignaciones (proyecto_id, perfil_id, rol_en_proyecto, carga_porcentaje, estado, fecha_inicio) VALUES
                                                                                                               (@pr_dash, @pid_mariana, 'project_manager', 20, 'activa', CURDATE() - INTERVAL 60 DAY),
                                                                                                               (@pr_dash, @pid_brian,   'colaborador',     50, 'activa', CURDATE() - INTERVAL 55 DAY),
                                                                                                               (@pr_dash, @pid_eduardo, 'colaborador',     40, 'activa', CURDATE() - INTERVAL 55 DAY);

-- --- Proyecto 5: Plataforma de Certificaciones Internas (completado,
--     para poblar el HISTORIAL en colaborador/perfil.html) ------------
INSERT INTO proyectos (nombre, descripcion, tecnologias, estado, colaboradores_requeridos, fecha_inicio, fecha_fin_estimada)
VALUES ('Plataforma de Certificaciones Internas',
        'Portal interno donde los colaboradores suben y validan certificaciones técnicas. Entregado y cerrado.',
        JSON_ARRAY('Angular','Java','MySQL'),
        'completado', 2, CURDATE() - INTERVAL 180 DAY, CURDATE() - INTERVAL 30 DAY);
SET @pr_cert = LAST_INSERT_ID();

INSERT INTO proyecto_habilidad_requerida (proyecto_id, habilidad_id, nivel_requerido) VALUES
                                                                                          (@pr_cert, (SELECT id FROM habilidades WHERE nombre='Angular'), 3),
                                                                                          (@pr_cert, (SELECT id FROM habilidades WHERE nombre='Java'), 3),
                                                                                          (@pr_cert, (SELECT id FROM habilidades WHERE nombre='MySQL'), 3);

INSERT INTO asignaciones (proyecto_id, perfil_id, rol_en_proyecto, carga_porcentaje, estado, fecha_inicio, fecha_fin) VALUES
                                                                                                                          (@pr_cert, @pid_ana,     'project_manager', 15, 'finalizada', CURDATE() - INTERVAL 180 DAY, CURDATE() - INTERVAL 30 DAY),
                                                                                                                          (@pr_cert, @pid_gabriel, 'colaborador',     50, 'finalizada', CURDATE() - INTERVAL 175 DAY, CURDATE() - INTERVAL 30 DAY),
                                                                                                                          (@pr_cert, @pid_renzo,   'colaborador',     50, 'finalizada', CURDATE() - INTERVAL 175 DAY, CURDATE() - INTERVAL 30 DAY);

-- --- Proyecto 6: Automatización de Pruebas QA (activo) -----------------
INSERT INTO proyectos (nombre, descripcion, tecnologias, estado, colaboradores_requeridos, fecha_inicio, fecha_fin_estimada)
VALUES ('Automatización de Pruebas QA',
        'Suite de pruebas automatizadas end-to-end con Selenium y pruebas de carga con JMeter.',
        JSON_ARRAY('Selenium','JMeter','Python'),
        'activo', 2, CURDATE() - INTERVAL 25 DAY, CURDATE() + INTERVAL 40 DAY);
SET @pr_qa = LAST_INSERT_ID();

INSERT INTO proyecto_habilidad_requerida (proyecto_id, habilidad_id, nivel_requerido) VALUES
                                                                                          (@pr_qa, (SELECT id FROM habilidades WHERE nombre='Selenium'), 3),
                                                                                          (@pr_qa, (SELECT id FROM habilidades WHERE nombre='JMeter'), 3),
                                                                                          (@pr_qa, (SELECT id FROM habilidades WHERE nombre='Python'), 2);

INSERT INTO asignaciones (proyecto_id, perfil_id, rol_en_proyecto, carga_porcentaje, estado, fecha_inicio) VALUES
                                                                                                               (@pr_qa, @pid_mariana, 'project_manager', 15, 'activa', CURDATE() - INTERVAL 25 DAY),
                                                                                                               (@pr_qa, @pid_karla,   'colaborador',     60, 'activa', CURDATE() - INTERVAL 20 DAY),
                                                                                                               -- Este es el colaborador que llega vía AI Talent Matching aprobado
                                                                                                               -- (ver sección 8): Eduardo Campos, propuesto por Mariana y aprobado
                                                                                                               -- por Camila Vidal.
                                                                                                               (@pr_qa, @pid_eduardo, 'colaborador',     30, 'activa', CURDATE() - INTERVAL 10 DAY);

-- --- Proyecto 7: App de Onboarding (planificacion, SIN colaboradores
--     todavía: es la vacante abierta para la demo de AI Talent
--     Matching) --------------------------------------------------------
INSERT INTO proyectos (nombre, descripcion, tecnologias, estado, colaboradores_requeridos, fecha_inicio)
VALUES ('App de Onboarding',
        'Aplicación móvil para guiar el primer mes de un nuevo colaborador (checklist, contactos, documentos).',
        JSON_ARRAY('React','Node.js','Figma'),
        'planificacion', 2, CURDATE() + INTERVAL 15 DAY);
SET @pr_onb = LAST_INSERT_ID();

INSERT INTO proyecto_habilidad_requerida (proyecto_id, habilidad_id, nivel_requerido) VALUES
                                                                                          (@pr_onb, (SELECT id FROM habilidades WHERE nombre='React'), 3),
                                                                                          (@pr_onb, (SELECT id FROM habilidades WHERE nombre='Node.js'), 3),
                                                                                          (@pr_onb, (SELECT id FROM habilidades WHERE nombre='Figma'), 2);

INSERT INTO asignaciones (proyecto_id, perfil_id, rol_en_proyecto, carga_porcentaje, estado, fecha_inicio) VALUES
    (@pr_onb, @pid_jorge, 'project_manager', 10, 'activa', CURDATE());

-- --- Requisitos también para los 2 proyectos YA sembrados en el script
--     base (Portal Andes y App Móvil Aurora), que no traían
--     proyecto_habilidad_requerida propia ------------------------------
INSERT INTO proyecto_habilidad_requerida (proyecto_id, habilidad_id, nivel_requerido) VALUES
                                                                                          (@pid_portal_andes, (SELECT id FROM habilidades WHERE nombre='Spring Boot'), 4),
                                                                                          (@pid_portal_andes, (SELECT id FROM habilidades WHERE nombre='MySQL'), 3),
                                                                                          (@pid_portal_andes, (SELECT id FROM habilidades WHERE nombre='Docker'), 2),
                                                                                          (@pid_app_aurora,   (SELECT id FROM habilidades WHERE nombre='Kotlin'), 3);

-- Tomás Herrera LIDERA App Móvil Aurora (35%) y a la vez es colaborador
-- en Migración a Kubernetes (60%): es el caso "Miembro con dos papeles"
-- del modelo v7 (ve Aurora en su vista Project Manager y Kubernetes en su
-- vista Colaborador). Su carga total queda en 95%: cerca del límite, pero
-- nunca por encima del tope duro de 100%.
INSERT INTO asignaciones (proyecto_id, perfil_id, rol_en_proyecto, carga_porcentaje, estado, fecha_inicio)
VALUES (@pid_app_aurora, @pid_tomas, 'project_manager', 35, 'activa', CURDATE() - INTERVAL 15 DAY);

-- =====================================================================
-- 5. HABILIDADES DECLARADAS POR PERFIL (perfil_habilidad) — algunas
--    validadas por un Resource Manager, otras esperando validación
-- =====================================================================
INSERT INTO perfil_habilidad (perfil_id, habilidad_id, nivel, validado_por_id) VALUES
                                                                                   (@pid_jorge,    (SELECT id FROM habilidades WHERE nombre='Kubernetes'), 4, @pid_camila),
                                                                                   (@pid_jorge,    (SELECT id FROM habilidades WHERE nombre='Terraform'),  3, NULL),
                                                                                   (@pid_mariana,  (SELECT id FROM habilidades WHERE nombre='React'),      3, NULL),
                                                                                   (@pid_mariana,  (SELECT id FROM habilidades WHERE nombre='Power BI'),   3, @pid_luis),
                                                                                   (@pid_brian,    (SELECT id FROM habilidades WHERE nombre='React'),      4, @pid_luis),
                                                                                   (@pid_brian,    (SELECT id FROM habilidades WHERE nombre='Angular'),    3, NULL),
                                                                                   (@pid_fiorella, (SELECT id FROM habilidades WHERE nombre='Docker'),     4, @pid_camila),
                                                                                   (@pid_fiorella, (SELECT id FROM habilidades WHERE nombre='Kubernetes'), 4, @pid_camila),
                                                                                   (@pid_fiorella, (SELECT id FROM habilidades WHERE nombre='Terraform'),  3, NULL),
                                                                                   (@pid_fiorella, (SELECT id FROM habilidades WHERE nombre='AWS'),        4, @pid_camila),
                                                                                   (@pid_eduardo,  (SELECT id FROM habilidades WHERE nombre='PostgreSQL'), 3, NULL),
                                                                                   (@pid_eduardo,  (SELECT id FROM habilidades WHERE nombre='Power BI'),   4, @pid_luis),
                                                                                   (@pid_eduardo,  (SELECT id FROM habilidades WHERE nombre='Python'),     3, NULL),
                                                                                   (@pid_gabriel,  (SELECT id FROM habilidades WHERE nombre='Java'),       4, @pid_camila),
                                                                                   (@pid_gabriel,  (SELECT id FROM habilidades WHERE nombre='MySQL'),      3, @pid_camila),
                                                                                   (@pid_gabriel,  (SELECT id FROM habilidades WHERE nombre='Angular'),    2, NULL),
                                                                                   (@pid_helena,   (SELECT id FROM habilidades WHERE nombre='Figma'),      5, @pid_luis),
                                                                                   (@pid_helena,   (SELECT id FROM habilidades WHERE nombre='Adobe XD'),   4, @pid_luis),
                                                                                   (@pid_helena,   (SELECT id FROM habilidades WHERE nombre='React'),      3, NULL),
                                                                                   (@pid_ivan,     (SELECT id FROM habilidades WHERE nombre='Docker'),     3, @pid_camila),
                                                                                   (@pid_ivan,     (SELECT id FROM habilidades WHERE nombre='Kubernetes'), 3, NULL),
                                                                                   (@pid_ivan,     (SELECT id FROM habilidades WHERE nombre='Java'),       3, NULL),
                                                                                   (@pid_karla,    (SELECT id FROM habilidades WHERE nombre='Selenium'),   4, @pid_camila),
                                                                                   (@pid_karla,    (SELECT id FROM habilidades WHERE nombre='JMeter'),     3, NULL),
                                                                                   (@pid_karla,    (SELECT id FROM habilidades WHERE nombre='React'),      2, NULL),
                                                                                   (@pid_renzo,    (SELECT id FROM habilidades WHERE nombre='PostgreSQL'), 4, @pid_luis),
                                                                                   (@pid_renzo,    (SELECT id FROM habilidades WHERE nombre='MySQL'),      4, @pid_luis),
                                                                                   (@pid_renzo,    (SELECT id FROM habilidades WHERE nombre='Python'),     3, NULL),
                                                                                   (@pid_tomas,    (SELECT id FROM habilidades WHERE nombre='Java'),       5, @pid_camila),
                                                                                   (@pid_tomas,    (SELECT id FROM habilidades WHERE nombre='Spring Boot'),4, @pid_camila),
                                                                                   (@pid_tomas,    (SELECT id FROM habilidades WHERE nombre='Docker'),     3, NULL);

-- Certificados de respaldo (Opción A: link externo) para algunas de las
-- habilidades recién declaradas.
INSERT INTO certificados_habilidad (perfil_id, habilidad_id, nombre_archivo, url_archivo) VALUES
                                                                                              (@pid_helena,   (SELECT id FROM habilidades WHERE nombre='Figma'),      'Certificado Figma Academy',            'https://www.figma.com/academy/certificado-demo'),
                                                                                              (@pid_fiorella, (SELECT id FROM habilidades WHERE nombre='Kubernetes'), 'CKA - Certified Kubernetes Admin',      'https://training.linuxfoundation.org/certification/cka-demo'),
                                                                                              (@pid_renzo,    (SELECT id FROM habilidades WHERE nombre='PostgreSQL'), 'PostgreSQL Professional Certification', 'https://www.enterprisedb.com/certificacion-demo'),
                                                                                              (@pid_karla,    (SELECT id FROM habilidades WHERE nombre='Selenium'),   'Selenium WebDriver Certified Tester',   'https://www.udemy.com/certificate/selenium-demo');

-- =====================================================================
-- 6. FORO — hilos con respuestas, una marcada como solución
-- =====================================================================

-- Hilo 1: Migración a Kubernetes
INSERT INTO foro_publicaciones (proyecto_id, autor_id, titulo, contenido, etiquetas)
VALUES (@pr_k8s, @pid_fiorella, '¿Namespace por ambiente o por equipo?',
        'Estamos por definir la convención de namespaces del cluster. ¿Vamos por ambiente (dev/qa/prod) o por equipo/servicio? Quiero cerrar esto antes de crear los primeros manifests.',
        JSON_ARRAY('kubernetes','arquitectura'));
SET @hilo_k8s = LAST_INSERT_ID();

INSERT INTO foro_publicaciones (proyecto_id, autor_id, publicacion_padre_id, contenido)
VALUES (@pr_k8s, @pid_ivan, @hilo_k8s,
        'Yo iría por ambiente para no complicar el RBAC. Por equipo se vuelve difícil de mantener cuando crecen los microservicios.');

INSERT INTO foro_publicaciones (proyecto_id, autor_id, publicacion_padre_id, contenido, es_solucion)
VALUES (@pr_k8s, @pid_jorge, @hilo_k8s,
        'De acuerdo con Iván. Definimos namespaces por ambiente (dev/qa/prod) y usamos labels de equipo/servicio dentro de cada uno para filtrar con kubectl. Quedó documentado en el README del repo de infra.',
        TRUE);

-- Hilo 2: Automatización de Pruebas QA
INSERT INTO foro_publicaciones (proyecto_id, autor_id, titulo, contenido, etiquetas)
VALUES (@pr_qa, @pid_karla, 'Selenium Grid se cae al correr en paralelo x4',
        'Al correr la suite con 4 hilos en paralelo, el Grid se cae por memoria a los pocos minutos. ¿Alguien tuvo este problema con la config actual?',
        JSON_ARRAY('selenium','ci'));
SET @hilo_qa = LAST_INSERT_ID();

INSERT INTO foro_publicaciones (proyecto_id, autor_id, publicacion_padre_id, contenido)
VALUES (@pr_qa, @pid_eduardo, @hilo_qa,
        'A mí me pasó algo parecido corriendo reportes pesados en paralelo. ¿Cuánta RAM tiene el nodo del Grid?');

INSERT INTO foro_publicaciones (proyecto_id, autor_id, publicacion_padre_id, contenido, es_solucion)
VALUES (@pr_qa, @pid_mariana, @hilo_qa,
        'Bajamos a 2 hilos en paralelo y subimos la memoria del contenedor del Grid de 2GB a 4GB. Dejó de caerse. Quedó como configuración estándar para los pipelines de este proyecto.',
        TRUE);

-- Hilo 3: App Móvil Aurora (queda abierto, sin solución todavía)
INSERT INTO foro_publicaciones (proyecto_id, autor_id, titulo, contenido, etiquetas)
VALUES (@pid_app_aurora, @pid_diego, 'Dudas sobre autenticación con Firebase',
        '¿Usamos Firebase Auth directo desde la app o pasamos por nuestro backend para emitir el token? Quiero definirlo antes de avanzar con el login.',
        JSON_ARRAY('firebase','auth'));
SET @hilo_aurora = LAST_INSERT_ID();

INSERT INTO foro_publicaciones (proyecto_id, autor_id, publicacion_padre_id, contenido)
VALUES (@pid_app_aurora, @pid_tomas, @hilo_aurora,
        'Pasemos por nuestro backend para poder validar el rol del usuario antes de emitir el token final. Lo reviso con el equipo de seguridad y les confirmo esta semana.');

-- =====================================================================
-- 7. CALENDARIO — eventos pasados y futuros (reunión, entregable, hito),
--    incluyendo uno "atrasado" a propósito
-- =====================================================================

-- Migración a Kubernetes
INSERT INTO eventos_proyecto (proyecto_id, creado_por_id, tipo_id, titulo, descripcion, fecha_inicio, fecha_fin, estado, audiencia_id) VALUES
                                                                                                                                           (@pr_k8s, @pid_jorge, (SELECT id FROM tipos_evento WHERE codigo='hito'),
                                                                                                                                            'POC de namespaces aprobada', 'Prueba de concepto de la convención de namespaces, aprobada por el equipo.',
                                                                                                                                            NOW() - INTERVAL 20 DAY, NULL, 'cumplido', (SELECT id FROM tipos_audiencia WHERE codigo='todos')),
                                                                                                                                           (@pr_k8s, @pid_jorge, (SELECT id FROM tipos_evento WHERE codigo='reunion'),
                                                                                                                                            'Standup semanal de migración', 'Seguimiento semanal del avance de la migración a K8s.',
                                                                                                                                            NOW() + INTERVAL 3 DAY, NOW() + INTERVAL 3 DAY + INTERVAL 30 MINUTE, 'pendiente', (SELECT id FROM tipos_audiencia WHERE codigo='todos')),
                                                                                                                                           (@pr_k8s, @pid_jorge, (SELECT id FROM tipos_evento WHERE codigo='entregable'),
                                                                                                                                            'Cutover a producción', 'Corte definitivo del tráfico de producción hacia el nuevo cluster.',
                                                                                                                                            NOW() + INTERVAL 30 DAY, NULL, 'pendiente', (SELECT id FROM tipos_audiencia WHERE codigo='todos'));

-- Automatización de Pruebas QA
INSERT INTO eventos_proyecto (proyecto_id, creado_por_id, tipo_id, titulo, descripcion, fecha_inicio, fecha_fin, estado, audiencia_id) VALUES
                                                                                                                                           (@pr_qa, @pid_mariana, (SELECT id FROM tipos_evento WHERE codigo='reunion'),
                                                                                                                                            'Retro de automatización', 'Retrospectiva del primer sprint de automatización de pruebas.',
                                                                                                                                            NOW() - INTERVAL 7 DAY, NOW() - INTERVAL 7 DAY + INTERVAL 1 HOUR, 'cumplido', (SELECT id FROM tipos_audiencia WHERE codigo='todos')),
                                                                                                                                           (@pr_qa, @pid_mariana, (SELECT id FROM tipos_evento WHERE codigo='entregable'),
                                                                                                                                            'Suite E2E del checkout lista', 'Entrega de la suite E2E completa del flujo de checkout.',
                                                                                                                                            NOW() + INTERVAL 12 DAY, NULL, 'pendiente', (SELECT id FROM tipos_audiencia WHERE codigo='todos'));

-- Dashboard de Analítica (en_pausa) — un entregable que ya venció y
-- sigue pendiente: se marca "atrasado" a propósito para la demo.
INSERT INTO eventos_proyecto (proyecto_id, creado_por_id, tipo_id, titulo, descripcion, fecha_inicio, fecha_fin, estado, audiencia_id) VALUES
                                                                                                                                           (@pr_dash, @pid_mariana, (SELECT id FROM tipos_evento WHERE codigo='hito'),
                                                                                                                                            'Diseño de tablero aprobado', 'Mockups del dashboard aprobados por el cliente.',
                                                                                                                                            NOW() - INTERVAL 50 DAY, NULL, 'cumplido', (SELECT id FROM tipos_audiencia WHERE codigo='todos')),
                                                                                                                                           (@pr_dash, @pid_mariana, (SELECT id FROM tipos_evento WHERE codigo='entregable'),
                                                                                                                                            'Primer avance de tablero', 'Primer entregable funcional del dashboard, pendiente por la pausa del proyecto.',
                                                                                                                                            NOW() - INTERVAL 10 DAY, NULL, 'atrasado', (SELECT id FROM tipos_audiencia WHERE codigo='todos'));

-- Portal Andes (planificacion) — reunión de kickoff solo para el PM
INSERT INTO eventos_proyecto (proyecto_id, creado_por_id, tipo_id, titulo, descripcion, fecha_inicio, fecha_fin, estado, audiencia_id) VALUES
    (@pid_portal_andes, @pid_ana, (SELECT id FROM tipos_evento WHERE codigo='reunion'),
     'Kickoff interno con BanCredit', 'Reunión de alineamiento previa al kickoff oficial con el cliente.',
     NOW() + INTERVAL 5 DAY, NOW() + INTERVAL 5 DAY + INTERVAL 1 HOUR, 'pendiente', (SELECT id FROM tipos_audiencia WHERE codigo='solo_pm'));

-- App de Onboarding (planificacion) — reunión de discovery
INSERT INTO eventos_proyecto (proyecto_id, creado_por_id, tipo_id, titulo, descripcion, fecha_inicio, fecha_fin, estado, audiencia_id) VALUES
    (@pr_onb, @pid_jorge, (SELECT id FROM tipos_evento WHERE codigo='reunion'),
     'Discovery con RRHH', 'Levantamiento de requisitos del flujo de onboarding con el equipo de RRHH.',
     NOW() + INTERVAL 8 DAY, NOW() + INTERVAL 8 DAY + INTERVAL 1 HOUR, 'pendiente', (SELECT id FROM tipos_audiencia WHERE codigo='todos'));

-- =====================================================================
-- 8. AI TALENT MATCHING — propuestas_asignacion (2 pendientes,
--    1 aprobada, 1 rechazada)
-- =====================================================================

-- Pendiente 1: Jorge propone a Helena (Diseñadora, libre) para la
-- vacante de "App de Onboarding".
INSERT INTO propuestas_asignacion
(proyecto_id, candidato_perfil_id, solicitado_por_perfil_id, dedicacion_porcentaje,
 score_total, score_habilidades, score_experiencia, score_disponibilidad)
VALUES (@pr_onb, @pid_helena, @pid_jorge, 40, 88, 90, 80, 95);

-- Pendiente 2: Jorge también propone a Brian para la misma vacante,
-- como segunda opción.
INSERT INTO propuestas_asignacion
(proyecto_id, candidato_perfil_id, solicitado_por_perfil_id, dedicacion_porcentaje,
 score_total, score_habilidades, score_experiencia, score_disponibilidad)
VALUES (@pr_onb, @pid_brian, @pid_jorge, 35, 81, 85, 70, 90);

-- Aprobada: Mariana propuso a Eduardo para "Automatización de Pruebas
-- QA" hace unos días; Camila (RM) la aprobó, y esa aprobación es la
-- que generó la asignación real que ya está en la sección 4.
INSERT INTO propuestas_asignacion
(proyecto_id, candidato_perfil_id, solicitado_por_perfil_id, revisado_por_perfil_id, dedicacion_porcentaje,
 score_total, score_habilidades, score_experiencia, score_disponibilidad,
 estado, motivo_resolucion, fecha_solicitud, fecha_resolucion)
VALUES (@pr_qa, @pid_eduardo, @pid_mariana, @pid_camila, 30,
        75, 70, 65, 85,
        'aprobada', 'Buen fit para soporte de métricas de calidad del proyecto.',
        NOW() - INTERVAL 12 DAY, NOW() - INTERVAL 10 DAY);

-- Rechazada: Jorge intentó sumar a Tomás Herrera a "App de Onboarding"
-- (un tercer proyecto para él), pero Camila la rechazó porque Tomás ya
-- está sobrecargado (115% entre Kubernetes y Aurora).
INSERT INTO propuestas_asignacion
(proyecto_id, candidato_perfil_id, solicitado_por_perfil_id, revisado_por_perfil_id, dedicacion_porcentaje,
 score_total, score_habilidades, score_experiencia, score_disponibilidad,
 estado, motivo_resolucion, fecha_solicitud, fecha_resolucion)
VALUES (@pr_onb, @pid_tomas, @pid_jorge, @pid_camila, 25,
        70, 82, 90, 40,
        'rechazada', 'Tomás ya está en 115% de carga entre otros proyectos; no cumple el límite permitido.',
        NOW() - INTERVAL 6 DAY, NOW() - INTERVAL 5 DAY);

-- =====================================================================
-- 9. NOTIFICACIONES ya generadas (para que notificaciones.html no se
--    vea vacío al entrar con cualquier cuenta demo)
-- =====================================================================
INSERT INTO notificaciones (perfil_id, tipo_id, titulo, detalle, leida, canal, enlace_accion) VALUES
                                                                                                  (@pid_camila, (SELECT id FROM tipos_notificacion WHERE codigo='solicitud'),
                                                                                                   'Nueva propuesta de Talent Matching', 'Helena Ponce fue propuesta para "App de Onboarding" con 40% de dedicación.', FALSE, 'app', 'ai-talent-matching.html'),
                                                                                                  (@pid_camila, (SELECT id FROM tipos_notificacion WHERE codigo='solicitud'),
                                                                                                   'Nueva propuesta de Talent Matching', 'Brian Castro fue propuesto para "App de Onboarding" con 35% de dedicación.', FALSE, 'app', 'ai-talent-matching.html'),
                                                                                                  (@pid_mariana, (SELECT id FROM tipos_notificacion WHERE codigo='resultado_ia'),
                                                                                                   'Propuesta de Talent Matching aprobada', 'Eduardo Campos · Automatización de Pruebas QA. Buen fit para soporte de métricas de calidad del proyecto.', TRUE, 'app', 'ai-talent-matching.html'),
                                                                                                  (@pid_jorge, (SELECT id FROM tipos_notificacion WHERE codigo='resultado_ia'),
                                                                                                   'Propuesta de Talent Matching rechazada', 'Tomás Herrera · App de Onboarding. Tomás ya está en 115% de carga entre otros proyectos; no cumple el límite permitido.', FALSE, 'app', 'ai-talent-matching.html'),
                                                                                                  (@pid_tomas, (SELECT id FROM tipos_notificacion WHERE codigo='alerta'),
                                                                                                   'Estás por encima del límite de carga', 'Tu carga activa total es de 115%, por encima del límite configurado (100%).', FALSE, 'app_mail', NULL),
                                                                                                  (@pid_diego, (SELECT id FROM tipos_notificacion WHERE codigo='info'),
                                                                                                   'Fuiste asignado a un proyecto', 'Se te asignó al proyecto "App Móvil Aurora" con 45% de dedicación.', TRUE, 'app', 'proyectos.html'),
                                                                                                  (@pid_eduardo, (SELECT id FROM tipos_notificacion WHERE codigo='info'),
                                                                                                   'Fuiste asignado a un proyecto', 'Se te asignó al proyecto "Automatización de Pruebas QA" con 30% de dedicación.', FALSE, 'app', 'proyectos.html'),
                                                                                                  (@pid_gabriel, (SELECT id FROM tipos_notificacion WHERE codigo='info'),
                                                                                                   'Proyecto finalizado', 'El proyecto "Plataforma de Certificaciones Internas" fue marcado como completado.', TRUE, 'app', 'perfil.html');

-- =====================================================================
-- 10. PREFERENCIAS DE NOTIFICACIÓN (un par de ejemplos)
-- =====================================================================
INSERT INTO preferencias_notificacion (perfil_id, tipo_evento, canal) VALUES
                                                                          (@pid_ana,   'alertas_criticas',        'app_mail'),
                                                                          (@pid_ana,   'resumen_semanal',         'solo_mail'),
                                                                          (@pid_diego, 'solicitudes_aprobacion',  'app'),
                                                                          (@pid_tomas, 'alertas_criticas',        'app_mail');

-- =====================================================================
-- 11. AUDITORÍA — historial sintético para que administrador/auditoria
--     .html tenga contenido que mostrar en la demo (acciones y códigos
--     iguales a los que realmente usa AuditoriaService en el código)
-- =====================================================================
INSERT INTO auditoria_logs (usuario_id, accion, entidad_afectada, entidad_id, detalle, fecha) VALUES
                                                                                                  (1, 'USUARIO_INVITADO', 'correo_autorizado', NULL, 'Se autorizó el correo camila.vidal@nexacorp.com para autoregistro.', NOW() - INTERVAL 40 DAY),
                                                                                                  (1, 'USUARIO_INVITADO', 'correo_autorizado', NULL, 'Se autorizó el correo jorge.paredes@nexacorp.com para autoregistro.', NOW() - INTERVAL 40 DAY),
                                                                                                  (1, 'USUARIO_INVITADO', 'correo_autorizado', NULL, 'Se autorizó el correo mariana.solis@nexacorp.com para autoregistro.', NOW() - INTERVAL 40 DAY),
                                                                                                  ((SELECT id FROM usuarios WHERE correo='jorge.paredes@nexacorp.com'), 'PERFIL_HABILIDAD_AGREGADA', 'perfil_habilidad', @pid_jorge, 'Kubernetes · Avanzado', NOW() - INTERVAL 35 DAY),
                                                                                                  ((SELECT id FROM usuarios WHERE correo='fiorella.torres@nexacorp.com'), 'PERFIL_HABILIDAD_AGREGADA', 'perfil_habilidad', @pid_fiorella, 'AWS · Avanzado', NOW() - INTERVAL 34 DAY),
                                                                                                  ((SELECT id FROM usuarios WHERE correo='camila.vidal@nexacorp.com'), 'PERFIL_HABILIDAD_ACTUALIZADA', 'perfil_habilidad', @pid_fiorella, 'Validación de Kubernetes de Fiorella Torres.', NOW() - INTERVAL 33 DAY),
                                                                                                  ((SELECT id FROM usuarios WHERE correo='mariana.solis@nexacorp.com'), 'MATCHING_PROPUESTO', 'propuesta_asignacion', NULL, 'Eduardo Campos propuesto para "Automatización de Pruebas QA" (score 75%).', NOW() - INTERVAL 12 DAY),
                                                                                                  ((SELECT id FROM usuarios WHERE correo='camila.vidal@nexacorp.com'), 'MATCHING_APROBADO', 'propuesta_asignacion', NULL, 'Eduardo Campos · Automatización de Pruebas QA.', NOW() - INTERVAL 10 DAY),
                                                                                                  ((SELECT id FROM usuarios WHERE correo='jorge.paredes@nexacorp.com'), 'MATCHING_PROPUESTO', 'propuesta_asignacion', NULL, 'Tomás Herrera propuesto para "App de Onboarding" (score 70%).', NOW() - INTERVAL 6 DAY),
                                                                                                  ((SELECT id FROM usuarios WHERE correo='camila.vidal@nexacorp.com'), 'MATCHING_RECHAZADO', 'propuesta_asignacion', NULL, 'App de Onboarding.', NOW() - INTERVAL 5 DAY),
                                                                                                  ((SELECT id FROM usuarios WHERE correo='jorge.paredes@nexacorp.com'), 'MATCHING_PROPUESTO', 'propuesta_asignacion', NULL, 'Helena Ponce propuesta para "App de Onboarding" (score 88%).', NOW() - INTERVAL 2 DAY),
                                                                                                  ((SELECT id FROM usuarios WHERE correo='jorge.paredes@nexacorp.com'), 'MATCHING_PROPUESTO', 'propuesta_asignacion', NULL, 'Brian Castro propuesto para "App de Onboarding" (score 81%).', NOW() - INTERVAL 1 DAY),
                                                                                                  (1, 'USUARIO_ROL_CAMBIADO', 'usuario', (SELECT id FROM usuarios WHERE correo='camila.vidal@nexacorp.com'), 'Rol de camila.vidal@nexacorp.com actualizado.', NOW() - INTERVAL 40 DAY),
                                                                                                  (1, 'USUARIO_INVITADO', 'correo_autorizado', NULL, 'Se autorizó el correo nuevo.ingreso1@nexacorp.com para autoregistro.', NOW() - INTERVAL 2 DAY);

SET FOREIGN_KEY_CHECKS = 1;


-- ################ 5) FOROS v6 (migracion_v6_foros.sql + demo) ################
USE skillbridge_db;

-- ---------------------------------------------------------------------
-- 1. FORO_TOPICOS
--    color: clave de tono del design system (blue|violet|green|amber|red|
--    neutral), no un hex, para que respete tema claro/oscuro.
--    icono: id del sprite /img/icons.svg.
--    palabras_clave: separadas por coma, en minúscula y sin tildes.
-- ---------------------------------------------------------------------
CREATE TABLE foro_topicos (
                              id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                              codigo          VARCHAR(40) NOT NULL UNIQUE,
                              nombre          VARCHAR(80) NOT NULL,
                              descripcion     VARCHAR(255) NULL,
                              color           VARCHAR(20) NOT NULL DEFAULT 'neutral',
                              icono           VARCHAR(40) NOT NULL DEFAULT 'icon-message',
                              palabras_clave  TEXT NULL,
                              orden           TINYINT UNSIGNED NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO foro_topicos (codigo, nombre, descripcion, color, icono, palabras_clave, orden) VALUES
                                                                                                ('backend', 'Backend y APIs', 'Lógica del servidor, Spring Boot, servicios y APIs', 'blue', 'icon-cpu',
                                                                                                 'backend,java,spring,spring boot,api,rest,endpoint,controlador,controller,servicio,service,microservicio,jpa,hibernate,repositorio,kafka,json,excepcion,exception,nullpointer,maven,dependencia,lombok,dto', 1),
                                                                                                ('datos', 'Base de datos', 'Modelado, consultas SQL, MySQL, migraciones y rendimiento', 'amber', 'icon-database',
                                                                                                 'base de datos,bd,db,mysql,postgresql,postgres,sql,query,consulta,tabla,columna,llave foranea,fk,migracion,indice,rds,workbench,esquema,jdbc,join,procedimiento,trigger,dump,backup', 2),
                                                                                                ('devops', 'DevOps y nube', 'Despliegues, AWS, contenedores, CI/CD e infraestructura', 'green', 'icon-globe',
                                                                                                 'devops,aws,ec2,s3,docker,kubernetes,k8s,namespace,cluster,deploy,despliegue,desplegar,pipeline,ci,cd,jenkins,github actions,terraform,nube,cloud,contenedor,nginx,ssh,pem,instancia,vpc,security group,puerto,nohup,war,jar', 3),
                                                                                                ('frontend', 'Frontend e interfaz', 'Vistas, HTML/CSS/JS, Thymeleaf y componentes', 'violet', 'icon-monitor',
                                                                                                 'frontend,html,css,javascript,js,react,thymeleaf,vista,pantalla,boton,formulario,responsive,estilo,componente,navegador,fragmento,modal,dropdown,tabla html,bootstrap', 4),
                                                                                                ('qa', 'QA y pruebas', 'Pruebas manuales y automatizadas, bugs y regresiones', 'red', 'icon-check-circle',
                                                                                                 'qa,prueba,pruebas,test,testing,selenium,e2e,bug,bugs,regresion,automatizacion,junit,mockito,caso de prueba,grid,cobertura,reproducir,falla,flaky', 5),
                                                                                                ('seguridad', 'Seguridad y accesos', 'Login, roles, tokens, contraseñas y permisos', 'red', 'icon-shield',
                                                                                                 'seguridad,auth,autenticacion,autorizacion,login,token,jwt,firebase,contrasena,password,permiso,permisos,rol,roles,rbac,2fa,doble factor,sesion,oauth,cifrado,bcrypt,vulnerabilidad,interceptor', 6),
                                                                                                ('diseno', 'Diseño UX/UI', 'Mockups, prototipos, usabilidad y accesibilidad', 'violet', 'icon-eye',
                                                                                                 'diseno,figma,mockup,mockups,prototipo,ux,ui,usabilidad,wireframe,paleta,tipografia,accesibilidad,iconos,maqueta', 7),
                                                                                                ('gestion', 'Gestión del proyecto', 'Reuniones, plazos, alcance, clientes y coordinación', 'neutral', 'icon-briefcase',
                                                                                                 'gestion,reunion,cliente,plazo,fecha limite,entrega,entregable,sprint,alcance,requerimiento,requisito,cronograma,prioridad,riesgo,presupuesto,kickoff,retro,retrospectiva,daily,planificacion,acta,coordinacion,stakeholder', 8),
                                                                                                ('general', 'General', 'Todo lo que no encaja en otro tópico', 'neutral', 'icon-message',
                                                                                                 NULL, 9);

-- ---------------------------------------------------------------------
-- 2. FORO_PUBLICACIONES — columnas nuevas
-- ---------------------------------------------------------------------
ALTER TABLE foro_publicaciones
    ADD COLUMN topico_id       BIGINT UNSIGNED NULL AFTER titulo,
    ADD COLUMN respuesta_a_id  BIGINT UNSIGNED NULL AFTER publicacion_padre_id,
    ADD COLUMN editado         BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN num_ediciones   TINYINT UNSIGNED NOT NULL DEFAULT 0,
    ADD COLUMN fecha_edicion   DATETIME NULL,
    ADD CONSTRAINT fk_fp_topico FOREIGN KEY (topico_id) REFERENCES foro_topicos(id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_fp_respuesta_a FOREIGN KEY (respuesta_a_id) REFERENCES foro_publicaciones(id) ON DELETE SET NULL;

CREATE INDEX idx_fp_topico ON foro_publicaciones(topico_id);
CREATE INDEX idx_fp_respuesta_a ON foro_publicaciones(respuesta_a_id);

-- ---------------------------------------------------------------------
-- 3. FORO_DESTACADOS
-- ---------------------------------------------------------------------
CREATE TABLE foro_destacados (
                                 publicacion_id  BIGINT UNSIGNED NOT NULL,
                                 perfil_id       BIGINT UNSIGNED NOT NULL,
                                 fecha_creacion  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                 PRIMARY KEY (publicacion_id, perfil_id),
                                 CONSTRAINT fk_fdest_publicacion FOREIGN KEY (publicacion_id) REFERENCES foro_publicaciones(id) ON DELETE CASCADE,
                                 CONSTRAINT fk_fdest_perfil FOREIGN KEY (perfil_id) REFERENCES perfiles(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_fdest_perfil ON foro_destacados(perfil_id);

-- ---------------------------------------------------------------------
-- 4. FORO_ADJUNTOS
-- ---------------------------------------------------------------------
CREATE TABLE foro_adjuntos (
                               id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                               publicacion_id  BIGINT UNSIGNED NOT NULL,
                               tipo            ENUM('archivo','enlace') NOT NULL,
                               nombre          VARCHAR(255) NOT NULL,
                               mime_tipo       VARCHAR(120) NULL,
                               tamano_bytes    INT UNSIGNED NULL,
                               contenido       LONGBLOB NULL,
                               url             VARCHAR(500) NULL,
                               fecha_creacion  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                               CONSTRAINT fk_fadj_publicacion FOREIGN KEY (publicacion_id) REFERENCES foro_publicaciones(id) ON DELETE CASCADE,
                               CONSTRAINT ck_fadj_contenido CHECK (
                                   (tipo = 'archivo' AND (contenido IS NOT NULL OR url IS NOT NULL)) OR
                                   (tipo = 'enlace' AND url IS NOT NULL)
                                   )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_fadj_publicacion ON foro_adjuntos(publicacion_id);

-- ---------------------------------------------------------------------
-- 5. CONFIGURACION_GLOBAL
-- ---------------------------------------------------------------------
INSERT INTO configuracion_global (clave, valor, descripcion)
SELECT * FROM (SELECT 'foro_max_ediciones' AS clave, '3' AS valor,
                      'Veces que el autor puede editar un mismo mensaje del foro' AS descripcion) AS tmp
WHERE NOT EXISTS (SELECT 1 FROM configuracion_global WHERE clave = 'foro_max_ediciones');

-- ---------------------------------------------------------------------
-- 6. TIPOS_NOTIFICACION
-- ---------------------------------------------------------------------
INSERT INTO tipos_notificacion (codigo, nombre, descripcion)
SELECT * FROM (SELECT 'foro_respuesta' AS codigo, 'Respuesta en tu hilo' AS nombre,
                      'Alguien respondió en un hilo que abriste' AS descripcion) AS tmp
WHERE NOT EXISTS (SELECT 1 FROM tipos_notificacion WHERE codigo = 'foro_respuesta');

INSERT INTO tipos_notificacion (codigo, nombre, descripcion)
SELECT * FROM (SELECT 'foro_cita' AS codigo, 'Respondieron tu mensaje' AS nombre,
                      'Alguien respondió directamente a uno de tus mensajes del foro' AS descripcion) AS tmp
WHERE NOT EXISTS (SELECT 1 FROM tipos_notificacion WHERE codigo = 'foro_cita');

-- ---------------------------------------------------------------------
-- 7. Hilos que ya existían: se dejan con topico_id NULL a propósito.
--    Al arrancar, la aplicación (InicializadorTopicosForo) clasifica
--    automáticamente los hilos sin tópico usando las palabras clave de
--    arriba. Solo toca los NULL: nunca pisa un tópico ya elegido.
-- ---------------------------------------------------------------------

-- ---------------------------------------------------------------------
-- 8. Datos demo del foro v6 (citas, destacados, edición, enlaces)
--    Usa las variables @hilo_* / @pid_* definidas en la sección 4.
-- ---------------------------------------------------------------------
SET @r_ivan_k8s   = (SELECT id FROM foro_publicaciones WHERE publicacion_padre_id = @hilo_k8s AND autor_id = @pid_ivan LIMIT 1);
SET @r_jorge_k8s  = (SELECT id FROM foro_publicaciones WHERE publicacion_padre_id = @hilo_k8s AND autor_id = @pid_jorge LIMIT 1);
SET @r_mariana_qa = (SELECT id FROM foro_publicaciones WHERE publicacion_padre_id = @hilo_qa AND autor_id = @pid_mariana LIMIT 1);
SET @r_tomas_aur  = (SELECT id FROM foro_publicaciones WHERE publicacion_padre_id = @hilo_aurora AND autor_id = @pid_tomas LIMIT 1);

-- Jorge responde citando puntualmente el mensaje de Iván.
UPDATE foro_publicaciones SET respuesta_a_id = @r_ivan_k8s WHERE id = @r_jorge_k8s;

-- Tomás (PM de Aurora) editó su respuesta una vez (queda la huella "editado").
UPDATE foro_publicaciones SET editado = TRUE, num_ediciones = 1, fecha_edicion = NOW() - INTERVAL 1 DAY
WHERE id = @r_tomas_aur;

-- "Me fue útil"
INSERT INTO foro_destacados (publicacion_id, perfil_id) VALUES
                                                            (@r_jorge_k8s, @pid_fiorella), (@r_jorge_k8s, @pid_ivan),
                                                            (@r_mariana_qa, @pid_karla), (@r_mariana_qa, @pid_eduardo),
                                                            (@hilo_aurora, @pid_tomas);

-- Enlace adjunto a la solución del hilo de Kubernetes.
INSERT INTO foro_adjuntos (publicacion_id, tipo, nombre, url) VALUES
    (@r_jorge_k8s, 'enlace', 'Kubernetes: Namespaces (documentación oficial)',
     'https://kubernetes.io/docs/concepts/overview/working-with-objects/namespaces/');

-- Hilo sin responder sobre base de datos (útil para probar la búsqueda: "mysql", "bd", "rds").
INSERT INTO foro_publicaciones (proyecto_id, autor_id, titulo, contenido, etiquetas)
VALUES (@pid_portal_andes, @pid_ana, 'Conexión a RDS rechazada desde la instancia EC2',
        'Al levantar el .jar en la EC2, Spring Boot no logra conectarse a la base MySQL en RDS: "Communications link failure". Ya revisé usuario y contraseña. ¿Puede ser el security group del puerto 3306?',
        JSON_ARRAY('MySQL','Spring Boot'));

-- =====================================================================
-- Fin. Resumen para tu presentación:
--   16 usuarios (1 admin, 2 RM, 4 PM "contextuales", 9 colaboradores)
--   20 habilidades en 6 categorías
--   7 proyectos (2 planificación, 3 activos, 1 en pausa, 1 completado)
--   Tomás Herrera queda a propósito en 115% de carga (demo de alerta)
--   4 propuestas de AI Talent Matching (2 pendientes, 1 aprobada, 1 rechazada)
--   4 hilos de foro (2 con solución, 1 sin responder), con tópicos,
--   citas, destacados, un mensaje editado y un enlace adjunto
--   9 eventos de calendario (pasados, futuros y uno atrasado)
--   8 notificaciones ya generadas
--   2 invitaciones pendientes sin registrar
--   14 filas de auditoría
--
--   TODAS las cuentas nuevas usan la contraseña: admin123
-- =====================================================================

-- =====================================================================
-- ################ 6) v7–v10: archivos, perfil profesional, cursos, IA ################
-- Antes venían en scripts sueltos (migracion_v8_mejoras, migracion_v9_*,
-- migracion_v10_resumen_ia, spring_session_mysql) o los agregaba la app al
-- arrancar (InicializadorRazonCancelacion / V9 / V10 / TablasExtra). Los
-- inicializadores siguen existiendo y no hacen nada si esto ya está.
-- =====================================================================
USE skillbridge_db;
SET NAMES utf8mb4;

-- Proyectos: enlace al repositorio y motivo de cancelación
ALTER TABLE proyectos
    ADD COLUMN github_url        VARCHAR(500) DEFAULT NULL,
    ADD COLUMN razon_cancelacion TEXT NULL;

-- Archivos de contexto de un proyecto (ArchivoProyectoController)
CREATE TABLE archivos_proyecto (
    id           BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    proyecto_id  BIGINT UNSIGNED NOT NULL,
    nombre       VARCHAR(255) NOT NULL,
    tipo         VARCHAR(100),
    datos        MEDIUMBLOB NOT NULL,
    fecha_subida DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_archivo_proyecto FOREIGN KEY (proyecto_id)
        REFERENCES proyectos(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Perfil profesional del colaborador (resource-manager/colaborador-detalle.html)
ALTER TABLE perfiles
    ADD COLUMN linkedin_url   VARCHAR(500) DEFAULT NULL,
    ADD COLUMN github_url     VARCHAR(500) DEFAULT NULL,
    ADD COLUMN portafolio_url VARCHAR(500) DEFAULT NULL COMMENT 'Portafolio o sitio web personal',
    ADD COLUMN telefono       VARCHAR(30)  DEFAULT NULL COMMENT 'Teléfono de contacto',
    ADD COLUMN ubicacion      VARCHAR(120) DEFAULT NULL COMMENT 'Ciudad / país o modalidad',
    ADD COLUMN etiquetas      JSON         DEFAULT NULL COMMENT 'Etiquetas cortas sobre la persona (arreglo JSON)';

-- Datos demo del perfil profesional
UPDATE perfiles p JOIN usuarios u ON u.id = p.usuario_id SET
    p.biografia = COALESCE(p.biografia, 'Desarrollador frontend enfocado en interfaces accesibles y rápidas. Me gusta convertir diseños de Figma en componentes reutilizables y medir su rendimiento.'),
    p.ubicacion = 'Lima, Perú', p.telefono = '+51 900 000 101',
    p.linkedin_url = COALESCE(p.linkedin_url, 'https://www.linkedin.com/in/brian-castro-demo'),
    p.github_url   = COALESCE(p.github_url,   'https://github.com/brian-castro-demo'),
    p.portafolio_url = 'https://brian-castro-demo.dev',
    p.etiquetas = JSON_ARRAY('Accesibilidad', 'Design systems', 'Mentor de practicantes', 'Inglés B2')
WHERE u.correo = 'brian.castro@nexacorp.com' AND p.telefono IS NULL;

UPDATE perfiles p JOIN usuarios u ON u.id = p.usuario_id SET
    p.biografia = COALESCE(p.biografia, 'Ingeniera DevOps. Automatizo despliegues en AWS y cuido que los entornos sean reproducibles con Docker e infraestructura como código.'),
    p.ubicacion = 'Arequipa, Perú · remoto', p.telefono = '+51 900 000 102',
    p.linkedin_url = COALESCE(p.linkedin_url, 'https://www.linkedin.com/in/fiorella-torres-demo'),
    p.github_url   = COALESCE(p.github_url,   'https://github.com/fiorella-torres-demo'),
    p.etiquetas = JSON_ARRAY('AWS Cloud Practitioner', 'CI/CD', 'Guardias on-call', 'Inglés C1')
WHERE u.correo = 'fiorella.torres@nexacorp.com' AND p.telefono IS NULL;

UPDATE perfiles p JOIN usuarios u ON u.id = p.usuario_id SET
    p.biografia = COALESCE(p.biografia, 'Analista de datos. Construyo tableros y modelos para que las decisiones del negocio se basen en números claros.'),
    p.ubicacion = 'Lima, Perú', p.telefono = '+51 900 000 103',
    p.linkedin_url = COALESCE(p.linkedin_url, 'https://www.linkedin.com/in/eduardo-campos-demo'),
    p.etiquetas = JSON_ARRAY('Storytelling con datos', 'Power BI', 'Curioso')
WHERE u.correo = 'eduardo.campos@nexacorp.com' AND p.telefono IS NULL;

UPDATE perfiles p JOIN usuarios u ON u.id = p.usuario_id SET
    p.biografia = COALESCE(p.biografia, 'Backend developer con cinco años en Java y Spring. Diseño APIs REST limpias y me interesa la observabilidad de los servicios.'),
    p.ubicacion = 'Trujillo, Perú', p.telefono = '+51 900 000 104',
    p.linkedin_url = COALESCE(p.linkedin_url, 'https://www.linkedin.com/in/gabriel-chavez-demo'),
    p.github_url   = COALESCE(p.github_url,   'https://github.com/gabriel-chavez-demo'),
    p.etiquetas = JSON_ARRAY('APIs REST', 'Code review', 'Líder técnico', 'Inglés B2')
WHERE u.correo = 'gabriel.chavez@nexacorp.com' AND p.telefono IS NULL;

UPDATE perfiles p JOIN usuarios u ON u.id = p.usuario_id SET
    p.biografia = COALESCE(p.biografia, 'Diseñadora UX/UI. Investigo con usuarios reales antes de dibujar y documento cada decisión en el sistema de diseño.'),
    p.ubicacion = 'Lima, Perú · híbrido', p.telefono = '+51 900 000 105',
    p.linkedin_url = COALESCE(p.linkedin_url, 'https://www.linkedin.com/in/helena-ponce-demo'),
    p.portafolio_url = 'https://helena-ponce-demo.design',
    p.etiquetas = JSON_ARRAY('Investigación UX', 'Figma', 'Facilitadora de talleres')
WHERE u.correo = 'helena.ponce@nexacorp.com' AND p.telefono IS NULL;

UPDATE perfiles p JOIN usuarios u ON u.id = p.usuario_id SET
    p.biografia = COALESCE(p.biografia, 'Ingeniero backend y DevOps. Seis años llevando servicios Java a producción, con foco en rendimiento y costos de nube.'),
    p.ubicacion = 'Cusco, Perú · remoto', p.telefono = '+51 900 000 106',
    p.linkedin_url = COALESCE(p.linkedin_url, 'https://www.linkedin.com/in/ivan-rojas-demo'),
    p.github_url   = COALESCE(p.github_url,   'https://github.com/ivan-rojas-demo'),
    p.etiquetas = JSON_ARRAY('Kubernetes', 'Optimización de costos', 'Mentor')
WHERE u.correo = 'ivan.rojas@nexacorp.com' AND p.telefono IS NULL;

UPDATE perfiles p JOIN usuarios u ON u.id = p.usuario_id SET
    p.biografia = COALESCE(p.biografia, 'QA engineer. Escribo pruebas automatizadas de punta a punta y ayudo a los equipos a definir criterios de aceptación medibles.'),
    p.ubicacion = 'Lima, Perú', p.telefono = '+51 900 000 107',
    p.linkedin_url = COALESCE(p.linkedin_url, 'https://www.linkedin.com/in/karla-medina-demo'),
    p.etiquetas = JSON_ARRAY('Testing automatizado', 'Detallista', 'ISTQB')
WHERE u.correo = 'karla.medina@nexacorp.com' AND p.telefono IS NULL;

UPDATE perfiles p JOIN usuarios u ON u.id = p.usuario_id SET
    p.biografia = COALESCE(p.biografia, 'Data engineer. Diseño pipelines de datos confiables y me encargo de que lleguen limpios a los equipos de análisis.'),
    p.ubicacion = 'Piura, Perú · remoto', p.telefono = '+51 900 000 108',
    p.github_url   = COALESCE(p.github_url,   'https://github.com/renzo-fernandez-demo'),
    p.etiquetas = JSON_ARRAY('ETL', 'Spark', 'Inglés C1')
WHERE u.correo = 'renzo.fernandez@nexacorp.com' AND p.telefono IS NULL;

UPDATE perfiles p JOIN usuarios u ON u.id = p.usuario_id SET
    p.biografia = COALESCE(p.biografia, 'Backend developer senior. Siete años construyendo sistemas transaccionales; disfruto enseñar buenas prácticas de diseño.'),
    p.ubicacion = 'Lima, Perú', p.telefono = '+51 900 000 109',
    p.linkedin_url = COALESCE(p.linkedin_url, 'https://www.linkedin.com/in/tomas-herrera-demo'),
    p.github_url   = COALESCE(p.github_url,   'https://github.com/tomas-herrera-demo'),
    p.portafolio_url = 'https://tomas-herrera-demo.dev',
    p.etiquetas = JSON_ARRAY('Arquitectura', 'Microservicios', 'Mentor', 'Speaker interno')
WHERE u.correo = 'tomas.herrera@nexacorp.com' AND p.telefono IS NULL;

-- Catálogo de cursos del Administrador (AdminCursosController)
CREATE TABLE cursos (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  titulo VARCHAR(150) NOT NULL,
  descripcion TEXT NULL,
  url VARCHAR(500) NULL,
  categoria_id BIGINT UNSIGNED NULL,
  habilidad_id BIGINT UNSIGNED NULL,
  nivel VARCHAR(20) NOT NULL DEFAULT 'BASICO',
  estado VARCHAR(20) NOT NULL DEFAULT 'PUBLICADO',
  duracion_horas INT NOT NULL DEFAULT 1,
  fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  fecha_actualizacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  -- v11 catálogo de formación
  tipo VARCHAR(20) NOT NULL DEFAULT 'CURSO',          -- CURSO | VIDEO | RUTA | ESPECIALIZACION
  plataforma VARCHAR(40) NULL,                        -- clave de PlataformaCurso; NULL = se detecta por la URL
  gratuito TINYINT(1) NOT NULL DEFAULT 1,
  con_certificado TINYINT(1) NOT NULL DEFAULT 0,
  idioma VARCHAR(5) NOT NULL DEFAULT 'ES',
  destacado TINYINT(1) NOT NULL DEFAULT 0,
  CONSTRAINT fk_curso_categoria FOREIGN KEY (categoria_id) REFERENCES categorias_habilidad(id),
  CONSTRAINT fk_curso_habilidad FOREIGN KEY (habilidad_id) REFERENCES habilidades(id),
  INDEX idx_cursos_estado_titulo (estado, titulo),
  INDEX idx_cursos_categoria (categoria_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Pasos ordenados de una ruta (roadmap) o especialización
CREATE TABLE curso_pasos (
  curso_id BIGINT UNSIGNED NOT NULL,
  orden INT NOT NULL,
  titulo VARCHAR(150) NOT NULL,
  url VARCHAR(500) NULL,
  PRIMARY KEY (curso_id, orden),
  CONSTRAINT fk_paso_curso FOREIGN KEY (curso_id) REFERENCES cursos(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- El catálogo inicial de recursos gratuitos lo carga la app al primer arranque
-- (config/InicializadorTablasExtra.sembrarCatalogoDemo).

-- Resumen de hilos del foro con IA (ForoService.generarResumenIa)
ALTER TABLE foro_publicaciones
    ADD COLUMN resumen_ia             TEXT NULL     COMMENT 'Resumen del hilo generado por el Asistente IA (solo hilo raiz)',
    ADD COLUMN resumen_ia_generado_en DATETIME NULL COMMENT 'Cuando se genero/regenero el resumen';

-- Spring Session JDBC: sesiones HTTP en MySQL. La app también las crea al
-- arrancar (spring.session.jdbc.initialize-schema=always).
CREATE TABLE IF NOT EXISTS SPRING_SESSION (
    PRIMARY_ID            CHAR(36)     NOT NULL,
    SESSION_ID            CHAR(36)     NOT NULL,
    CREATION_TIME         BIGINT       NOT NULL,
    LAST_ACCESS_TIME      BIGINT       NOT NULL,
    MAX_INACTIVE_INTERVAL INT          NOT NULL,
    EXPIRY_TIME           BIGINT       NOT NULL,
    PRINCIPAL_NAME        VARCHAR(100),
    CONSTRAINT SPRING_SESSION_PK PRIMARY KEY (PRIMARY_ID),
    CONSTRAINT SPRING_SESSION_IX1 UNIQUE (SESSION_ID),
    INDEX SPRING_SESSION_IX2 (EXPIRY_TIME),
    INDEX SPRING_SESSION_IX3 (PRINCIPAL_NAME)
) ENGINE = InnoDB ROW_FORMAT = DYNAMIC;

CREATE TABLE IF NOT EXISTS SPRING_SESSION_ATTRIBUTES (
    SESSION_PRIMARY_ID CHAR(36)     NOT NULL,
    ATTRIBUTE_NAME     VARCHAR(200) NOT NULL,
    ATTRIBUTE_BYTES    BLOB         NOT NULL,
    CONSTRAINT SPRING_SESSION_ATTRIBUTES_PK PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME),
    CONSTRAINT SPRING_SESSION_ATTRIBUTES_FK FOREIGN KEY (SESSION_PRIMARY_ID)
        REFERENCES SPRING_SESSION (PRIMARY_ID) ON DELETE CASCADE
) ENGINE = InnoDB ROW_FORMAT = DYNAMIC;

-- =====================================================================
-- Fin del script.
-- =====================================================================

-- =====================================================================
-- Resumen profesional con IA del colaborador (colaborador/resumen-profesional.html).
-- Una fila por perfil con el resumen tipo CV que arma Claude a partir de las
-- constancias de sus habilidades (JSON editable). La app también la crea sola
-- al arrancar (config/InicializadorResumenIa).
-- =====================================================================
CREATE TABLE IF NOT EXISTS perfil_resumen_ia (
    perfil_id       BIGINT UNSIGNED NOT NULL PRIMARY KEY,
    contenido_json  MEDIUMTEXT NOT NULL,
    firma_fuentes   VARCHAR(64) NULL,
    fuentes_usadas  INT NOT NULL DEFAULT 0,
    generado_en     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    editado_en      DATETIME NULL,
    CONSTRAINT fk_resumen_ia_perfil FOREIGN KEY (perfil_id) REFERENCES perfiles(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Resumen previo de cada constancia (lo ve el Resource Manager al validar).
CREATE TABLE IF NOT EXISTS constancia_analisis_ia (
    perfil_id       BIGINT UNSIGNED NOT NULL,
    habilidad_id    BIGINT UNSIGNED NOT NULL,
    firma           VARCHAR(64) NOT NULL,
    contenido_json  TEXT NOT NULL,
    generado_en     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (perfil_id, habilidad_id),
    CONSTRAINT fk_analisis_ia_perfil FOREIGN KEY (perfil_id) REFERENCES perfiles(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
