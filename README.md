# SkillBridge AI

Plataforma de gestión de talento y proyectos con IA: usuarios y roles, catálogo de habilidades, proyectos, asignaciones con AI Talent Matching, entregables, foros, calendario, reportes y un Asistente IA (agente) conectado a Claude (Anthropic Messages API).

**Stack:** Java 17 · Spring Boot 3.2.5 · Spring MVC + Thymeleaf · Spring Data JPA (Hibernate) · MySQL 8 · Spring Security · Spring Session JDBC · Java Mail (Gmail SMTP) · Apache POI / OpenPDF (exportes) · API de Claude (Anthropic).

---

## Puesta en marcha

### 1. Base de datos

Con MySQL 8 corriendo:

```bash
mysql --default-character-set=utf8mb4 --init-command="SET time_zone='-05:00'" -u root -p < db/skillbridge_db.sql
```

`--init-command` carga los datos de demo en hora de Perú (los eventos y fechas de ejemplo se calculan con `NOW()`). En Workbench, ejecuta antes `SET time_zone = '-05:00';` en la misma pestaña.

> Si usas la consola de MySQL, cárgalo siempre en UTF-8 (`mysql --default-character-set=utf8mb4 -u root -p < db/skillbridge_db.sql`); en Workbench basta con abrirlo y ejecutar. Si alguna vez quedaron nombres como "IvÃ¡n" en vez de "Iván" (datos cargados con scripts antiguos), la app los repara sola al arrancar (`config/ReparadorTildes`).

`db/skillbridge_db.sql` es el **único script** del proyecto: crea el esquema `skillbridge_db` con todas las tablas, la siembra base (administrador, catálogos, configuración) y los datos de demo. Si el esquema ya existe, el script falla a propósito para no borrar datos; para empezar de cero, descomenta la línea `DROP SCHEMA IF EXISTS skillbridge_db;` del inicio.

Si ya tienes una base creada con una versión anterior, **no hace falta recrearla**: al arrancar, la app agrega sola lo que le falte (ver *Inicializadores* más abajo).

### 2. Secretos

Copia `src/main/resources/secretos.properties.example` como `src/main/resources/secretos.properties` (está en `.gitignore`, nunca se sube) y completa:

| Variable | Para qué | Si no se define |
|---|---|---|
| `DB_USERNAME` | Usuario de MySQL | `root` |
| `DB_PASSWORD` | Clave de MySQL | vacía |
| `MAIL_PASSWORD` | Contraseña de aplicación de Gmail (correos de invitación, recuperación y notificaciones) | la app arranca, pero no se envían correos |
| `ANTHROPIC_API_KEY` | Asistente IA (agente), resúmenes de foros y chat, matching y buscador con IA ([consola de Anthropic](https://console.anthropic.com/)) | el asistente responde que no está configurado |
| `CLAUDE_MODELOS` | Opcional: modelos de Claude a probar en orden, separados por coma | `claude-sonnet-5` |
| `AGENTE_ENABLED` / `AGENTE_SOLICITUDES_MINUTO` | Opcional: activar el agente y su límite por minuto | `true` / `8` |
| `IA_RESPALDO_API_KEY` | Opcional: segundo proveedor de IA si Claude falla (cualquier API compatible con OpenAI; por defecto Groq, [clave gratis](https://console.groq.com/keys)) | solo se usa Claude |

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

Todas usan la contraseña **`admin123`**, solo para desarrollo local. Con el perfil `prod` esa contraseña queda bloqueada al iniciar sesión (`app.seguridad.bloquear-clave-demo=true`): si el script de ejemplo se cargó en el servidor, cada cuenta debe crear una contraseña nueva con «¿Olvidaste tu contraseña?».

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
| **Administrador** (`/administrador/**`) | Inicio, Usuarios y roles, Catálogo de habilidades, Cursos, Catálogos técnicos, Proyectos, Mensajes, Reportes globales, Auditoría, Configuración |
| **Resource Manager** (`/resource-manager/**`) | Inicio, Asignaciones, Colaboradores (capacidad del equipo, Ajustar carga y ficha de detalle), Mensajes, AI Talent Matching, Calendario (agenda personal), Cursos, Reportes |
| **Project Manager** (`/project-manager/**`) | Inicio, Proyectos (con archivos de contexto), Asignaciones, Calendario, AI Talent Matching, Foros, Chat, Cursos, Asistente IA, Reportes |
| **Colaborador** (`/colaborador/**`) | Inicio, Mi perfil (habilidades y certificados), Mis proyectos, Entregables, Calendario, Foros, Chat, Cursos, Asistente IA, Notificaciones |
| Todos (menú del usuario, arriba a la derecha) | Mi cuenta (datos, foto, contraseña, doble factor TOTP), Notificaciones, buscador global (`Ctrl+K`) |

### Asistente IA (chatbot con contexto y memoria)

Una sola pantalla para los 4 roles (`compartido/asistente-ia.html`, `AsistenteIaController`), con jQuery/AJAX contra el REST `/api/asistente/**`:

- **Contexto real** (`AsistenteContextoService`): en cada mensaje se leen de la base los datos de quien pregunta según su vista. El Colaborador ve su perfil, habilidades, proyectos, carga y entregables; el PM, sus proyectos con equipo, vacantes, huecos y entregables; el RM, la capacidad del equipo, la banca, las solicitudes y las vacantes, y si pregunta "¿a quién pongo en Portal Andes?" se agrega el ranking de AI Talent Matching; el Administrador, cifras globales. El panel "Contexto cargado" muestra esos mismos datos.
- **Historial** (`AsistenteConversacionService`): cada turno se guarda en `asistente_conversaciones` / `asistente_mensajes` y el panel "Historial" permite retomar, renombrar, fijar o eliminar conversaciones. Por seguridad, el agente recibe solo el mensaje actual y consulta los datos vigentes con sus herramientas; el historial se oculta si cambian los permisos de la persona.
- Las respuestas pueden traer enlaces de acción (por ejemplo "Ver Portal Andes" o "Buscar talento para…").

Detalles del agente (herramientas por rol, confirmación de escrituras, límites y auditoría): ver [`ASISTENTE_IA.md`](ASISTENTE_IA.md) y [`Documentacion_IA/`](Documentacion_IA/).

### Colaboradores del Resource Manager (directorio + capacidad)

`resource-manager/colaboradores.html` reúne lo que antes eran dos pantallas (la antigua "Ocupación del equipo" redirige aquí):

- **Capacidad:** tarjetas con cuántas personas disponibles hay en la banca, subutilizadas, en rango óptimo, al límite o sobreasignadas (contra el límite de carga de Configuración). Al pulsar una, la tabla muestra solo a esas personas, combinado con el buscador.
- **Directorio:** foto, cargo, dos habilidades principales (el resto en "+N"), barra de carga y estado, con filtros por habilidad y estado.
- **Ajustar carga / Rebalancear:** desde la barra de carga se abren las asignaciones activas de la persona para cambiar la dedicación o finalizar una asignación (queda en Auditoría).
- **Ver perfil** y marcar disponible o no disponible, como antes.

### Calendario del Resource Manager

`resource-manager/calendario.html` usa el mismo calendario que PM y Colaborador. Muestra la **agenda personal** del RM (tabla `eventos_personales`: solo él la ve, crea, edita y elimina) y, solo para consulta, los **hitos y entregables** de los proyectos en curso. El Asistente IA puede consultar esa agenda y **preparar eventos** (`crear_evento_agenda`), que se guardan solo cuando el RM pulsa *Confirmar cambio*.

### Mensajes directos

Además de las salas de proyecto, el chat tiene **mensajes directos** entre dos personas (tablas `chat_salas` con `tipo = 'directo'` y `chat_participantes`), que solo ven ellas dos. El Resource Manager y el Administrador pueden escribirle a cualquier cuenta activa (menú **Mensajes**, o **Enviar mensaje** en el perfil de un colaborador); un Colaborador o PM puede escribirle a un Resource Manager con **Nuevo mensaje** y siempre puede responder. Entre Miembros se usan los chats y foros de proyecto. El primer mensaje sin leer avisa con una notificación (y correo).

### Chat de proyectos (RF07)

Cada proyecto tiene una sala de chat que solo ven sus integrantes activos (PM y colaboradores). Se guarda el historial con autor, fecha y hora (`chat_salas`, `chat_mensajes`) y hasta dónde leyó cada persona (`chat_lecturas`, contador de no leídos en el sidebar).

Arquitectura: servicio web **REST** (`ChatApiController`, `/api/chat/**`, JSON) consumido con **jQuery/AJAX** (`js/chat.js` sobre `js/sbai-api.js`, que agrega el token CSRF). Los mensajes nuevos llegan por *polling*: cada 3 s el navegador pide `GET /api/chat/salas/{id}/mensajes?despues={ultimoId}` (15 s si la pestaña está oculta), sin necesidad de WebSockets. Incluye "Resumir con IA" (Claude) de la conversación reciente. La primera vez se cargan conversaciones de ejemplo en los proyectos activos.

### Vista de detalle de proyecto

`/project-manager/proyectos/{id}.html` (solo el PM que lo lidera) y `/resource-manager/proyectos/{id}.html` (cualquier proyecto), plantilla `compartido/proyecto-detalle.html` (`ProyectoVistaController`, `ProyectoVistaService`). Muestra estado, avance, plazo, stack, **cobertura de habilidades** (quién del equipo cubre cada habilidad requerida y dónde hay huecos), equipo con su carga total, entregables, chat/foro/archivos y **candidatos sugeridos** con el mismo puntaje de AI Talent Matching: el PM los propone al RM y el RM los asigna directo. Se llega desde el nombre del proyecto en Proyectos/Asignaciones/Inicio, desde "Encaja en …" del Inicio del RM y desde el buscador global.

### AI Talent Matching con IA (RF05)

Arquitectura **híbrida y explicable** (`MatchingIaService`, REST `MatchingApiController` en `/api/matching/**`, consumido con jQuery/AJAX desde `js/matching-ia.js`):

1. **Pregúntale a la IA** (pantalla AI Talent Matching del PM y del RM): una petición en lenguaje natural ("Necesito 2 backend con Spring Boot y Docker para Portal Andes al 50%") se convierte en filtros (proyecto, habilidades, cantidad y dedicación). Si la IA no responde, un intérprete por palabras clave hace lo mismo.
2. **Java calcula el ranking** con los datos reales (`TalentMatchingService`): la IA nunca inventa candidatos ni cambia el puntaje.
3. **La IA explica** cada recomendación y resume a quién priorizar. Si falla, se muestran las explicaciones calculadas por el sistema.
4. Cada recomendación queda en `recomendaciones_ia_log`; `fue_asignado` se marca cuando esa persona termina asignada al proyecto, y la pantalla muestra el **acierto de la IA**.

En la vista de detalle del proyecto, **"Analizar con IA"** hace los pasos 2 a 4 para el proyecto abierto. Proponer (PM) o asignar (RM) siguen siendo decisiones de una persona, con confirmación.

### Reportes del Resource Manager (dashboard + Excel + PDF)

`resource-manager/reportes.html` (`RmReportesController`, `RmReporteService`) reúne con los mismos filtros (búsqueda, estado de carga, cargo, proyecto, categoría de habilidad, ocupación mínima y solo brechas) lo que guarda la base:

- **Capacidad:** ocupación, banca (días sin proyecto), próximas liberaciones y riesgo de sobrecarga.
- **Proyectos:** equipo, vacantes, cobertura de habilidades, plazo y un nivel de riesgo con sus motivos.
- **Habilidades:** oferta contra demanda y brechas.
- **Decisiones:** propuestas (resultado, origen, tiempo de respuesta) y acierto de la IA.
- **Entregas:** cumplimiento a tiempo y calificación promedio.
- **Hallazgos clave:** conclusiones calculadas automáticamente.

Los gráficos (donas interactivas con tooltip y clic para filtrar, y barras con la línea del límite) se dibujan en SVG con `js/rm-reportes.js`, sin librerías. `RmReporteExportService` genera el **Excel** (portada con indicadores y hallazgos, 4 gráficos nativos editables, hojas de detalle con autofiltro, barras de datos y colores por estado) y el **PDF ejecutivo** (A4 horizontal, donas y barras dibujadas, tablas por sección y "Página N de M").

### Buscador inteligente (Ctrl+K)

El buscador del topbar sigue mostrando coincidencias al instante mientras escribes (`GET /cuenta/buscar`). Si lo escrito parece una pregunta ("¿quién sabe Docker y está libre?", "proyectos de nube atrasados", "gente en la banca"), aparece **Preguntar a la IA ↵**. Solo al elegirla se llama con jQuery/AJAX a `POST /cuenta/buscar-ia` (`BuscadorIaService`): la IA convierte la pregunta en filtros (personas o proyectos, habilidades, nivel, % libre, banca, estados, atrasados, vacantes) y Java busca en la base con el alcance de cada rol (el Colaborador no ve a otras personas). Si la IA no responde, un intérprete por palabras clave arma los filtros.

### Catálogo de formación (Cursos)

El Administrador publica en *Cursos* cuatro tipos de recurso: **cursos**, **videos**, **rutas** (roadmaps por pasos) y **especializaciones** (programas de varios cursos). Cada recurso tiene enlace, plataforma (se detecta sola por el enlace: YouTube, Coursera, Microsoft Learn, roadmap.sh, freeCodeCamp…, ver `util/PlataformaCurso`), categoría y habilidad, nivel, duración, idioma, si es gratuito, si da certificado y si es destacado. Las rutas y especializaciones guardan sus pasos en orden (`curso_pasos`).

Lo **publicado** lo ven Colaborador, PM y RM en su vista *Cursos* (`CursosCatalogoController`, plantilla `compartido/cursos.html`): búsqueda, filtros (tipo, categoría, habilidad, nivel, plataforma, idioma, gratis, certificado, "Para ti" según las habilidades declaradas y guardados), reproductor de YouTube integrado y progreso de cada ruta. Guardados y progreso se guardan en el navegador de cada persona. Todo lo pinta `js/cursos.js` (el mismo archivo maneja la vista del Administrador).

### Configuración global

La pantalla *Configuración* del Administrador edita `configuracion_global`. Claves que hoy controlan comportamiento real: `dominio_correo_permitido`, `expiracion_sesion_minutos`, `limite_carga_colaborador`, `maximo_proyectos_simultaneos` y los tres `peso_matching_*` (AI Talent Matching). Las demás se guardan pero todavía no tienen efecto.

### Valores calculados (no son columnas)

- **% de avance de proyecto**: heurístico por tiempo transcurrido entre `fecha_inicio` y `fecha_fin_estimada` (`ProyectoService.avanceHeuristico`); la fuente de verdad del trabajo entregado es el módulo de Entregables.
- **Demanda de una habilidad**: según cuántos proyectos la requieren.
- **Tipo / severidad / origen en Auditoría**: derivados del texto de la acción (`AuditoriaFila`).

### Correos

Los correos (Gmail SMTP) usan una plantilla HTML con el logo incrustado, se envían en segundo plano después de guardar cada cambio y algunos se pueden apagar en *Notificaciones → Preferencias de aviso*. Cada lunes a las 8:00 llega un **resumen semanal** según el rol (`ResumenSemanalService`, `@Scheduled`), que también se puede pedir al momento con "Enviarme el resumen ahora". El mapa completo de qué correo llega, a quién y cuándo (y qué casos no envían correo) está en [`Documentacion_Correos.md`](Documentacion_Correos.md).

### Servicios web REST

Todos los servicios REST (`/api/**`, buscador y catálogo de habilidades) están documentados en [`Documentacion_REST/`](Documentacion_REST/README.md) con ruta, método, parámetros, ejemplo de respuesta y códigos HTTP.

---

## Seguridad

- **Spring Security** (`config/SecurityConfig`): login por formulario, acceso por rol según la URL, CSRF en todos los POST, logout por POST, cabeceras de seguridad (CSP, frame-options, referrer-policy).
- **`security/SesionVigenteFilter`** revalida al usuario contra la BD en cada request: si lo suspenden o le cambian el rol, se refleja sin esperar a que cierre sesión.
- **`interceptor/SesionInterceptor`** fija la vista activa (rol efectivo) según el área visitada.
- Cada controlador verifica además que el recurso pertenezca al usuario o a su proyecto (p. ej. un PM solo gestiona sus proyectos; los archivos y entregas solo los descargan los integrantes del proyecto).
- Contraseñas con BCrypt y política de 12+ caracteres (`util/PasswordPolicy`); doble factor TOTP (`util/Totp`, `DobleFactorService`).
- Sesiones guardadas en MySQL (tablas `SPRING_SESSION*`), sobreviven a reinicios del servidor.

---

## Pruebas unitarias

JUnit 5 + Mockito + MockMvc (vienen con `spring-boot-starter-test`). No necesitan MySQL ni Gmail: los repositorios
y el envío de correo están simulados, así que corren en segundos.

```bash
mvn test
```

| Clase de prueba | Qué cubre |
|---|---|
| `util/TotpTest` | 2FA: vectores oficiales del RFC 6238, Base32, tolerancia de ±30 s, códigos inválidos y URI `otpauth://` |
| `util/PasswordPolicyTest` | Reglas de contraseña (12 caracteres, mayúscula, número, símbolo, sin espacios) |
| `service/PreferenciaNotificacionServiceTest` | Qué canales cuentan como "con correo", valores por defecto y guardado de preferencias |
| `service/EmailServiceTest` | Plantilla HTML (saludo, botón, escape de HTML, sin emojis), ícono correcto por categoría, mensaje MIME (logo e ícono inline, texto plano) y fallo de SMTP |
| `service/ProyectoServiceDedicacionTest` | "Ajustar carga": rangos, límite configurado, carga total, auditoría y aviso al colaborador |
| `controller/RmOcupacionControllerTest` | Servicio REST de Ajustar carga: respuestas 200/400/404 en JSON y el formulario clásico |

Las pruebas van en `src/test/java` con el mismo paquete que la clase que prueban.

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
| `InicializadorTablasExtra` | tablas `archivos_proyecto`, `cursos`, `curso_pasos`, `chat_lecturas`, `chat_participantes` (y las columnas de mensajes directos en `chat_salas`), `asistente_conversaciones` y `asistente_mensajes`, `eventos_personales` (agenda del RM), columnas del catálogo de formación y, la primera vez, un catálogo de cursos y conversaciones de chat de ejemplo |
| `InicializadorTopicosForo` | asigna tópico a los hilos que no tengan |
| `DataSeeder` | tipos de notificación `asignacion` y `foro_respuesta` |
| `ReparadorTildes` | corrige textos con tildes guardadas dos veces ("IvÃ¡n" → "Iván"); si no está seguro, no toca el valor |

**Si cambias el esquema:** agrega el cambio en `db/skillbridge_db.sql` (para instalaciones nuevas) **y** un inicializador idempotente (para las bases que ya existen). No vuelvas a crear scripts de migración sueltos.

---

## Despliegue (EC2)

```bash
export DB_PASSWORD='...'
export MAIL_PASSWORD='...'
export ANTHROPIC_API_KEY='...'
nohup java -jar skillbridge-ai.jar > app.log 2>&1 &
```

**Zona horaria:** la app trabaja siempre en hora de Perú (`America/Lima`, UTC-5), aunque el EC2 o RDS estén en UTC. La fija `SkillbridgeAiApplication` para Java y `spring.datasource.hikari.connection-init-sql` para MySQL (`NOW()` y las columnas con `DEFAULT CURRENT_TIMESTAMP`). Si usas el perfil `prod`, incluye `serverTimezone=America/Lima` en `DB_URL`, igual que en `application.properties`. En RDS puedes además poner `time_zone = America/Lima` en el grupo de parámetros, para que las consultas manuales también muestren hora de Perú.

`app.base-url` en `application.properties` apunta a la IP elástica de EC2 (`http://13.216.85.235:8080`; se cambia con la variable `APP_BASE_URL`) y debe ser la URL pública (se usa para los enlaces de los correos). En producción conviene `spring.thymeleaf.cache=true` y, con HTTPS, `server.servlet.session.cookie.secure=true`.
