-- Ejecutar sobre el esquema existente antes de desplegar el JAR.
SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns
 WHERE table_schema=DATABASE() AND table_name='tipos_audiencia' AND column_name='politica'),
 'SELECT 1', 'ALTER TABLE tipos_audiencia ADD COLUMN politica VARCHAR(40)');
PREPARE migracion FROM @ddl;
EXECUTE migracion;
DEALLOCATE PREPARE migracion;

SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns
 WHERE table_schema=DATABASE() AND table_name='correos_autorizados' AND column_name='estado_envio'),
 'SELECT 1', 'ALTER TABLE correos_autorizados ADD COLUMN estado_envio VARCHAR(32)');
PREPARE migracion FROM @ddl;
EXECUTE migracion;
DEALLOCATE PREPARE migracion;

SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns
 WHERE table_schema=DATABASE() AND table_name='correos_autorizados' AND column_name='ultimo_envio'),
 'SELECT 1', 'ALTER TABLE correos_autorizados ADD COLUMN ultimo_envio DATETIME NULL');
PREPARE migracion FROM @ddl;
EXECUTE migracion;
DEALLOCATE PREPARE migracion;

SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns
 WHERE table_schema=DATABASE() AND table_name='correos_autorizados' AND column_name='origen_carga'
 AND column_type LIKE '%masiva_excel%'), 'SELECT 1',
 'ALTER TABLE correos_autorizados MODIFY origen_carga ENUM(''individual'',''masiva_csv'',''masiva_excel'') NOT NULL DEFAULT ''individual''');
PREPARE migracion FROM @ddl;
EXECUTE migracion;
DEALLOCATE PREPARE migracion;

INSERT INTO tipos_notificacion(codigo,nombre) VALUES
 ('asignacion','Asignación'), ('entregable_entregado','Entrega recibida'), ('alerta','Alerta'),
 ('info','Información'), ('solicitud','Solicitud'), ('resultado_ia','Resultado de propuesta'),
 ('foro_respuesta','Respuesta en foro'), ('foro_cita','Cita en foro'),
 ('entregable_nuevo','Nuevo entregable'), ('entregable_revisado','Entrega revisada')
 ON DUPLICATE KEY UPDATE codigo=tipos_notificacion.codigo;
