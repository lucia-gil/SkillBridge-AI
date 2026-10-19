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
 -- Tomás Herrera: segunda asignación activa (ver App Móvil Aurora más abajo)
 -- que lo deja en 115% de carga total, a propósito, para la demo de
 -- ocupación/sobrecarga en resource-manager/ocupacion.html.
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

-- Tomás Herrera también está en App Móvil Aurora (55%) — junto con el
-- 60% de Migración a Kubernetes de arriba, su carga total queda en
-- 115%: por diseño, para poder mostrar la alerta de sobrecarga.
INSERT INTO asignaciones (proyecto_id, perfil_id, rol_en_proyecto, carga_porcentaje, estado, fecha_inicio)
VALUES (@pid_app_aurora, @pid_tomas, 'colaborador', 55, 'activa', CURDATE() - INTERVAL 15 DAY);

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
VALUES (@pid_app_aurora, @pid_luis, @hilo_aurora,
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

-- =====================================================================
-- Fin. Resumen para tu presentación:
--   16 usuarios (1 admin, 2 RM, 4 PM "contextuales", 9 colaboradores)
--   20 habilidades en 6 categorías
--   7 proyectos (2 planificación, 3 activos, 1 en pausa, 1 completado)
--   Tomás Herrera queda a propósito en 115% de carga (demo de alerta)
--   4 propuestas de AI Talent Matching (2 pendientes, 1 aprobada, 1 rechazada)
--   3 hilos de foro con respuestas (2 con solución marcada)
--   9 eventos de calendario (pasados, futuros y uno atrasado)
--   8 notificaciones ya generadas
--   2 invitaciones pendientes sin registrar
--   14 filas de auditoría
--
--   TODAS las cuentas nuevas usan la contraseña: admin123
-- =====================================================================
