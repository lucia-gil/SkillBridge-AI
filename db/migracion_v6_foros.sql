-- =====================================================================
-- SkillBridge AI — Migración v6: Foros como centro de ayuda
-- Motor: MySQL 8.0+  |  Ejecutar UNA sola vez sobre una BD que ya tiene
-- skillbridge_db_COMPLETA_con_demo.sql (o v4 + entregables + 2FA).
--   mysql -u root -p < db/migracion_v6_foros.sql
-- =====================================================================
--
-- QUÉ AGREGA Y POR QUÉ
--
--  1. FORO_TOPICOS (tabla nueva, catálogo)
--     Categoriza cada hilo por tema (Backend, Base de datos, DevOps...).
--     Mismo criterio que pidió el JP para tipos_evento / tipos_notificacion /
--     categorias_habilidad: un catálogo en tabla aparte y no un ENUM.
--     palabras_clave alimenta el clasificador automático
--     (ForoClasificadorService): al crear un hilo sin tópico elegido, el
--     backend lo asigna solo según las palabras del título y del mensaje.
--     La misma lista viaja a la vista para la búsqueda inteligente.
--
--  2. FORO_PUBLICACIONES — columnas nuevas
--     topico_id       FK a foro_topicos (solo en hilos raíz).
--     respuesta_a_id  "Responder a un mensaje puntual" (estilo Slack/Zoom):
--                     el hilo sigue siendo plano (todas las respuestas
--                     cuelgan de publicacion_padre_id), y esta columna solo
--                     indica a QUÉ mensaje del hilo cita la respuesta. Se
--                     separa de publicacion_padre_id a propósito: así no se
--                     rompe ninguna consulta existente que asume
--                     "padre = hilo raíz".
--     editado / num_ediciones / fecha_edicion
--                     Huella visible de edición ("editado" en la UI) y
--                     límite de ediciones (configuracion_global
--                     'foro_max_ediciones'). El texto ANTERIOR de cada
--                     edición queda en auditoria_logs (valor_anterior /
--                     valor_nuevo), que ya existía para esto: no se
--                     duplica en una tabla de historial propia.
--
--  3. FORO_DESTACADOS (tabla nueva)
--     "Me fue útil" / estrella: una fila por (mensaje, persona). PK
--     compuesta para que nadie destaque dos veces el mismo mensaje.
--
--  4. FORO_ADJUNTOS (tabla nueva)
--     Imágenes, PDF, Word, Excel, etc. (tipo='archivo', binario en
--     `contenido`, mismo enfoque que entregas.archivo_contenido) o enlaces
--     (tipo='enlace', solo `url`). Se guarda en BD y no en disco para que
--     funcione igual en la instancia EC2 sin configurar almacenamiento
--     (si luego se usa S3, basta con llenar `url` y dejar `contenido` NULL).
--
--  5. CONFIGURACION_GLOBAL: 'foro_max_ediciones' (por defecto 3).
--  6. TIPOS_NOTIFICACION: 'foro_respuesta' y 'foro_cita'. ForoService ya
--     notificaba con 'foro_respuesta', pero ese código no existía en el
--     catálogo, así que NotificacionService descartaba la notificación sin
--     avisar. Ahora sí llega.
-- =====================================================================
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
