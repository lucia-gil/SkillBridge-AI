-- =====================================================================
-- SkillBridge AI — Modelo de datos v2 (Entregable 2: Base de Datos + Spring
-- en Cloud)
-- Motor: MySQL 8.0+  |  Charset: utf8mb4  |  Motor de tabla: InnoDB
--
-- Esta versión reemplaza la v1 (basada solo en mockups) e incorpora el
-- documento "SkillBridge AI 1.pdf" — un diseño de base de datos ya discutido
-- y ajustado con el jefe de práctica, en particular la separación
-- usuarios/perfiles y el modelo de "rol" (ver sección de decisiones en
-- Entregable2-Informe.md, punto "Reconciliación con el documento del JP").
--
-- Convenciones:
--   * Se usa ENUM nativo de MySQL (no VARCHAR+CHECK como en la v1) porque
--     el documento fuente lo especifica así explícitamente y el proyecto
--     ya está comprometido con MySQL — no hace falta portabilidad a otro
--     motor.
--   * "Quién hizo esto" (autor_id, creado_por_id, validado_por_id,
--     solicitado_por_id, actualizado_por_id) apunta SIEMPRE a `perfiles`,
--     nunca a `usuarios`: usuarios solo tiene datos de login (correo,
--     contraseña), no tiene nombre ni nada mostrable en pantalla. La única
--     excepción es auditoria_logs.usuario_id, que registra acciones a nivel
--     de sesión/login (puede haber eventos sin perfil de negocio asociado
--     todavía, ej. intentos de login fallidos).
--   * Dos columnas "estado" con el mismo nombre pero valores completamente
--     distintos según la tabla (usuarios/perfiles vs. proyectos vs.
--     asignaciones) — no compartir lógica de "cambiar estado" genérica
--     entre ellas.
-- =====================================================================

-- Crea el schema desde cero (idempotente: se puede correr el archivo
-- completo las veces que haga falta sin arrastrar tablas de un intento
-- anterior a medio crear) y lo deja seleccionado como default — sin este
-- USE, MySQL Workbench tira "Error 1046: No database selected" en el
-- primer CREATE TABLE si la pestaña de la consola no tenía un schema
-- default ya elegido.
DROP SCHEMA IF EXISTS skillbride_db;
CREATE SCHEMA skillbride_db
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;
USE skillbride_db;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ---------------------------------------------------------------------
-- 1. USUARIOS — Autenticación (RF01)
--    Solo datos de login. rol_organizacional es NULL para la mayoría de
--    las personas: "esta persona no tiene un puesto fijo de gestión
--    global; lo que hace depende de en qué proyecto la mires" (ver
--    asignaciones.rol_en_proyecto más abajo). Administrador y Resource
--    Manager SÍ son puestos fijos, independientes de cualquier proyecto,
--    por eso viven aquí y no en asignaciones.
-- ---------------------------------------------------------------------
CREATE TABLE usuarios (
    id                      BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    correo                  VARCHAR(150) NOT NULL UNIQUE,
    contrasena_hash         VARCHAR(255) NOT NULL,
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
--    1:1 con usuarios. Todo lo que se muestra en pantalla (nombre, cargo,
--    disponibilidad...) vive aquí, no en usuarios.
-- ---------------------------------------------------------------------
CREATE TABLE perfiles (
    id                          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    usuario_id                  BIGINT UNSIGNED NOT NULL UNIQUE,
    nombre_completo             VARCHAR(150) NOT NULL,
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
-- 4. HABILIDADES — Catálogo cerrado (RF08)
--    categoria queda como texto libre (no FK a tabla propia): no hay CRUD
--    de categorías previsto, es solo una etiqueta de agrupación visual.
-- ---------------------------------------------------------------------
CREATE TABLE habilidades (
    id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    nombre      VARCHAR(80) NOT NULL UNIQUE,
    categoria   VARCHAR(60) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_habilidades_categoria ON habilidades(categoria);

-- ---------------------------------------------------------------------
-- 5. PERFIL_HABILIDAD — Relación colaborador ↔ habilidad (RF02/RF08)
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
-- 6. PROYECTOS (RF03)
--    tecnologias como JSON (lista de strings) — etiqueta libre, no impacta
--    el matching de habilidades, por eso no se normaliza a catálogo.
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
    creado_por_id           BIGINT UNSIGNED NOT NULL,
    fecha_creacion          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_proyecto_creador FOREIGN KEY (creado_por_id) REFERENCES perfiles(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_proyectos_estado ON proyectos(estado);

-- Nota de integridad (documentada en el informe, no resuelta por la BD):
-- al crear un proyecto, la aplicación DEBE insertar en la misma transacción
-- una fila en `asignaciones` para creado_por_id con rol_en_proyecto =
-- 'project_manager'. Si no, queda un proyecto con "creador" pero sin PM
-- formalmente asignado. Implementado en backend/.../ProyectoService.java.

-- ---------------------------------------------------------------------
-- 7. PROYECTO_HABILIDAD_REQUERIDA (RF03, insumo de RF05)
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
-- 8. ASIGNACIONES — Rol contextual por proyecto (RF03/RF04)
--    rol_en_proyecto SOLO puede ser 'project_manager' o 'colaborador':
--    liderar el proyecto o aportar como recurso. Administrador y Resource
--    Manager no "participan en" un proyecto de esa forma (existen por
--    encima de todos los proyectos a la vez) — si un Resource Manager
--    quiere aportar como recurso en un proyecto puntual, se le crea una
--    fila aquí con rol_en_proyecto='colaborador', sin tocar su
--    usuarios.rol_organizacional='resource_manager', que no cambia nunca.
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
-- 9. EXCEPCIONES_CARGA (RF04 — límite de carga por colaborador)
--    No está en el documento del JP, pero sí en RF04 ("evitar asignaciones
--    que superen los límites... ") y en los mockups (alerta "Tomás Herrera
--    (120%)... Solicitar excepción"). Se mantiene de la v1, adaptada para
--    apuntar a `perfiles` en vez de a `usuario`.
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
-- 10. FORO_PUBLICACIONES (RF06)
--     Una sola tabla autorreferencial: publicacion_padre_id NULL = hilo
--     raíz (tiene titulo); no NULL = respuesta a ese hilo (o a otra
--     respuesta). etiquetas es JSON libre (no hay catálogo de categorías
--     de foro — se reemplaza el diseño de la v1 que sí tenía
--     foro_categoria, siguiendo el documento fuente).
--     num_vistas es una extensión propia (no está en el documento del JP)
--     para no perder esa funcionalidad, ya visible en los mockups.
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
-- 11. RESUMENES_IA (RF06 — diferenciador)
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
-- 12-13. CHAT_SALAS / CHAT_MENSAJES (RF07)
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
-- 14. NOTIFICACIONES (RF09, propuesto en el backlog del equipo)
--     No está en el documento del JP, se mantiene de la v1 (adaptada a
--     perfiles) porque ya está en las 3 pantallas de "Centro de
--     notificaciones" de los mockups y en RF09 del backlog.
-- ---------------------------------------------------------------------
CREATE TABLE notificaciones (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    perfil_id       BIGINT UNSIGNED NOT NULL,
    tipo            ENUM('alerta','solicitud','info','resultado_ia') NOT NULL,
    titulo          VARCHAR(150) NOT NULL,
    detalle         TEXT NULL,
    leida           BOOLEAN NOT NULL DEFAULT FALSE,
    canal           ENUM('app','mail','app_mail') NOT NULL DEFAULT 'app',
    enlace_accion   VARCHAR(255) NULL,
    fecha_creacion  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notif_perfil FOREIGN KEY (perfil_id) REFERENCES perfiles(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_notif_perfil_leida ON notificaciones(perfil_id, leida);

CREATE TABLE preferencias_notificacion (
    id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    perfil_id   BIGINT UNSIGNED NOT NULL,
    tipo_evento ENUM('alertas_criticas','solicitudes_aprobacion','resultados_ia','resumen_semanal') NOT NULL,
    canal       ENUM('app','mail','app_mail','solo_app','solo_mail') NOT NULL,
    CONSTRAINT fk_pref_perfil FOREIGN KEY (perfil_id) REFERENCES perfiles(id) ON DELETE CASCADE,
    CONSTRAINT uq_pref_perfil_evento UNIQUE (perfil_id, tipo_evento)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------------------------------------------------------
-- 15. AUDITORIA_LOGS — Trazabilidad
--     usuario_id apunta a `usuarios` (no a perfiles): son eventos de
--     sesión/seguridad, y pueden ocurrir antes de que exista un perfil de
--     negocio completo (ej. un intento de login).
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
    detalle             TEXT NULL,
    fecha               DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_audit_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_audit_fecha ON auditoria_logs(fecha);

-- ---------------------------------------------------------------------
-- 16. CONFIGURACION_GLOBAL (RF08, panel de Administrador)
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
-- 17. CONOCIMIENTO_EMBEDDINGS — Diferenciador base de IA (búsqueda
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
-- 18. RECOMENDACIONES_IA_LOG — Trazabilidad del AI Talent Matching (RF05)
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

SET FOREIGN_KEY_CHECKS = 1;

-- =====================================================================
-- DATOS SEMILLA
-- =====================================================================

-- Administrador semilla (para poder autorizar el primer lote de correos).
-- La contraseña real se hashea desde la aplicación (BCrypt); este valor es
-- un placeholder que HAY que reemplazar antes de desplegar a producción.
INSERT INTO usuarios (correo, contrasena_hash, rol_organizacional, estado)
VALUES ('admin@nexacorp.com',
        '$2a$10$CAMBIA.ESTE.HASH.ANTES.DE.DESPLEGAR..................',
        'administrador', 'activo');

INSERT INTO perfiles (usuario_id, nombre_completo, cargo, disponibilidad_porcentaje, estado)
VALUES (1, 'Admin SkillBridge', 'Administradora de plataforma', 100, 'activo');

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

INSERT INTO habilidades (nombre, categoria) VALUES
 ('Java', 'Backend'), ('Spring Boot', 'Backend'), ('Kubernetes', 'DevOps & Cloud'),
 ('Docker', 'DevOps & Cloud'), ('React', 'Frontend'), ('Thymeleaf', 'Frontend'),
 ('PostgreSQL', 'Datos'), ('MySQL', 'Datos'), ('Apache Kafka', 'Backend'),
 ('Terraform', 'DevOps & Cloud'), ('Figma', 'Diseño'), ('Selenium', 'QA');