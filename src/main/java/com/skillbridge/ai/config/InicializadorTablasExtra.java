package com.skillbridge.ai.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Crea, si todavía no existen, las tablas que se agregaron después del
 * esquema base: "archivos_proyecto" (archivos de contexto del PM) y
 * "cursos" (módulo Cursos del Administrador). Igual que Inicializador2fa
 * con usuarios_2fa: con ddl-auto=none, sin esto esas pantallas revientan en
 * una base creada con una versión anterior de db/skillbridge_db.sql.
 * Mismo SQL que la sección 6 de ese script. Si la tabla ya existe no la toca.
 */
@Component
@Order(1)
public class InicializadorTablasExtra implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(InicializadorTablasExtra.class);
    private final JdbcTemplate jdbc;

    public InicializadorTablasExtra(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(String... args) {
        try {
            jdbc.execute("CREATE TABLE IF NOT EXISTS archivos_proyecto ("
                    + " id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,"
                    + " proyecto_id BIGINT UNSIGNED NOT NULL,"
                    + " nombre VARCHAR(255) NOT NULL,"
                    + " tipo VARCHAR(100),"
                    + " datos MEDIUMBLOB NOT NULL,"
                    + " fecha_subida DATETIME DEFAULT CURRENT_TIMESTAMP,"
                    + " CONSTRAINT fk_archivo_proyecto FOREIGN KEY (proyecto_id)"
                    + " REFERENCES proyectos(id) ON DELETE CASCADE"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        } catch (Exception e) {
            log.warn("No se pudo verificar/crear la tabla archivos_proyecto: {}", e.getMessage());
        }
        try {
            jdbc.execute("CREATE TABLE IF NOT EXISTS cursos ("
                    + " id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,"
                    + " titulo VARCHAR(150) NOT NULL,"
                    + " descripcion TEXT NULL,"
                    + " url VARCHAR(500) NULL,"
                    + " categoria_id BIGINT UNSIGNED NULL,"
                    + " habilidad_id BIGINT UNSIGNED NULL,"
                    + " nivel VARCHAR(20) NOT NULL DEFAULT 'BASICO',"
                    + " estado VARCHAR(20) NOT NULL DEFAULT 'PUBLICADO',"
                    + " duracion_horas INT NOT NULL DEFAULT 1,"
                    + " fecha_creacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                    + " fecha_actualizacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,"
                    + " CONSTRAINT fk_curso_categoria FOREIGN KEY (categoria_id) REFERENCES categorias_habilidad(id),"
                    + " CONSTRAINT fk_curso_habilidad FOREIGN KEY (habilidad_id) REFERENCES habilidades(id),"
                    + " INDEX idx_cursos_estado_titulo (estado, titulo),"
                    + " INDEX idx_cursos_categoria (categoria_id)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        } catch (Exception e) {
            log.warn("No se pudo verificar/crear la tabla cursos: {}", e.getMessage());
        }
        catalogoDeFormacion();
        chatDeProyectos();
        memoriaDelAsistente();
    }

    // ───────── Memoria del Asistente IA (chatbot) ─────────

    private void memoriaDelAsistente() {
        try {
            jdbc.execute("CREATE TABLE IF NOT EXISTS asistente_conversaciones (     id                   BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,     usuario_id           BIGINT UNSIGNED NOT NULL,     rol                  VARCHAR(30) NOT NULL,     titulo               VARCHAR(120) NOT NULL,     fecha_creacion       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,     fecha_actualizacion  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,     INDEX idx_asist_conv_usuario (usuario_id, rol, fecha_actualizacion),     CONSTRAINT fk_asist_conv_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            jdbc.execute("CREATE TABLE IF NOT EXISTS asistente_mensajes (     id               BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,     conversacion_id  BIGINT UNSIGNED NOT NULL,     autor            VARCHAR(10) NOT NULL,     contenido        TEXT NOT NULL,     fecha            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,     INDEX idx_asist_msg_conv (conversacion_id, id),     CONSTRAINT fk_asist_msg_conv FOREIGN KEY (conversacion_id) REFERENCES asistente_conversaciones(id) ON DELETE CASCADE ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        } catch (Exception e) {
            log.warn("No se pudieron verificar/crear las tablas del Asistente IA: {}", e.getMessage());
        }
    }

    // ───────── Chat de proyectos (RF07) ─────────

    private void chatDeProyectos() {
        try {
            jdbc.execute("CREATE TABLE IF NOT EXISTS chat_lecturas ("
                    + " sala_id BIGINT UNSIGNED NOT NULL,"
                    + " perfil_id BIGINT UNSIGNED NOT NULL,"
                    + " ultimo_mensaje_id BIGINT UNSIGNED NOT NULL DEFAULT 0,"
                    + " PRIMARY KEY (sala_id, perfil_id),"
                    + " CONSTRAINT fk_lectura_sala FOREIGN KEY (sala_id) REFERENCES chat_salas(id) ON DELETE CASCADE,"
                    + " CONSTRAINT fk_lectura_perfil FOREIGN KEY (perfil_id) REFERENCES perfiles(id) ON DELETE CASCADE"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        } catch (Exception e) {
            log.warn("No se pudo verificar/crear la tabla chat_lecturas: {}", e.getMessage());
        }
        try {
            sembrarChatDemo();
        } catch (Exception e) {
            log.warn("No se pudieron cargar las conversaciones de ejemplo del chat: {}", e.getMessage());
        }
    }

    /**
     * Una sola vez (si el chat está vacío): conversaciones de ejemplo en los
     * proyectos activos, escritas por sus integrantes reales, para la demo.
     */
    private void sembrarChatDemo() {
        Integer hay = jdbc.queryForObject("SELECT COUNT(*) FROM chat_mensajes", Integer.class);
        if (hay != null && hay > 0) return;
        java.util.List<Long> proyectos = jdbc.queryForList(
                "SELECT p.id FROM proyectos p WHERE p.estado = 'activo' AND "
                        + "(SELECT COUNT(*) FROM asignaciones a WHERE a.proyecto_id = p.id AND a.estado = 'activa') >= 2 "
                        + "ORDER BY p.id LIMIT 3", Long.class);
        String[][] guiones = {
                {"¡Hola equipo! Abro la sala para coordinar lo del sprint de esta semana.",
                 "Perfecto. Yo sigo con la integración del login; mañana subo el PR.",
                 "Ojo con el endpoint de usuarios: cambió el formato de las fechas.",
                 "Anotado, lo ajusto. ¿Hacemos la demo el viernes a las 4?",
                 "Viernes 4 pm me va bien. Preparo los datos de prueba."},
                {"Buenas, ¿alguien revisó los resultados del pipeline de anoche?",
                 "Sí, falló un test de integración por la base de datos de pruebas. Ya lo corregí.",
                 "Gracias. Entonces podemos desplegar hoy a staging.",
                 "De acuerdo. Después del despliegue dejo el enlace en el foro del proyecto."},
                {"Equipo, subí el tablero con las tareas pendientes del módulo de reportes.",
                 "Me tomo la exportación a Excel.",
                 "Yo reviso los filtros por fecha. ¿Alguna duda con los criterios?",
                 "Ninguna por ahora. Avisamos aquí si algo se traba."}
        };
        int g = 0;
        for (Long proyectoId : proyectos) {
            java.util.List<Long> miembros = jdbc.queryForList(
                    "SELECT perfil_id FROM asignaciones WHERE proyecto_id = ? AND estado = 'activa' "
                            + "ORDER BY rol_en_proyecto DESC, id", Long.class, proyectoId);
            Long salaId = jdbc.queryForList("SELECT id FROM chat_salas WHERE proyecto_id = ? ORDER BY id LIMIT 1", Long.class, proyectoId)
                    .stream().findFirst().orElse(null);
            if (salaId == null) {
                org.springframework.jdbc.support.GeneratedKeyHolder llave = new org.springframework.jdbc.support.GeneratedKeyHolder();
                jdbc.update(con -> {
                    java.sql.PreparedStatement ps = con.prepareStatement(
                            "INSERT INTO chat_salas (proyecto_id, nombre) VALUES (?, 'General')", java.sql.Statement.RETURN_GENERATED_KEYS);
                    ps.setLong(1, proyectoId);
                    return ps;
                }, llave);
                salaId = llave.getKey().longValue();
            }
            String[] guion = guiones[g++ % guiones.length];
            for (int i = 0; i < guion.length; i++) {
                // Mensajes repartidos en las últimas horas, del más viejo al más nuevo.
                // La hora se calcula en Java (igual que los mensajes reales, ChatMensaje):
                // con NOW() de MySQL podía quedar en otra zona horaria y desordenarse.
                jdbc.update("INSERT INTO chat_mensajes (sala_id, autor_id, contenido, fecha_envio) VALUES (?, ?, ?, ?)",
                        salaId, miembros.get(i % miembros.size()), guion[i],
                        java.sql.Timestamp.valueOf(java.time.LocalDateTime.now().minusMinutes((long) (guion.length - i) * 37)));
            }
        }
        if (!proyectos.isEmpty()) log.info("Chat: se cargaron conversaciones de ejemplo en {} proyecto(s).", proyectos.size());
    }

    // ───────── Catálogo de formación (v11): tipos, plataforma, rutas ─────────

    private void catalogoDeFormacion() {
        agregarSiFalta("cursos", "tipo", "VARCHAR(20) NOT NULL DEFAULT 'CURSO'");
        agregarSiFalta("cursos", "plataforma", "VARCHAR(40) NULL");
        agregarSiFalta("cursos", "gratuito", "TINYINT(1) NOT NULL DEFAULT 1");
        agregarSiFalta("cursos", "con_certificado", "TINYINT(1) NOT NULL DEFAULT 0");
        agregarSiFalta("cursos", "idioma", "VARCHAR(5) NOT NULL DEFAULT 'ES'");
        agregarSiFalta("cursos", "destacado", "TINYINT(1) NOT NULL DEFAULT 0");
        try {
            jdbc.execute("CREATE TABLE IF NOT EXISTS curso_pasos ("
                    + " curso_id BIGINT UNSIGNED NOT NULL,"
                    + " orden INT NOT NULL,"
                    + " titulo VARCHAR(150) NOT NULL,"
                    + " url VARCHAR(500) NULL,"
                    + " PRIMARY KEY (curso_id, orden),"
                    + " CONSTRAINT fk_paso_curso FOREIGN KEY (curso_id) REFERENCES cursos(id) ON DELETE CASCADE"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        } catch (Exception e) {
            log.warn("No se pudo verificar/crear la tabla curso_pasos: {}", e.getMessage());
        }
        try {
            sembrarCatalogoDemo();
        } catch (Exception e) {
            log.warn("No se pudo sembrar el catálogo de formación de ejemplo: {}", e.getMessage());
        }
    }

    private void agregarSiFalta(String tabla, String columna, String definicion) {
        try {
            Integer existe = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.COLUMNS "
                            + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                    Integer.class, tabla, columna);
            if (existe != null && existe == 0) {
                jdbc.execute("ALTER TABLE " + tabla + " ADD COLUMN " + columna + " " + definicion);
                log.info("Catálogo de formación: agregada la columna {}.{}", tabla, columna);
            }
        } catch (Exception e) {
            log.warn("No se pudo verificar/agregar {}.{}: {}", tabla, columna, e.getMessage());
        }
    }

    /**
     * Una sola vez: si todavía no hay ningún recurso "nuevo" (con plataforma o
     * de tipo ruta/especialización), carga un catálogo inicial de recursos
     * GRATUITOS reales para que la vista no arranque vacía. El Administrador
     * puede editarlos o archivarlos como cualquier otro.
     */
    private void sembrarCatalogoDemo() {
        Integer nuevos = jdbc.queryForObject("SELECT COUNT(*) FROM cursos WHERE plataforma IS NOT NULL "
                + "OR tipo IN ('RUTA','ESPECIALIZACION','VIDEO')", Integer.class);
        if (nuevos != null && nuevos > 0) return;

        long python = curso("Python para principiantes — curso completo",
                "Variables, condicionales, bucles, funciones y estructuras de datos en un solo video de freeCodeCamp. Ideal para empezar desde cero.",
                "https://www.youtube.com/watch?v=rfscVS0vtbw", "VIDEO", "Python", "BASICO", 5, true, false, "EN", true);
        curso("SQL y bases de datos desde cero",
                "Diseño de tablas, consultas, joins y buenas prácticas con un curso completo y gratuito.",
                "https://www.youtube.com/watch?v=HXV3zeQKqGY", "VIDEO", "MySQL", "BASICO", 4, true, false, "EN", false);
        curso("Docker para principiantes",
                "Imágenes, contenedores, volúmenes y Docker Compose explicados paso a paso por TechWorld with Nana.",
                "https://www.youtube.com/watch?v=3c-iBn73dDE", "VIDEO", "Docker", "INTERMEDIO", 3, true, false, "EN", true);
        curso("Kubernetes desde cero",
                "Pods, deployments, services e ingress con ejemplos prácticos para pasar de Docker a Kubernetes.",
                "https://www.youtube.com/watch?v=X48VuDVv0do", "VIDEO", "Kubernetes", "INTERMEDIO", 4, true, false, "EN", false);
        curso("React: curso para principiantes",
                "Componentes, props, estado y hooks construyendo proyectos reales.",
                "https://www.youtube.com/watch?v=bMknfKXIFA8", "VIDEO", "React", "BASICO", 12, true, false, "EN", false);
        curso("Spring Boot: API REST paso a paso",
                "Crea una API REST con Spring Boot, Spring Data JPA y PostgreSQL desde cero.",
                "https://www.youtube.com/watch?v=9SGDpanrc8U", "VIDEO", "Spring Boot", "INTERMEDIO", 2, true, false, "EN", false);
        curso("Responsive Web Design (certificación gratuita)",
                "HTML y CSS modernos con proyectos prácticos. Al terminar los proyectos obtienes una certificación verificable de freeCodeCamp.",
                "https://www.freecodecamp.org/learn/2022/responsive-web-design/", "CURSO", "React", "BASICO", 300, true, true, "ES", true);
        curso("Fundamentos de Azure (AZ-900)",
                "Ruta oficial y gratuita de Microsoft Learn para entender la nube, sus servicios y precios. Prepara la certificación AZ-900.",
                "https://learn.microsoft.com/es-es/training/paths/microsoft-azure-fundamentals-describe-cloud-concepts/", "CURSO", null, "BASICO", 6, true, true, "ES", false);
        curso("AWS Cloud Practitioner Essentials",
                "Curso oficial gratuito de AWS Skill Builder: conceptos de nube, servicios principales, seguridad y facturación.",
                "https://explore.skillbuilder.aws/learn/course/external/view/elearning/134/aws-cloud-practitioner-essentials", "CURSO", "AWS", "BASICO", 6, true, true, "ES", false);
        curso("Python en Kaggle Learn",
                "Micro-curso práctico con ejercicios en notebooks. Kaggle entrega un certificado de finalización.",
                "https://www.kaggle.com/learn/python", "CURSO", "Python", "BASICO", 5, true, true, "EN", false);
        curso("Introducción a la ciberseguridad",
                "Curso gratuito de Cisco Networking Academy con insignia digital al completarlo.",
                "https://www.netacad.com/courses/introduction-to-cybersecurity", "CURSO", null, "BASICO", 6, true, true, "ES", false);
        curso("CS50: Introducción a las Ciencias de la Computación",
                "El curso de Harvard en edX: algoritmos, C, Python, SQL y web. Gratis para auditar; certificado opcional.",
                "https://www.edx.org/learn/computer-science/harvard-cs50-s-introduction-to-computer-science", "CURSO", null, "INTERMEDIO", 120, true, true, "EN", false);

        long backend = curso("Ruta Backend Developer",
                "Roadmap visual de roadmap.sh con todo lo que necesita un backend: lenguajes, bases de datos, APIs, seguridad y despliegue.",
                "https://roadmap.sh/backend", "RUTA", "Java", "INTERMEDIO", 200, true, false, "EN", true);
        pasos(backend, new String[][]{
                {"Git y GitHub para principiantes", "https://www.youtube.com/watch?v=RGOj5yH7evk"},
                {"SQL y bases de datos desde cero", "https://www.youtube.com/watch?v=HXV3zeQKqGY"},
                {"Spring Boot: API REST paso a paso", "https://www.youtube.com/watch?v=9SGDpanrc8U"},
                {"Docker para principiantes", "https://www.youtube.com/watch?v=3c-iBn73dDE"},
                {"Roadmap completo en roadmap.sh", "https://roadmap.sh/backend"}});
        long devops = curso("Ruta DevOps",
                "Del control de versiones a Kubernetes y la nube: el camino recomendado para quien quiere entrar a DevOps.",
                "https://roadmap.sh/devops", "RUTA", "Kubernetes", "INTERMEDIO", 150, true, false, "EN", false);
        pasos(devops, new String[][]{
                {"Git y GitHub para principiantes", "https://www.youtube.com/watch?v=RGOj5yH7evk"},
                {"Docker para principiantes", "https://www.youtube.com/watch?v=3c-iBn73dDE"},
                {"Kubernetes desde cero", "https://www.youtube.com/watch?v=X48VuDVv0do"},
                {"AWS Cloud Practitioner Essentials", "https://explore.skillbuilder.aws/learn/course/external/view/elearning/134/aws-cloud-practitioner-essentials"},
                {"Roadmap DevOps en roadmap.sh", "https://roadmap.sh/devops"}});
        long fullstack = curso("Especialización Full Stack Java + React",
                "Programa armado por el equipo con recursos gratuitos: backend con Spring Boot, base de datos, frontend con React y despliegue con Docker.",
                null, "ESPECIALIZACION", "Spring Boot", "INTERMEDIO", 40, true, false, "ES", true);
        pasos(fullstack, new String[][]{
                {"SQL y bases de datos desde cero", "https://www.youtube.com/watch?v=HXV3zeQKqGY"},
                {"Spring Boot: API REST paso a paso", "https://www.youtube.com/watch?v=9SGDpanrc8U"},
                {"React: curso para principiantes", "https://www.youtube.com/watch?v=bMknfKXIFA8"},
                {"Docker para principiantes", "https://www.youtube.com/watch?v=3c-iBn73dDE"}});
        log.info("Catálogo de formación: se cargaron recursos de ejemplo (ids {} a {}).", python, fullstack);
    }

    /** Inserta un recurso; la categoría sale de la habilidad (si existe con ese nombre). */
    private long curso(String titulo, String descripcion, String url, String tipo, String habilidad, String nivel,
                       int horas, boolean gratuito, boolean certificado, String idioma, boolean destacado) {
        org.springframework.jdbc.support.GeneratedKeyHolder llave = new org.springframework.jdbc.support.GeneratedKeyHolder();
        jdbc.update(con -> {
            java.sql.PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO cursos (titulo, descripcion, url, tipo, habilidad_id, categoria_id, nivel, estado, duracion_horas,"
                            + " gratuito, con_certificado, idioma, destacado)"
                            + " VALUES (?, ?, ?, ?, (SELECT id FROM habilidades WHERE nombre = ?),"
                            + " (SELECT categoria_id FROM habilidades WHERE nombre = ?), ?, 'PUBLICADO', ?, ?, ?, ?, ?)",
                    java.sql.Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, titulo); ps.setString(2, descripcion); ps.setString(3, url); ps.setString(4, tipo);
            ps.setString(5, habilidad); ps.setString(6, habilidad); ps.setString(7, nivel); ps.setInt(8, horas);
            ps.setBoolean(9, gratuito); ps.setBoolean(10, certificado); ps.setString(11, idioma); ps.setBoolean(12, destacado);
            return ps;
        }, llave);
        return llave.getKey() == null ? -1 : llave.getKey().longValue();
    }

    private void pasos(long cursoId, String[][] pasos) {
        for (int i = 0; i < pasos.length; i++) {
            jdbc.update("INSERT INTO curso_pasos (curso_id, orden, titulo, url) VALUES (?, ?, ?, ?)",
                    cursoId, i, pasos[i][0], pasos[i][1]);
        }
    }
}
