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


INSERT INTO usuarios (correo, contrasena_hash, nombre_completo, rol_organizacional, estado)
VALUES ('diego.salazar@nexacorp.com',
        '$2b$10$5VvawdnDpSRdkKvzrBKun.sAfXs.HHrisVGgIuPFDIYGA3vxVG0Oe',
        'Diego Salazar', NULL, 'activo');

INSERT INTO perfiles (usuario_id, cargo, disponibilidad_porcentaje, estado)
VALUES (LAST_INSERT_ID(), 'Frontend Developer', 100, 'activo');

INSERT INTO correos_autorizados (correo, autorizado_por_id, origen_carga, utilizado, fecha_uso)
VALUES ('diego.salazar@nexacorp.com', 1, 'individual', TRUE, NOW());

