-- Ejecutar sobre el esquema existente antes de desplegar el JAR.
SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns
 WHERE table_schema=DATABASE() AND table_name='tipos_audiencia' AND column_name='politica'),
 'SELECT 1', 'ALTER TABLE tipos_audiencia ADD COLUMN politica VARCHAR(40)');
PREPARE migracion FROM @ddl;
EXECUTE migracion;
DEALLOCATE PREPARE migracion;

CREATE TABLE IF NOT EXISTS seguridad_incidente (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 regla VARCHAR(32) NOT NULL, ventana BIGINT NOT NULL,
 fecha TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 ultimo TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 cantidad INT NOT NULL DEFAULT 1, estado VARCHAR(20) NOT NULL DEFAULT 'indicio',
 severidad VARCHAR(12) NOT NULL DEFAULT 'baja', categoria VARCHAR(12) NOT NULL,
 UNIQUE KEY grupo(regla,ventana)
);
CREATE TABLE IF NOT EXISTS seguridad_portal (
 id INT PRIMARY KEY, activo BOOLEAN NOT NULL DEFAULT FALSE,
 responsable BIGINT, incidente BIGINT, version BIGINT NOT NULL DEFAULT 0,
 motivo VARCHAR(300), fecha TIMESTAMP NULL
);
INSERT INTO seguridad_portal(id) VALUES(1) ON DUPLICATE KEY UPDATE id=1;
CREATE TABLE IF NOT EXISTS seguridad_historial (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, incidente BIGINT, actor BIGINT,
 fecha TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, accion VARCHAR(50) NOT NULL, motivo VARCHAR(300)
);
CREATE TABLE IF NOT EXISTS seguridad_aviso (
 incidente BIGINT NOT NULL, perfil BIGINT NOT NULL, estado VARCHAR(24) NOT NULL DEFAULT 'en cola',
 PRIMARY KEY(incidente,perfil)
);
CREATE TABLE IF NOT EXISTS seguridad_intentos (
 actor BIGINT PRIMARY KEY, fallos INT NOT NULL DEFAULT 0, inicio BIGINT NOT NULL, ultimo_paso BIGINT NOT NULL DEFAULT -1
);

SET @ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns
 WHERE table_schema=DATABASE() AND table_name='correos_autorizados' AND column_name='estado_envio'),
 'SELECT 1', 'ALTER TABLE correos_autorizados ADD COLUMN estado_envio VARCHAR(32)');
PREPARE migracion FROM @ddl;
EXECUTE migracion;
DEALLOCATE PREPARE migracion;

INSERT INTO tipos_notificacion(codigo,nombre) VALUES
 ('asignacion','Asignación'), ('entregable_entregado','Entrega recibida'), ('alerta','Alerta'),
 ('info','Información'), ('solicitud','Solicitud'), ('resultado_ia','Resultado de propuesta'),
 ('foro_respuesta','Respuesta en foro'), ('foro_cita','Cita en foro'),
 ('entregable_nuevo','Nuevo entregable'), ('entregable_revisado','Entrega revisada')
 ON DUPLICATE KEY UPDATE codigo=tipos_notificacion.codigo;
