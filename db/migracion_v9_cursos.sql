CREATE TABLE IF NOT EXISTS cursos (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  titulo VARCHAR(150) NOT NULL,
  descripcion TEXT NULL,
  url VARCHAR(500) NULL,
  categoria_id BIGINT NULL,
  habilidad_id BIGINT NULL,
  nivel VARCHAR(20) NOT NULL DEFAULT 'BASICO',
  estado VARCHAR(20) NOT NULL DEFAULT 'PUBLICADO',
  duracion_horas INT NOT NULL DEFAULT 1,
  fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  fecha_actualizacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_curso_categoria FOREIGN KEY (categoria_id) REFERENCES categorias_habilidad(id),
  CONSTRAINT fk_curso_habilidad FOREIGN KEY (habilidad_id) REFERENCES habilidades(id),
  INDEX idx_cursos_estado_titulo (estado, titulo),
  INDEX idx_cursos_categoria (categoria_id)
);
