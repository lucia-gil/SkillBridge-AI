-- =====================================================================
-- SkillBridge AI 
-- Modelo de datos v4
-- Motor: MySQL 8.0+  |  Charset: utf8mb4  |  Motor de tabla: InnoDB
-- =====================================================================
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

-- DROP SCHEMA + CREATE SCHEMA: permite correr este archivo completo las
-- veces que haga falta sin arrastrar tablas de un intento anterior a medio
-- crear, y sin el "Error 1046: No database selected" de MySQL Workbench si
-- la pestaña de la consola no tenía un schema default ya elegido.
DROP SCHEMA IF EXISTS skillbridge_db;
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
CREATE TABLE correos_autorizados (
    id                  BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    correo              VARCHAR(150) NOT NULL UNIQUE,
    autorizado_por_id   BIGINT UNSIGNED NOT NULL,
    origen_carga        ENUM('individual','masiva_csv') NOT NULL DEFAULT 'individual',
    utilizado           BOOLEAN NOT NULL DEFAULT FALSE,
    fecha_autorizacion  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_uso           DATETIME NULL,
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
    fecha_creacion          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
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
--    liderar el proyecto o aportar como recurso. Administrador y Resource
--    Manager no "participan en" un proyecto de esa forma (existen por
--    encima de todos los proyectos a la vez) — si un Resource Manager
--    quiere aportar como recurso en un proyecto puntual, se le crea una
--    fila aquí con rol_en_proyecto='colaborador', sin tocar su
--    usuarios.rol_organizacional='resource_manager', que no cambia nunca.
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
CREATE TABLE chat_salas (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    proyecto_id     BIGINT UNSIGNED NOT NULL,
    nombre          VARCHAR(100) NOT NULL,
    fecha_creacion  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_sala_proyecto FOREIGN KEY (proyecto_id) REFERENCES proyectos(id) ON DELETE CASCADE
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
    descripcion VARCHAR(255) NULL
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

-- =====================================================================
-- DATOS DE PRUEBA ADICIONALES — equipo de ejemplo para probar el rol de
-- Colaborador (incluye Project Manager, que también es una cuenta
-- "colaborador" a nivel de usuarios: el rol de PM es contextual, por
-- proyecto, vía asignaciones.rol_en_proyecto, no un rol organizacional
-- fijo) y el rol de Administrador (el admin semilla ya viene arriba).
-- Contraseña real para TODAS las cuentas de prueba de abajo:
-- "Colaborador123!" (hash BCrypt real generado con la librería bcrypt,
-- verificable por Spring Security BCryptPasswordEncoder — cámbienla antes
-- de exponer esto públicamente, igual que la del admin).
-- =====================================================================

-- Correos autorizados del equipo de prueba (ya "usados": simulan cuentas
-- que ya completaron el registro, para no tener que pasar por el flujo de
-- alta manualmente al probar la app).
INSERT INTO correos_autorizados (correo, autorizado_por_id, origen_carga, utilizado, fecha_uso) VALUES
 ('carla.mendoza@nexacorp.com',  1, 'individual', TRUE, NOW()),
 ('luis.ramirez@nexacorp.com',   1, 'individual', TRUE, NOW()),
 ('sofia.vega@nexacorp.com',     1, 'masiva_csv', TRUE, NOW()),
 ('diego.torres@nexacorp.com',   1, 'masiva_csv', TRUE, NOW()),
 ('andrea.salazar@nexacorp.com', 1, 'individual', TRUE, NOW());

-- Usuarios de prueba: 4 cuentas "normales" (rol_organizacional NULL — serán
-- Project Manager o Colaborador según el proyecto, vía asignaciones) + 1
-- Resource Manager (rol organizacional fijo, para cubrir también ese caso
-- ya que el catálogo lo soporta).
INSERT INTO usuarios (correo, contrasena_hash, nombre_completo, rol_organizacional, estado) VALUES
 ('carla.mendoza@nexacorp.com',  '$2b$10$pJKAMeoRR.zBFw2q4ocDeOyrNa2OWPkebeiueR1D3tZWdvsxwVeCq', 'Carla Mendoza',  NULL,               'activo'),
 ('luis.ramirez@nexacorp.com',   '$2b$10$pJKAMeoRR.zBFw2q4ocDeOyrNa2OWPkebeiueR1D3tZWdvsxwVeCq', 'Luis Ramírez',   NULL,               'activo'),
 ('sofia.vega@nexacorp.com',     '$2b$10$pJKAMeoRR.zBFw2q4ocDeOyrNa2OWPkebeiueR1D3tZWdvsxwVeCq', 'Sofía Vega',     NULL,               'activo'),
 ('diego.torres@nexacorp.com',   '$2b$10$pJKAMeoRR.zBFw2q4ocDeOyrNa2OWPkebeiueR1D3tZWdvsxwVeCq', 'Diego Torres',   NULL,               'activo'),
 ('andrea.salazar@nexacorp.com', '$2b$10$pJKAMeoRR.zBFw2q4ocDeOyrNa2OWPkebeiueR1D3tZWdvsxwVeCq', 'Andrea Salazar', 'resource_manager', 'activo');

-- Perfiles (usuario_id resuelto por correo: no se hardcodean ids, siguiendo
-- la misma convención que el script ya usa para categoria_id/habilidad_id).
INSERT INTO perfiles (usuario_id, cargo, disponibilidad_porcentaje, experiencia_anios, biografia, estado) VALUES
 ((SELECT id FROM usuarios WHERE correo = 'carla.mendoza@nexacorp.com'),  'Project Manager',       100, 6, 'PM con experiencia liderando equipos de desarrollo backend y móvil.', 'activo'),
 ((SELECT id FROM usuarios WHERE correo = 'luis.ramirez@nexacorp.com'),   'Desarrollador Backend', 100, 3, 'Backend developer especializado en Java y Spring Boot.', 'activo'),
 ((SELECT id FROM usuarios WHERE correo = 'sofia.vega@nexacorp.com'),     'Ingeniera DevOps',      100, 4, 'DevOps con foco en Kubernetes, Docker y CI/CD.', 'activo'),
 ((SELECT id FROM usuarios WHERE correo = 'diego.torres@nexacorp.com'),   'Desarrollador Frontend', 80, 2, 'Frontend developer, React y Thymeleaf.', 'activo'),
 ((SELECT id FROM usuarios WHERE correo = 'andrea.salazar@nexacorp.com'), 'Resource Manager',      100, 8, 'Encargada de asignación de recursos entre proyectos.', 'activo');

-- Habilidades declaradas por cada perfil (nivel 1-5), algunas validadas
-- por el admin (validado_por_id) para probar también ese flujo.
INSERT INTO perfil_habilidad (perfil_id, habilidad_id, nivel, validado_por_id) VALUES
 ((SELECT p.id FROM perfiles p JOIN usuarios u ON u.id = p.usuario_id WHERE u.correo = 'carla.mendoza@nexacorp.com'),
  (SELECT id FROM habilidades WHERE nombre = 'Spring Boot'), 4, 1),
 ((SELECT p.id FROM perfiles p JOIN usuarios u ON u.id = p.usuario_id WHERE u.correo = 'carla.mendoza@nexacorp.com'),
  (SELECT id FROM habilidades WHERE nombre = 'Java'), 4, 1),
 ((SELECT p.id FROM perfiles p JOIN usuarios u ON u.id = p.usuario_id WHERE u.correo = 'luis.ramirez@nexacorp.com'),
  (SELECT id FROM habilidades WHERE nombre = 'Java'), 5, 1),
 ((SELECT p.id FROM perfiles p JOIN usuarios u ON u.id = p.usuario_id WHERE u.correo = 'luis.ramirez@nexacorp.com'),
  (SELECT id FROM habilidades WHERE nombre = 'Spring Boot'), 5, 1),
 ((SELECT p.id FROM perfiles p JOIN usuarios u ON u.id = p.usuario_id WHERE u.correo = 'luis.ramirez@nexacorp.com'),
  (SELECT id FROM habilidades WHERE nombre = 'MySQL'), 3, NULL),
 ((SELECT p.id FROM perfiles p JOIN usuarios u ON u.id = p.usuario_id WHERE u.correo = 'sofia.vega@nexacorp.com'),
  (SELECT id FROM habilidades WHERE nombre = 'Kubernetes'), 5, 1),
 ((SELECT p.id FROM perfiles p JOIN usuarios u ON u.id = p.usuario_id WHERE u.correo = 'sofia.vega@nexacorp.com'),
  (SELECT id FROM habilidades WHERE nombre = 'Docker'), 5, 1),
 ((SELECT p.id FROM perfiles p JOIN usuarios u ON u.id = p.usuario_id WHERE u.correo = 'sofia.vega@nexacorp.com'),
  (SELECT id FROM habilidades WHERE nombre = 'Terraform'), 3, NULL),
 ((SELECT p.id FROM perfiles p JOIN usuarios u ON u.id = p.usuario_id WHERE u.correo = 'diego.torres@nexacorp.com'),
  (SELECT id FROM habilidades WHERE nombre = 'React'), 4, NULL),
 ((SELECT p.id FROM perfiles p JOIN usuarios u ON u.id = p.usuario_id WHERE u.correo = 'diego.torres@nexacorp.com'),
  (SELECT id FROM habilidades WHERE nombre = 'Thymeleaf'), 2, NULL);

-- ---------------------------------------------------------------------
-- Proyectos de prueba: uno activo con equipo completo (PM + 2
-- colaboradores), uno en planificación (para probar vacantes/otro estado),
-- y uno completado (para probar historial de asignaciones finalizadas —
-- el mecanismo de clave_activa de la sección 9).
-- ---------------------------------------------------------------------
INSERT INTO proyectos (nombre, descripcion, tecnologias, estado, colaboradores_requeridos, fecha_inicio, fecha_fin_estimada) VALUES
 ('Plataforma de Matching IA', 'Motor de AI Talent Matching para asignar colaboradores a proyectos según habilidades.', JSON_ARRAY('Java','Spring Boot','MySQL'), 'activo', 3, '2026-08-01', '2026-12-15'),
 ('Portal de Autoservicio RRHH', 'Portal donde cada colaborador actualiza su propio perfil y disponibilidad.', JSON_ARRAY('React','Thymeleaf'), 'planificacion', 2, '2026-10-01', NULL),
 ('Migración Legacy a Cloud', 'Migración del sistema de facturación legacy a Cloud SQL + Cloud Run.', JSON_ARRAY('Docker','Kubernetes','Terraform'), 'completado', 2, '2026-03-01', '2026-07-31');

INSERT INTO proyecto_habilidad_requerida (proyecto_id, habilidad_id, nivel_requerido) VALUES
 ((SELECT id FROM proyectos WHERE nombre = 'Plataforma de Matching IA'), (SELECT id FROM habilidades WHERE nombre = 'Java'), 4),
 ((SELECT id FROM proyectos WHERE nombre = 'Plataforma de Matching IA'), (SELECT id FROM habilidades WHERE nombre = 'Spring Boot'), 4),
 ((SELECT id FROM proyectos WHERE nombre = 'Portal de Autoservicio RRHH'), (SELECT id FROM habilidades WHERE nombre = 'React'), 3),
 ((SELECT id FROM proyectos WHERE nombre = 'Migración Legacy a Cloud'), (SELECT id FROM habilidades WHERE nombre = 'Kubernetes'), 4);

-- ---------------------------------------------------------------------
-- Asignaciones: cubre rol_en_proyecto='project_manager' (Carla),
-- rol_en_proyecto='colaborador' activo (Luis, Sofía, Diego), Y una fila
-- FINALIZADA (Sofía en el proyecto ya completado) para probar en vivo que
-- el historial funciona: dos filas para la misma persona, pero en
-- proyectos distintos y con distinto estado, conviven sin chocar contra
-- el UNIQUE de clave_activa.
-- Luis queda con 60% + 50% = 110% de carga total activa (por encima del
-- limite_carga_colaborador=100 de configuracion_global) a propósito, para
-- poder probar el flujo de excepciones_carga de abajo con un caso real.
-- ---------------------------------------------------------------------
INSERT INTO asignaciones (proyecto_id, perfil_id, rol_en_proyecto, carga_porcentaje, estado, fecha_inicio, fecha_fin) VALUES
 ((SELECT id FROM proyectos WHERE nombre = 'Plataforma de Matching IA'),
  (SELECT p.id FROM perfiles p JOIN usuarios u ON u.id = p.usuario_id WHERE u.correo = 'carla.mendoza@nexacorp.com'),
  'project_manager', 100, 'activa', '2026-08-01', NULL),
 ((SELECT id FROM proyectos WHERE nombre = 'Plataforma de Matching IA'),
  (SELECT p.id FROM perfiles p JOIN usuarios u ON u.id = p.usuario_id WHERE u.correo = 'luis.ramirez@nexacorp.com'),
  'colaborador', 60, 'activa', '2026-08-01', NULL),
 ((SELECT id FROM proyectos WHERE nombre = 'Portal de Autoservicio RRHH'),
  (SELECT p.id FROM perfiles p JOIN usuarios u ON u.id = p.usuario_id WHERE u.correo = 'luis.ramirez@nexacorp.com'),
  'colaborador', 50, 'activa', '2026-10-01', NULL),
 ((SELECT id FROM proyectos WHERE nombre = 'Plataforma de Matching IA'),
  (SELECT p.id FROM perfiles p JOIN usuarios u ON u.id = p.usuario_id WHERE u.correo = 'sofia.vega@nexacorp.com'),
  'colaborador', 40, 'activa', '2026-08-15', NULL),
 ((SELECT id FROM proyectos WHERE nombre = 'Portal de Autoservicio RRHH'),
  (SELECT p.id FROM perfiles p JOIN usuarios u ON u.id = p.usuario_id WHERE u.correo = 'diego.torres@nexacorp.com'),
  'colaborador', 70, 'activa', '2026-10-01', NULL),
 ((SELECT id FROM proyectos WHERE nombre = 'Migración Legacy a Cloud'),
  (SELECT p.id FROM perfiles p JOIN usuarios u ON u.id = p.usuario_id WHERE u.correo = 'sofia.vega@nexacorp.com'),
  'colaborador', 100, 'finalizada', '2026-03-01', '2026-07-31');

-- Excepción de carga real: Luis queda en 110% (60+50) — por encima del
-- límite configurado (100%) — se solicita y se aprueba una excepción.
INSERT INTO excepciones_carga (asignacion_id, solicitado_por_id, aprobado_por_id, porcentaje_aprobado, fecha_limite, estado, motivo, fecha_resolucion) VALUES
 ((SELECT a.id FROM asignaciones a
     JOIN proyectos pr ON pr.id = a.proyecto_id
     JOIN perfiles p ON p.id = a.perfil_id JOIN usuarios u ON u.id = p.usuario_id
   WHERE u.correo = 'luis.ramirez@nexacorp.com' AND pr.nombre = 'Portal de Autoservicio RRHH'),
  (SELECT p.id FROM perfiles p JOIN usuarios u ON u.id = p.usuario_id WHERE u.correo = 'luis.ramirez@nexacorp.com'),
  (SELECT p.id FROM perfiles p JOIN usuarios u ON u.id = p.usuario_id WHERE u.correo = 'carla.mendoza@nexacorp.com'),
  110, '2026-12-31', 'aprobada', 'Cobertura temporal mientras se contrata un backend adicional para el Portal de Autoservicio RRHH.', NOW());

-- ---------------------------------------------------------------------
-- Foro, resumen de IA, chat — actividad de ejemplo en el proyecto activo.
-- ---------------------------------------------------------------------
INSERT INTO foro_publicaciones (proyecto_id, autor_id, publicacion_padre_id, titulo, contenido, etiquetas, es_solucion, num_vistas) VALUES
 ((SELECT id FROM proyectos WHERE nombre = 'Plataforma de Matching IA'),
  (SELECT p.id FROM perfiles p JOIN usuarios u ON u.id = p.usuario_id WHERE u.correo = 'carla.mendoza@nexacorp.com'),
  NULL, '¿Cómo calculamos el score de compatibilidad?', 'Estoy definiendo los pesos del matching, ¿alguien tiene una propuesta de fórmula?', JSON_ARRAY('matching','ia'), FALSE, 12);

INSERT INTO foro_publicaciones (proyecto_id, autor_id, publicacion_padre_id, titulo, contenido, etiquetas, es_solucion, num_vistas) VALUES
 ((SELECT id FROM proyectos WHERE nombre = 'Plataforma de Matching IA'),
  (SELECT p.id FROM perfiles p JOIN usuarios u ON u.id = p.usuario_id WHERE u.correo = 'luis.ramirez@nexacorp.com'),
  (SELECT id FROM foro_publicaciones WHERE titulo = '¿Cómo calculamos el score de compatibilidad?'),
  NULL, 'Sugiero 50% habilidades + 30% experiencia + 20% disponibilidad, ya está en configuracion_global así que solo hay que leerlo de ahí en vez de hardcodearlo.', NULL, TRUE, 5);

INSERT INTO resumenes_ia (publicacion_id, resumen, modelo_utilizado) VALUES
 ((SELECT id FROM foro_publicaciones WHERE titulo = '¿Cómo calculamos el score de compatibilidad?'),
  'El hilo define la fórmula del score de matching: 50% habilidades, 30% experiencia, 20% disponibilidad, tomados de configuracion_global. Respuesta de Luis marcada como solución.', 'claude-sonnet-5');

INSERT INTO chat_salas (proyecto_id, nombre) VALUES
 ((SELECT id FROM proyectos WHERE nombre = 'Plataforma de Matching IA'), 'General - Matching IA');

INSERT INTO chat_mensajes (sala_id, autor_id, contenido) VALUES
 ((SELECT id FROM chat_salas WHERE nombre = 'General - Matching IA'),
  (SELECT p.id FROM perfiles p JOIN usuarios u ON u.id = p.usuario_id WHERE u.correo = 'carla.mendoza@nexacorp.com'),
  'Buenas equipo, quedamos en revisar el score el jueves.'),
 ((SELECT id FROM chat_salas WHERE nombre = 'General - Matching IA'),
  (SELECT p.id FROM perfiles p JOIN usuarios u ON u.id = p.usuario_id WHERE u.correo = 'luis.ramirez@nexacorp.com'),
  'De acuerdo, dejo la propuesta en el foro.');

-- ---------------------------------------------------------------------
-- Notificaciones y preferencias — para Luis (con la excepción de carga) y
-- Carla (como PM).
-- ---------------------------------------------------------------------
INSERT INTO notificaciones (perfil_id, tipo_id, titulo, detalle, leida, canal, enlace_accion) VALUES
 ((SELECT p.id FROM perfiles p JOIN usuarios u ON u.id = p.usuario_id WHERE u.correo = 'luis.ramirez@nexacorp.com'),
  (SELECT id FROM tipos_notificacion WHERE codigo = 'alerta'),
  'Carga por encima del límite', 'Tu carga total activa es 110%, por encima del límite configurado (100%).', FALSE, 'app_mail', '/asignaciones/mias'),
 ((SELECT p.id FROM perfiles p JOIN usuarios u ON u.id = p.usuario_id WHERE u.correo = 'luis.ramirez@nexacorp.com'),
  (SELECT id FROM tipos_notificacion WHERE codigo = 'solicitud'),
  'Excepción de carga aprobada', 'Tu excepción de carga para el Portal de Autoservicio RRHH fue aprobada.', TRUE, 'app', '/excepciones/mias'),
 ((SELECT p.id FROM perfiles p JOIN usuarios u ON u.id = p.usuario_id WHERE u.correo = 'carla.mendoza@nexacorp.com'),
  (SELECT id FROM tipos_notificacion WHERE codigo = 'info'),
  'Nuevo colaborador asignado', 'Sofía Vega fue asignada a Plataforma de Matching IA (40%).', TRUE, 'app', '/proyectos/plataforma-matching-ia');

INSERT INTO preferencias_notificacion (perfil_id, tipo_evento, canal) VALUES
 ((SELECT p.id FROM perfiles p JOIN usuarios u ON u.id = p.usuario_id WHERE u.correo = 'luis.ramirez@nexacorp.com'), 'alertas_criticas', 'app_mail'),
 ((SELECT p.id FROM perfiles p JOIN usuarios u ON u.id = p.usuario_id WHERE u.correo = 'luis.ramirez@nexacorp.com'), 'solicitudes_aprobacion', 'solo_app'),
 ((SELECT p.id FROM perfiles p JOIN usuarios u ON u.id = p.usuario_id WHERE u.correo = 'carla.mendoza@nexacorp.com'), 'resultados_ia', 'app_mail');

-- ---------------------------------------------------------------------
-- Eventos de proyecto + comentarios (tablas nuevas del v4).
-- ---------------------------------------------------------------------
INSERT INTO eventos_proyecto (proyecto_id, creado_por_id, tipo_id, titulo, descripcion, fecha_inicio, fecha_fin, enlace_virtual, audiencia_id, estado) VALUES
 ((SELECT id FROM proyectos WHERE nombre = 'Plataforma de Matching IA'),
  (SELECT p.id FROM perfiles p JOIN usuarios u ON u.id = p.usuario_id WHERE u.correo = 'carla.mendoza@nexacorp.com'),
  (SELECT id FROM tipos_evento WHERE codigo = 'reunion'),
  'Revisión de score de matching', 'Reunión para cerrar la fórmula del score.', '2026-09-17 15:00:00', '2026-09-17 16:00:00', 'https://meet.example.com/matching-ia',
  (SELECT id FROM tipos_audiencia WHERE codigo = 'todos'), 'pendiente'),
 ((SELECT id FROM proyectos WHERE nombre = 'Plataforma de Matching IA'),
  (SELECT p.id FROM perfiles p JOIN usuarios u ON u.id = p.usuario_id WHERE u.correo = 'carla.mendoza@nexacorp.com'),
  (SELECT id FROM tipos_evento WHERE codigo = 'entregable'),
  'Entrega del motor de matching v1', 'Primera versión funcional del motor de matching.', '2026-10-15 23:59:00', NULL, NULL,
  (SELECT id FROM tipos_audiencia WHERE codigo = 'todos'), 'pendiente'),
 ((SELECT id FROM proyectos WHERE nombre = 'Plataforma de Matching IA'),
  (SELECT p.id FROM perfiles p JOIN usuarios u ON u.id = p.usuario_id WHERE u.correo = 'carla.mendoza@nexacorp.com'),
  (SELECT id FROM tipos_evento WHERE codigo = 'reunion'),
  'Sync solo PMs - riesgos del proyecto', 'Reunión de seguimiento restringida a Project Managers.', '2026-09-10 10:00:00', '2026-09-10 10:30:00', 'https://meet.example.com/pm-sync',
  (SELECT id FROM tipos_audiencia WHERE codigo = 'solo_pm'), 'cumplido');

INSERT INTO comentarios_evento (evento_id, autor_id, contenido) VALUES
 ((SELECT id FROM eventos_proyecto WHERE titulo = 'Revisión de score de matching'),
  (SELECT p.id FROM perfiles p JOIN usuarios u ON u.id = p.usuario_id WHERE u.correo = 'luis.ramirez@nexacorp.com'),
  'Confirmo asistencia, llevo la propuesta de pesos del foro.'),
 ((SELECT id FROM eventos_proyecto WHERE titulo = 'Revisión de score de matching'),
  (SELECT p.id FROM perfiles p JOIN usuarios u ON u.id = p.usuario_id WHERE u.correo = 'sofia.vega@nexacorp.com'),
  'Yo me conecto 10 minutos tarde, tengo otra reunión antes.');

-- ---------------------------------------------------------------------
-- IA: embeddings + log de recomendaciones (RF05), para completar el
-- circuito de datos que ambos roles (Administrador y Colaborador/PM)
-- pueden llegar a consultar.
-- ---------------------------------------------------------------------
INSERT INTO conocimiento_embeddings (tipo_origen, referencia_id, contenido_indexado, vector_embedding) VALUES
 ('publicacion_foro',
  (SELECT id FROM foro_publicaciones WHERE titulo = '¿Cómo calculamos el score de compatibilidad?'),
  '¿Cómo calculamos el score de compatibilidad? Estoy definiendo los pesos del matching...',
  JSON_ARRAY(0.012, -0.034, 0.087, 0.005, -0.099));

INSERT INTO recomendaciones_ia_log (proyecto_id, perfil_recomendado_id, solicitado_por_id, puntaje_compatibilidad, explicacion, fue_asignado) VALUES
 ((SELECT id FROM proyectos WHERE nombre = 'Portal de Autoservicio RRHH'),
  (SELECT p.id FROM perfiles p JOIN usuarios u ON u.id = p.usuario_id WHERE u.correo = 'diego.torres@nexacorp.com'),
  (SELECT p.id FROM perfiles p JOIN usuarios u ON u.id = p.usuario_id WHERE u.correo = 'carla.mendoza@nexacorp.com'),
  87.50, 'Coincide en React (nivel 4/5, requerido 3/5) y tiene disponibilidad suficiente (80%).', TRUE);

-- ---------------------------------------------------------------------
-- Auditoría — un par de eventos de ejemplo, cubriendo acción del admin y
-- acción de un PM, con valor_anterior/valor_nuevo poblados (ampliación v4).
-- ---------------------------------------------------------------------
INSERT INTO auditoria_logs (usuario_id, accion, entidad_afectada, entidad_id, valor_anterior, valor_nuevo, detalle) VALUES
 (1, 'AUTORIZAR_CORREO', 'correos_autorizados',
  (SELECT id FROM correos_autorizados WHERE correo = 'carla.mendoza@nexacorp.com'),
  NULL, JSON_OBJECT('correo','carla.mendoza@nexacorp.com','utilizado', false),
  'Administrador autoriza el correo de Carla Mendoza para registro.'),
 ((SELECT id FROM usuarios WHERE correo = 'carla.mendoza@nexacorp.com'), 'APROBAR_EXCEPCION_CARGA', 'excepciones_carga',
  (SELECT id FROM excepciones_carga LIMIT 1),
  JSON_OBJECT('estado','pendiente'), JSON_OBJECT('estado','aprobada','porcentaje_aprobado',110),
  'Carla Mendoza (PM) aprueba la excepción de carga de Luis Ramírez.');
