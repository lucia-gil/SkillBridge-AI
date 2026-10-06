-- =====================================================================
-- SkillBridge AI — Migración v7: modelo de permisos y consistencia de roles
-- Motor: MySQL 8.0+ (probado también en MariaDB 10.11)
--
-- Para una BD YA creada con skillbridge_db_COMPLETA_con_demo.sql (o v4).
-- Una instalación nueva no la necesita: el script COMPLETA ya trae todo.
--
-- Modelo v7 en una línea: Administrador y Resource Manager son puestos
-- fijos y NUNCA participan en proyectos; los Miembros (rol_organizacional
-- NULL) pueden liderar unos proyectos y colaborar en otros a la vez; la
-- carga efectiva de una persona nunca supera el 100%.
--
-- Cómo usarla:
--   1) Corre SOLO la PARTE 1 y revisa qué inconsistencias aparecen.
--   2) Corre el archivo completo. Es idempotente: si se ejecuta dos veces,
--      la segunda no cambia nada ni duplica la auditoría.
--   3) La PARTE 4 repite el diagnóstico: todas las consultas deberían
--      volver vacías (salvo lo que haya que decidir a mano, ver notas).
-- =====================================================================

USE skillbridge_db;
SET NAMES utf8mb4;

-- =====================================================================
-- PARTE 1 — DIAGNÓSTICO (solo SELECT, no modifica nada)
-- =====================================================================

-- 1.1 Administradores o Resource Managers con asignaciones activas
--     (regla v7: nunca deberían tener filas activas en asignaciones).
SELECT '1.1 Admin/RM con asignaciones activas' AS chequeo,
       u.nombre_completo, u.correo, u.rol_organizacional,
       pr.nombre AS proyecto, a.rol_en_proyecto, a.carga_porcentaje
FROM asignaciones a
JOIN perfiles p   ON p.id = a.perfil_id
JOIN usuarios u   ON u.id = p.usuario_id
JOIN proyectos pr ON pr.id = a.proyecto_id
WHERE a.estado = 'activa' AND u.rol_organizacional IS NOT NULL
ORDER BY u.nombre_completo;

-- 1.2 Personas con carga EFECTIVA por encima del 100% (solo proyectos
--     vigentes: planificacion / activo / en_pausa).
SELECT '1.2 Carga efectiva > 100%' AS chequeo,
       u.nombre_completo, u.correo, SUM(a.carga_porcentaje) AS carga_efectiva
FROM asignaciones a
JOIN perfiles p   ON p.id = a.perfil_id
JOIN usuarios u   ON u.id = p.usuario_id
JOIN proyectos pr ON pr.id = a.proyecto_id
WHERE a.estado = 'activa' AND pr.estado IN ('planificacion','activo','en_pausa')
GROUP BY u.id, u.nombre_completo, u.correo
HAVING SUM(a.carga_porcentaje) > 100;

-- 1.3 Proyectos vigentes sin Project Manager operativo (sin fila activa de
--     PM, o cuyo PM es Admin/RM y por lo tanto nunca ve la vista PM).
SELECT '1.3 Proyecto vigente sin PM operativo' AS chequeo, pr.id, pr.nombre, pr.estado
FROM proyectos pr
WHERE pr.estado IN ('planificacion','activo','en_pausa')
  AND NOT EXISTS (
      SELECT 1 FROM asignaciones a
      JOIN perfiles p ON p.id = a.perfil_id
      JOIN usuarios u ON u.id = p.usuario_id
      WHERE a.proyecto_id = pr.id AND a.rol_en_proyecto = 'project_manager'
        AND a.estado = 'activa' AND u.rol_organizacional IS NULL AND u.estado = 'activo');

-- 1.4 Proyectos con más de un PM activo (solo debe haber uno).
SELECT '1.4 Más de un PM activo' AS chequeo, pr.id, pr.nombre, COUNT(*) AS pms_activos
FROM asignaciones a JOIN proyectos pr ON pr.id = a.proyecto_id
WHERE a.estado = 'activa' AND a.rol_en_proyecto = 'project_manager'
GROUP BY pr.id, pr.nombre
HAVING COUNT(*) > 1;

-- 1.5 Cuentas suspendidas que siguen con asignaciones activas.
SELECT '1.5 Cuenta suspendida con asignaciones activas' AS chequeo,
       u.nombre_completo, u.correo, pr.nombre AS proyecto, a.rol_en_proyecto
FROM asignaciones a
JOIN perfiles p   ON p.id = a.perfil_id
JOIN usuarios u   ON u.id = p.usuario_id
JOIN proyectos pr ON pr.id = a.proyecto_id
WHERE a.estado = 'activa' AND u.estado = 'inactivo';

-- =====================================================================
-- PARTE 2 — ESQUEMA (columnas nuevas, solo si todavía no existen)
-- La app también las crea sola al arrancar (InicializadorEsquemaV7).
-- MySQL 8 no soporta "ADD COLUMN IF NOT EXISTS": se usa información de
-- information_schema + PREPARE para que sea idempotente.
-- =====================================================================

SET @sql := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
                AND TABLE_NAME = 'proyectos' AND COLUMN_NAME = 'fecha_pausa') = 0,
               'ALTER TABLE proyectos ADD COLUMN fecha_pausa DATE NULL', 'DO 0');
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
                AND TABLE_NAME = 'proyectos' AND COLUMN_NAME = 'motivo_pausa') = 0,
               'ALTER TABLE proyectos ADD COLUMN motivo_pausa VARCHAR(500) NULL', 'DO 0');
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
                AND TABLE_NAME = 'proyectos' AND COLUMN_NAME = 'fecha_reanudacion_estimada') = 0,
               'ALTER TABLE proyectos ADD COLUMN fecha_reanudacion_estimada DATE NULL', 'DO 0');
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
                AND TABLE_NAME = 'correos_autorizados' AND COLUMN_NAME = 'rol_asignado') = 0,
               'ALTER TABLE correos_autorizados ADD COLUMN rol_asignado ENUM(''administrador'',''resource_manager'') NULL', 'DO 0');
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
                AND TABLE_NAME = 'propuestas_asignacion' AND COLUMN_NAME = 'origen') = 0,
               'ALTER TABLE propuestas_asignacion ADD COLUMN origen VARCHAR(20) NOT NULL DEFAULT ''talent_matching''', 'DO 0');
PREPARE st FROM @sql; EXECUTE st; DEALLOCATE PREPARE st;

-- =====================================================================
-- PARTE 3 — CORRECCIONES DE DATOS (casos detectados en la revisión)
-- Cada corrección deja su registro en auditoria_logs, a nombre del
-- administrador semilla, con el detalle "Corrección de consistencia v7".
-- =====================================================================

START TRANSACTION;

SET @admin_usuario_id = (SELECT id FROM usuarios WHERE correo = 'admin@nexacorp.com');
SET @pid_luis   = (SELECT p.id FROM perfiles p JOIN usuarios u ON u.id = p.usuario_id WHERE u.correo = 'luis.pena@nexacorp.com');
SET @pid_tomas  = (SELECT p.id FROM perfiles p JOIN usuarios u ON u.id = p.usuario_id WHERE u.correo = 'tomas.herrera@nexacorp.com');
SET @uid_brian  = (SELECT id FROM usuarios WHERE correo = 'brian.castro@nexacorp.com');
SET @aurora     = (SELECT id FROM proyectos WHERE nombre = 'App Móvil Aurora');

-- ---------------------------------------------------------------------
-- A1. Luis Peña es Resource Manager (puesto fijo) y además figuraba como
--     PM activo de App Móvil Aurora: nunca veía la vista PM y Aurora
--     quedaba sin PM operativo. Se finaliza su fila (no se borra: queda en
--     el historial) y Tomás Herrera, que ya estaba en el equipo, pasa a
--     liderar el proyecto (transferencia: misma fila, UPDATE de rol).
-- ---------------------------------------------------------------------
UPDATE asignaciones
SET estado = 'finalizada', fecha_fin = CURDATE()
WHERE proyecto_id = @aurora AND perfil_id = @pid_luis AND estado = 'activa';
SET @a1_luis = ROW_COUNT();

INSERT INTO auditoria_logs (usuario_id, accion, entidad_afectada, entidad_id, valor_anterior, valor_nuevo, detalle)
SELECT @admin_usuario_id, 'ASIGNACION_FINALIZADA', 'proyecto', @aurora,
       JSON_OBJECT('rol_en_proyecto', 'project_manager'), NULL,
       'Corrección de consistencia v7: Luis Peña es Resource Manager y no participa en proyectos; deja de liderar "App Móvil Aurora".'
FROM DUAL WHERE @a1_luis > 0;

-- ---------------------------------------------------------------------
-- A3 (junto con A1). Tomás Herrera pasa a PM de Aurora con 35%: su carga
--     total queda en 60% (Kubernetes) + 35% = 95% (antes 115%, por encima
--     del tope). Ejemplo real de "Miembro con dos papeles": PM en Aurora,
--     colaborador en Kubernetes.
-- ---------------------------------------------------------------------
UPDATE asignaciones
SET rol_en_proyecto = 'project_manager', carga_porcentaje = 35
WHERE proyecto_id = @aurora AND perfil_id = @pid_tomas AND estado = 'activa'
  AND rol_en_proyecto = 'colaborador'
  AND NOT EXISTS (SELECT 1 FROM (SELECT proyecto_id FROM asignaciones
                                 WHERE proyecto_id = @aurora AND rol_en_proyecto = 'project_manager'
                                   AND estado = 'activa') pm_vivo);
SET @a1_tomas = ROW_COUNT();

INSERT INTO auditoria_logs (usuario_id, accion, entidad_afectada, entidad_id, valor_anterior, valor_nuevo, detalle)
SELECT @admin_usuario_id, 'PM_REASIGNADO', 'proyecto', @aurora,
       JSON_OBJECT('pm', 'Luis Peña'), JSON_OBJECT('pm', 'Tomás Herrera'),
       'Corrección de consistencia v7: Tomás Herrera (ya en el equipo) pasa a liderar "App Móvil Aurora" con 35%; su carga total baja de 115% a 95%.'
FROM DUAL WHERE @a1_tomas > 0;

-- ---------------------------------------------------------------------
-- A2. Brian Castro: en el script es Miembro (Frontend Developer,
--     colaborador de Dashboard de Analítica) pero se le cambió el rol a
--     Resource Manager a mano, sin revisar sus asignaciones. Vuelve a ser
--     Miembro y conserva su asignación (siguen existiendo 2 RM).
-- ---------------------------------------------------------------------
SET @brian_rol_previo = (SELECT rol_organizacional FROM usuarios WHERE id = @uid_brian);
UPDATE usuarios SET rol_organizacional = NULL
WHERE id = @uid_brian AND rol_organizacional IS NOT NULL
  AND EXISTS (SELECT 1 FROM (SELECT a.id FROM asignaciones a JOIN perfiles p ON p.id = a.perfil_id
                             WHERE p.usuario_id = @uid_brian AND a.estado = 'activa') con_asignacion);
SET @a2_brian = ROW_COUNT();

INSERT INTO auditoria_logs (usuario_id, accion, entidad_afectada, entidad_id, valor_anterior, valor_nuevo, detalle)
SELECT @admin_usuario_id, 'USUARIO_ROL_CAMBIADO', 'usuario', @uid_brian,
       JSON_OBJECT('rol_organizacional', @brian_rol_previo), JSON_OBJECT('rol_organizacional', NULL),
       'Corrección de consistencia v7: brian.castro@nexacorp.com vuelve a ser Miembro (tiene asignaciones activas como colaborador; Admin/RM no participan en proyectos).'
FROM DUAL WHERE @a2_brian > 0;

COMMIT;

-- Resumen de lo aplicado en esta ejecución (1 = se corrigió, 0 = ya estaba bien)
SELECT @a1_luis AS a1_luis_deja_aurora, @a1_tomas AS a1_tomas_lidera_aurora, @a2_brian AS a2_brian_vuelve_a_miembro;

-- =====================================================================
-- PARTE 4 — VERIFICACIÓN (mismo diagnóstico de la PARTE 1)
-- Después de la PARTE 3 todas deberían volver vacías. Si aparece algo
-- más (datos que cargaste tú), decídelo caso a caso con la app:
--   * Admin/RM con asignaciones -> finaliza la asignación o cambia su rol.
--   * Carga > 100%              -> "Rebalancear" en Ocupación (RM).
--   * Proyecto sin PM           -> "Cambiar PM" en Asignaciones (RM).
-- =====================================================================

SELECT 'Verificación 1.1' AS chequeo, u.correo, pr.nombre
FROM asignaciones a JOIN perfiles p ON p.id = a.perfil_id JOIN usuarios u ON u.id = p.usuario_id
JOIN proyectos pr ON pr.id = a.proyecto_id
WHERE a.estado = 'activa' AND u.rol_organizacional IS NOT NULL;

SELECT 'Verificación 1.2' AS chequeo, u.correo, SUM(a.carga_porcentaje) AS carga_efectiva
FROM asignaciones a JOIN perfiles p ON p.id = a.perfil_id JOIN usuarios u ON u.id = p.usuario_id
JOIN proyectos pr ON pr.id = a.proyecto_id
WHERE a.estado = 'activa' AND pr.estado IN ('planificacion','activo','en_pausa')
GROUP BY u.id, u.correo HAVING SUM(a.carga_porcentaje) > 100;

SELECT 'Verificación 1.3' AS chequeo, pr.nombre
FROM proyectos pr
WHERE pr.estado IN ('planificacion','activo','en_pausa')
  AND NOT EXISTS (SELECT 1 FROM asignaciones a JOIN perfiles p ON p.id = a.perfil_id
                  JOIN usuarios u ON u.id = p.usuario_id
                  WHERE a.proyecto_id = pr.id AND a.rol_en_proyecto = 'project_manager'
                    AND a.estado = 'activa' AND u.rol_organizacional IS NULL AND u.estado = 'activo');
