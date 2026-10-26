-- ============================================================
-- Migración v9: perfil profesional del colaborador
--   (vista resource-manager/colaborador-detalle.html)
--
-- La app ya aplica estas columnas sola al arrancar
-- (config/InicializadorEsquemaV9.java); este script es para quien
-- prefiera hacerlo a mano. MySQL 8 no tiene "ADD COLUMN IF NOT EXISTS":
-- si alguna columna ya existe, ejecuta solo las que falten.
-- ============================================================

ALTER TABLE perfiles
    ADD COLUMN portafolio_url VARCHAR(500) DEFAULT NULL COMMENT 'Portafolio o sitio web personal',
    ADD COLUMN telefono       VARCHAR(30)  DEFAULT NULL COMMENT 'Teléfono de contacto',
    ADD COLUMN ubicacion      VARCHAR(120) DEFAULT NULL COMMENT 'Ciudad / país o modalidad',
    ADD COLUMN etiquetas      JSON         DEFAULT NULL COMMENT 'Etiquetas cortas sobre la persona (arreglo JSON)';

-- ------------------------------------------------------------
-- Datos de ejemplo para la demo (colaboradores de datos_demo_skillbridge.sql).
-- Solo completa perfiles que todavía no tienen estos datos.
-- Teléfonos y enlaces son ficticios.
-- ------------------------------------------------------------
UPDATE perfiles p JOIN usuarios u ON u.id = p.usuario_id SET
    p.biografia = COALESCE(p.biografia, 'Desarrollador frontend enfocado en interfaces accesibles y rápidas. Me gusta convertir diseños de Figma en componentes reutilizables y medir su rendimiento.'),
    p.ubicacion = 'Lima, Perú', p.telefono = '+51 900 000 101',
    p.linkedin_url = COALESCE(p.linkedin_url, 'https://www.linkedin.com/in/brian-castro-demo'),
    p.github_url   = COALESCE(p.github_url,   'https://github.com/brian-castro-demo'),
    p.portafolio_url = 'https://brian-castro-demo.dev',
    p.etiquetas = JSON_ARRAY('Accesibilidad', 'Design systems', 'Mentor de practicantes', 'Inglés B2')
WHERE u.correo = 'brian.castro@nexacorp.com' AND p.telefono IS NULL;

UPDATE perfiles p JOIN usuarios u ON u.id = p.usuario_id SET
    p.biografia = COALESCE(p.biografia, 'Ingeniera DevOps. Automatizo despliegues en AWS y cuido que los entornos sean reproducibles con Docker e infraestructura como código.'),
    p.ubicacion = 'Arequipa, Perú · remoto', p.telefono = '+51 900 000 102',
    p.linkedin_url = COALESCE(p.linkedin_url, 'https://www.linkedin.com/in/fiorella-torres-demo'),
    p.github_url   = COALESCE(p.github_url,   'https://github.com/fiorella-torres-demo'),
    p.etiquetas = JSON_ARRAY('AWS Cloud Practitioner', 'CI/CD', 'Guardias on-call', 'Inglés C1')
WHERE u.correo = 'fiorella.torres@nexacorp.com' AND p.telefono IS NULL;

UPDATE perfiles p JOIN usuarios u ON u.id = p.usuario_id SET
    p.biografia = COALESCE(p.biografia, 'Analista de datos. Construyo tableros y modelos para que las decisiones del negocio se basen en números claros.'),
    p.ubicacion = 'Lima, Perú', p.telefono = '+51 900 000 103',
    p.linkedin_url = COALESCE(p.linkedin_url, 'https://www.linkedin.com/in/eduardo-campos-demo'),
    p.etiquetas = JSON_ARRAY('Storytelling con datos', 'Power BI', 'Curioso')
WHERE u.correo = 'eduardo.campos@nexacorp.com' AND p.telefono IS NULL;

UPDATE perfiles p JOIN usuarios u ON u.id = p.usuario_id SET
    p.biografia = COALESCE(p.biografia, 'Backend developer con cinco años en Java y Spring. Diseño APIs REST limpias y me interesa la observabilidad de los servicios.'),
    p.ubicacion = 'Trujillo, Perú', p.telefono = '+51 900 000 104',
    p.linkedin_url = COALESCE(p.linkedin_url, 'https://www.linkedin.com/in/gabriel-chavez-demo'),
    p.github_url   = COALESCE(p.github_url,   'https://github.com/gabriel-chavez-demo'),
    p.etiquetas = JSON_ARRAY('APIs REST', 'Code review', 'Líder técnico', 'Inglés B2')
WHERE u.correo = 'gabriel.chavez@nexacorp.com' AND p.telefono IS NULL;

UPDATE perfiles p JOIN usuarios u ON u.id = p.usuario_id SET
    p.biografia = COALESCE(p.biografia, 'Diseñadora UX/UI. Investigo con usuarios reales antes de dibujar y documento cada decisión en el sistema de diseño.'),
    p.ubicacion = 'Lima, Perú · híbrido', p.telefono = '+51 900 000 105',
    p.linkedin_url = COALESCE(p.linkedin_url, 'https://www.linkedin.com/in/helena-ponce-demo'),
    p.portafolio_url = 'https://helena-ponce-demo.design',
    p.etiquetas = JSON_ARRAY('Investigación UX', 'Figma', 'Facilitadora de talleres')
WHERE u.correo = 'helena.ponce@nexacorp.com' AND p.telefono IS NULL;

UPDATE perfiles p JOIN usuarios u ON u.id = p.usuario_id SET
    p.biografia = COALESCE(p.biografia, 'Ingeniero backend y DevOps. Seis años llevando servicios Java a producción, con foco en rendimiento y costos de nube.'),
    p.ubicacion = 'Cusco, Perú · remoto', p.telefono = '+51 900 000 106',
    p.linkedin_url = COALESCE(p.linkedin_url, 'https://www.linkedin.com/in/ivan-rojas-demo'),
    p.github_url   = COALESCE(p.github_url,   'https://github.com/ivan-rojas-demo'),
    p.etiquetas = JSON_ARRAY('Kubernetes', 'Optimización de costos', 'Mentor')
WHERE u.correo = 'ivan.rojas@nexacorp.com' AND p.telefono IS NULL;

UPDATE perfiles p JOIN usuarios u ON u.id = p.usuario_id SET
    p.biografia = COALESCE(p.biografia, 'QA engineer. Escribo pruebas automatizadas de punta a punta y ayudo a los equipos a definir criterios de aceptación medibles.'),
    p.ubicacion = 'Lima, Perú', p.telefono = '+51 900 000 107',
    p.linkedin_url = COALESCE(p.linkedin_url, 'https://www.linkedin.com/in/karla-medina-demo'),
    p.etiquetas = JSON_ARRAY('Testing automatizado', 'Detallista', 'ISTQB')
WHERE u.correo = 'karla.medina@nexacorp.com' AND p.telefono IS NULL;

UPDATE perfiles p JOIN usuarios u ON u.id = p.usuario_id SET
    p.biografia = COALESCE(p.biografia, 'Data engineer. Diseño pipelines de datos confiables y me encargo de que lleguen limpios a los equipos de análisis.'),
    p.ubicacion = 'Piura, Perú · remoto', p.telefono = '+51 900 000 108',
    p.github_url   = COALESCE(p.github_url,   'https://github.com/renzo-fernandez-demo'),
    p.etiquetas = JSON_ARRAY('ETL', 'Spark', 'Inglés C1')
WHERE u.correo = 'renzo.fernandez@nexacorp.com' AND p.telefono IS NULL;

UPDATE perfiles p JOIN usuarios u ON u.id = p.usuario_id SET
    p.biografia = COALESCE(p.biografia, 'Backend developer senior. Siete años construyendo sistemas transaccionales; disfruto enseñar buenas prácticas de diseño.'),
    p.ubicacion = 'Lima, Perú', p.telefono = '+51 900 000 109',
    p.linkedin_url = COALESCE(p.linkedin_url, 'https://www.linkedin.com/in/tomas-herrera-demo'),
    p.github_url   = COALESCE(p.github_url,   'https://github.com/tomas-herrera-demo'),
    p.portafolio_url = 'https://tomas-herrera-demo.dev',
    p.etiquetas = JSON_ARRAY('Arquitectura', 'Microservicios', 'Mentor', 'Speaker interno')
WHERE u.correo = 'tomas.herrera@nexacorp.com' AND p.telefono IS NULL;
