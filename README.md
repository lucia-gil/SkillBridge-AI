# SkillBridge AI

Plataforma interna de gestión de talento y proyectos (empresa ficticia **NexaCorp**): usuarios y roles, catálogo de habilidades, proyectos, asignaciones con AI Talent Matching, entregables, foros, calendario, reportes y un Asistente IA conectado a Google Gemini.

**Stack:** Java 17 · Spring Boot 3.2.5 · Spring MVC + Thymeleaf · Spring Data JPA (Hibernate) · MySQL 8 · Spring Security · Spring Session JDBC · Java Mail (Gmail SMTP) · Apache POI / OpenPDF (exportes) · API de Google Gemini.

---

## Puesta en marcha

### 1. Base de datos

Con MySQL 8 corriendo:

```bash
mysql -u root -p < db/skillbridge_db.sql
```

`db/skillbridge_db.sql` es el **único script** del proyecto: crea el esquema `skillbridge_db` con todas las tablas, la siembra base (administrador, catálogos, configuración) y los datos de demo. Si el esquema ya existe, el script falla a propósito para no borrar datos; para empezar de cero, descomenta la línea `DROP SCHEMA IF EXISTS skillbridge_db;` del inicio.

Si ya tienes una base creada con una versión anterior, **no hace falta recrearla**: al arrancar, la app agrega sola lo que le falte (ver *Inicializadores* más abajo).

### 2. Secretos

Copia `src/main/resources/secretos.properties.example` como `src/main/resources/secretos.properties` (está en `.gitignore`, nunca se sube) y completa:

| Variable | Para qué | Si no se define |
|---|---|---|
| `DB_USERNAME` | Usuario de MySQL | `root` |
| `DB_PASSWORD` | Clave de MySQL | vacía |
| `MAIL_PASSWORD` | Contraseña de aplicación de Gmail (correos de invitación, recuperación y notificaciones) | la app arranca, pero no se envían correos |
| `GEMINI_API_KEY` | Asistente IA y resumen de foros ([clave gratis](https://aistudio.google.com/apikey)) | el asistente responde que no está configurado |

En el servidor se usan las mismas variables como variables de entorno (`export DB_PASSWORD='...'`), que tienen prioridad sobre `secretos.properties`.

### 3. Compilar y correr

```bash
mvn spring-boot:run
```

o generar el JAR y ejecutarlo:

```bash
mvn clean package
java -jar target/skillbridge-ai.jar
```

Abrir `http://localhost:8080/`.

### Cuentas de demo

Todas usan la contraseña **`admin123`**.

| Rol | Correo |
|---|---|
| Administrador | `admin@nexacorp.com` |
| Resource Manager | `luis.pena@nexacorp.com`, `camila.vidal@nexacorp.com` |
| Miembro que lidera proyectos (vista PM + Colaborador) | `ana.rios@nexacorp.com`, `jorge.paredes@nexacorp.com`, `mariana.solis@nexacorp.com`, `tomas.herrera@nexacorp.com` |
| Colaborador | `brian.castro@nexacorp.com`, `diego.salazar@nexacorp.com`, `fiorella.torres@nexacorp.com`, … |

---

## Roles y módulos

El rol fijo está en `usuarios.rol_organizacional`: `administrador`, `resource_manager` o `NULL` (= **Miembro**). Un Miembro entra como **Colaborador** y, si es Project Manager activo de algún proyecto (`asignaciones.rol_en_proyecto = 'project_manager'`), también puede usar la vista **Project Manager**.

Menú de cada rol (definido en `service/NavService`):

| Rol | Pantallas del menú |
|---|---|
| **Administrador** (`/administrador/**`) | Inicio, Usuarios y roles, Catálogo de habilidades, Cursos, Catálogos técnicos, Proyectos, Reportes globales, Auditoría, Configuración |
| **Resource Manager** (`/resource-manager/**`) | Inicio, Ocupación del equipo, Asignaciones, Colaboradores (con ficha de detalle), AI Talent Matching, Cursos, Reportes |
| **Project Manager** (`/project-manager/**`) | Inicio, Proyectos (con archivos de contexto), Asignaciones, Calendario, AI Talent Matching, Foros, Cursos, Asistente IA, Reportes |
| **Colaborador** (`/colaborador/**`) | Inicio, Mi perfil (habilidades y certificados), Mis proyectos, Entregables, Calendario, Foros, Cursos, Asistente IA, Notificaciones |
| Todos (menú del usuario, arriba a la derecha) | Mi cuenta (datos, foto, contraseña, doble factor TOTP), Notificaciones, buscador global (`Ctrl+K`) |

Detalles del Asistente IA y del resumen de foros con Gemini: ver [`ASISTENTE_IA.md`](ASISTENTE_IA.md).

### Catálogo de formación (Cursos)

El Administrador publica en *Cursos* cuatro tipos de recurso: **cursos**, **videos**, **rutas** (roadmaps por pasos) y **especializaciones** (programas de varios cursos). Cada recurso tiene enlace, plataforma (se detecta sola por el enlace: YouTube, Coursera, Microsoft Learn, roadmap.sh, freeCodeCamp…, ver `util/PlataformaCurso`), categoría y habilidad, nivel, duración, idioma, si es gratuito, si da certificado y si es destacado. Las rutas y especializaciones guardan sus pasos en orden (`curso_pasos`).

Lo **publicado** lo ven Colaborador, PM y RM en su vista *Cursos* (`CursosCatalogoController`, plantilla `compartido/cursos.html`): búsqueda, filtros (tipo, categoría, habilidad, nivel, plataforma, idioma, gratis, certificado, "Para ti" según las habilidades declaradas y guardados), reproductor de YouTube integrado y progreso de cada ruta. Guardados y progreso se guardan en el navegador de cada persona. Todo lo pinta `js/cursos.js` (el mismo archivo maneja la vista del Administrador).

### Configuración global

La pantalla *Configuración* del Administrador edita `configuracion_global`. Claves que hoy controlan comportamiento real: `dominio_correo_permitido`, `expiracion_sesion_minutos`, `limite_carga_colaborador`, `maximo_proyectos_simultaneos` y los tres `peso_matching_*` (AI Talent Matching). Las demás se guardan pero todavía no tienen efecto.

### Valores calculados (no son columnas)

- **% de avance de proyecto**: heurístico por tiempo transcurrido entre `fecha_inicio` y `fecha_fin_estimada` (`ProyectoService.avanceHeuristico`); la fuente de verdad del trabajo entregado es el módulo de Entregables.
- **Demanda de una habilidad**: según cuántos proyectos la requieren.
- **Tipo / severidad / origen en Auditoría**: derivados del texto de la acción (`AuditoriaFila`).

---

## Seguridad

- **Spring Security** (`config/SecurityConfig`): login por formulario, acceso por rol según la URL, CSRF en todos los POST, logout por POST, cabeceras de seguridad (CSP, frame-options, referrer-policy).
- **`security/SesionVigenteFilter`** revalida al usuario contra la BD en cada request: si lo suspenden o le cambian el rol, se refleja sin esperar a que cierre sesión.
- **`interceptor/SesionInterceptor`** fija la vista activa (rol efectivo) según el área visitada.
- Cada controlador verifica además que el recurso pertenezca al usuario o a su proyecto (p. ej. un PM solo gestiona sus proyectos; los archivos y entregas solo los descargan los integrantes del proyecto).
- Contraseñas con BCrypt y política de 12+ caracteres (`util/PasswordPolicy`); doble factor TOTP (`util/Totp`, `DobleFactorService`).
- Sesiones guardadas en MySQL (tablas `SPRING_SESSION*`), sobreviven a reinicios del servidor.

---

## Estructura

```
db/skillbridge_db.sql      script único de la base de datos
src/main/java/com/skillbridge/ai/
  config/       SecurityConfig, WebConfig, DataSeeder e inicializadores de esquema
  controller/   controladores MVC (uno por pantalla o grupo de pantallas)
  dto/          objetos que se pasan a las plantillas
  interceptor/  SesionInterceptor
  model/        entidades JPA (mapeo 1:1 con db/skillbridge_db.sql)
  repository/   Spring Data JPA
  security/     autenticación, handlers de login/acceso denegado, filtro de sesión
  service/      lógica de negocio
  util/         roles, política de contraseñas, TOTP, excepciones
src/main/resources/
  application.properties
  secretos.properties.example
  templates/    vistas Thymeleaf por rol + fragments/ compartidos
  static/       css/, js/, img/, fonts/ (todo autohospedado, sin CDN)
```

### Inicializadores de esquema

Con `spring.jpa.hibernate.ddl-auto=none`, Hibernate no toca la BD. Para que una base creada con una versión anterior del script siga funcionando, estas clases de `config/` agregan al arrancar **solo lo que falte** (no hacen nada si ya existe):

| Clase | Qué agrega |
|---|---|
| `Inicializador2fa` | tabla `usuarios_2fa` |
| `InicializadorEsquemaV7` | columnas de pausa de proyectos, `rol_asignado`, `origen` |
| `InicializadorEsquemaV9` | enlaces y datos del perfil profesional, `proyectos.github_url` |
| `InicializadorEsquemaV10` | columnas del resumen IA de foros |
| `InicializadorRazonCancelacion` | `proyectos.razon_cancelacion` |
| `InicializadorTablasExtra` | tablas `archivos_proyecto`, `cursos` y `curso_pasos`, columnas del catálogo de formación y, la primera vez, un catálogo de ejemplo con recursos gratuitos |
| `InicializadorTopicosForo` | asigna tópico a los hilos que no tengan |
| `DataSeeder` | tipos de notificación `asignacion` y `foro_respuesta` |

**Si cambias el esquema:** agrega el cambio en `db/skillbridge_db.sql` (para instalaciones nuevas) **y** un inicializador idempotente (para las bases que ya existen). No vuelvas a crear scripts de migración sueltos.

---

## Despliegue (EC2)

```bash
export DB_PASSWORD='...'
export MAIL_PASSWORD='...'
export GEMINI_API_KEY='...'
nohup java -jar skillbridge-ai.jar > app.log 2>&1 &
```

`app.base-url` en `application.properties` debe apuntar a la URL pública (se usa para los enlaces de los correos). En producción conviene `spring.thymeleaf.cache=true` y, con HTTPS, `server.servlet.session.cookie.secure=true`.
