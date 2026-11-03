-- ============================================================
-- Migración v9 (cursos): catálogo de formación del Administrador
-- (AdminCursosController / CursoService / model.Curso).
-- La aplicación también la crea sola al arrancar si no existe
-- (config/InicializadorEsquemaCursos); este script es para aplicarla a mano.
--
-- Las columnas id / categoria_id / habilidad_id son BIGINT UNSIGNED porque
-- categorias_habilidad.id y habilidades.id lo son: MySQL 8 rechaza una FK
-- entre BIGINT y BIGINT UNSIGNED (error 3780).
-- ============================================================
CREATE TABLE IF NOT EXISTS cursos (
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
  CONSTRAINT fk_curso_categoria FOREIGN KEY (categoria_id) REFERENCES categorias_habilidad(id),
  CONSTRAINT fk_curso_habilidad FOREIGN KEY (habilidad_id) REFERENCES habilidades(id),
  INDEX idx_cursos_estado_titulo (estado, titulo),
  INDEX idx_cursos_categoria (categoria_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
