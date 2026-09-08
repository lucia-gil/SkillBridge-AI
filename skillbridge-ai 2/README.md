# SkillBridge AI — Backend Spring Boot

Backend Spring Boot 3.2.5 (Java 17, empaquetado WAR) para el frontend Thymeleaf de SkillBridge AI, conectado a la base de datos MySQL de `db/skillbridge_db_v4.sql`.

## ⚠️ Importante: este código NO fue compilado en el entorno donde se generó

El entorno de trabajo usado para escribir este proyecto no tenía acceso a Maven Central (todas las peticiones a `repo.maven.apache.org` y mirrors devolvían HTTP 403 por la política de red del sandbox — se volvió a intentar `mvn compile` en la ronda de esta ampliación y sigue devolviendo 403), así que **no fue posible correr `mvn compile` ni `mvn package` para verificarlo**. Todo el código (el de la primera entrega y el de esta ampliación) fue revisado manualmente línea por línea (imports, firmas de métodos, mapeos JPA, convenciones de Spring Data, sintaxis Thymeleaf, binding de Jackson, choque de rutas `@GetMapping` entre controladores), pero la única verificación real es la que tú vas a hacer al compilarlo en tu máquina, que sí tiene internet. Si `mvn clean package` marca algún error, dime el mensaje exacto y lo corrijo.

Un hallazgo concreto de esa revisión manual, ya corregido: al conectar cada página nueva a su propio controlador, `SitioController` se quedó mapeando las mismas rutas que esos controladores nuevos (p.ej. `/administrador/inicio.html` en dos lados) - eso hace que Spring falle al arrancar con "Ambiguous mapping". Se corrigió retirando de `SitioController` toda ruta que ya tiene un controlador real (ver sección "Qué es real" abajo). También se encontró que `/cuenta/**` y `/notificaciones/**` (compartidas por los 4 roles) habían quedado fuera de los patrones que protege `SesionInterceptor`, así que un POST directo a esas rutas sin sesión iniciada no redirigía a login sino que rompía con `NullPointerException`; se agregaron esos dos prefijos al interceptor (`WebConfig`) con una regla nueva que exige sesión activa de cualquiera de los 4 roles sin el redireccionamiento por rol específico que sí aplica al resto.

## Puesta en marcha

1. **Crear la base de datos** (MySQL 8, con el servidor corriendo localmente):
   ```bash
   mysql -u root -p < db/skillbridge_db_v4.sql
   ```
   Esto crea el esquema `skillbridge_db` con todas las tablas y los datos semilla (incluido el usuario administrador).

2. **Ajustar la conexión** en `src/main/resources/application.properties` si tu MySQL no usa `localhost:3306` / usuario `root` / clave vacía:
   ```properties
   spring.datasource.url=jdbc:mysql://localhost:3306/skillbridge_db?...
   spring.datasource.username=root
   spring.datasource.password=
   ```

3. **Compilar y correr:**
   ```bash
   mvn clean package
   mvn spring-boot:run
   ```
   o ejecutar el WAR generado en `target/skillbridge-ai.war` en un Tomcat externo.

4. Abrir `http://localhost:8080/` — redirige automáticamente a `/auth/login.html`.

## Credenciales de prueba (usuario semilla del propio `skillbridge_db_v4.sql`)

- **Correo:** `admin@nexacorp.com`
- **Contraseña:** `admin123`
- Verifiqué que este hash BCrypt del seed corresponde efectivamente a `admin123` corriendo `bcrypt.checkpw` antes de entregar esto — no es una suposición.

## Qué es real y qué sigue siendo mock

Esta es la segunda entrega sobre este proyecto. La primera hizo 2 CRUD (Usuarios y roles, Catálogo de habilidades) + login/registro/logout. Esta ampliación, a pedido explícito, **rediseña y conecta a datos reales los roles Administrador y Colaborador completos, sin el chatbot de Asistente IA** (excluido explícitamente del alcance); Resource Manager y Project Manager quedan fuera de esta ronda y siguen siendo la demo original.

**Administrador — 100% real contra MySQL, salvo el Asistente IA:**
- **Inicio** (`inicio.html`) — KPIs reales (usuarios, proyectos activos, habilidades del catálogo, asignaciones activas), "Proyectos por estado", "Requiere tu atención" (usuarios sin rol, habilidades sin ningún proyecto que las pida, colaboradores sobrecargados), vista previa de usuarios y de las 5 habilidades más declaradas.
- **Usuarios y roles**, **Catálogo de habilidades** — igual que la primera entrega (CRUD completo, sin cambios de fondo).
- **Proyectos** (`proyectos.html`) — listar con filtro por estado, crear (con Project Manager obligatorio, registrado vía `asignaciones` porque el esquema v4 no tiene `proyectos.creado_por_id`), ver detalle (modal con equipo activo), asignar colaborador o PM, finalizar una asignación, cambiar estado.
- **Reportes globales** (`reportes.html`) — ocupación por colaborador, habilidades más declaradas, proyectos por estado; todo con datos agregados reales de `asignaciones`/`perfil_habilidad`/`proyectos`.
- **Auditoría** (`auditoria.html`) — lectura de `auditoria_logs`, con tipo/severidad/origen derivados (ver "Supuestos").
- **Configuración** (`configuracion.html`) — lee y escribe `configuracion_global`; una parte de las 13 claves ya controla comportamiento real, el resto queda declarada pero sin efecto todavía (ver "Supuestos" para el detalle exacto de cuáles).
- **Mi cuenta**, **Notificaciones** — compartidas con Colaborador (mismo controlador, ver abajo).
- **Asistente IA** (`asistente-ia.html`) — sigue siendo la demo estática original (`mock-data.js`), sin conexión a un LLM: excluido explícitamente del alcance de esta entrega.

**Colaborador — 100% real contra MySQL, salvo el Asistente IA:**
- **Inicio** (`inicio.html`) — KPIs reales (proyectos activos, carga activa, habilidades declaradas/validadas), lista de proyectos actuales con avance y dedicación, actividad reciente en foros, notificaciones recientes.
- **Mi perfil** (`perfil.html`) — habilidades declaradas (agregar nuevas desde el catálogo, con nivel), historial completo de proyectos, carga de trabajo desglosada por proyecto activo, datos de colaborador (solo los campos que existen de verdad en `perfiles`, ver "Supuestos").
- **Mis proyectos** (`proyectos.html`) — proyectos actuales (tarjetas) e historial completo (tabla), con un modal de detalle de solo lectura por proyecto (descripción, tecnologías, fechas, avance, equipo).
- **Foros** (`foros.html`, `foro-hilo.html`) — listar hilos de tus proyectos, filtrar por proyecto, ordenar por recientes/más respondidos/sin responder, publicar un hilo nuevo, responder, marcar una respuesta como solución (solo quien abrió el hilo o un Administrador). Ver "Supuestos" sobre qué partes del foro del mockup no se reconstruyeron.
- **Mi cuenta**, **Notificaciones** — compartidas con Administrador: mismo `CuentaController`/`CuentaService` (editar cargo/biografía/años de experiencia, cambiar contraseña) y mismo `NotificacionesController`/`NotificacionService` (marcar leída/todas, preferencias de aviso por tipo de evento).
- **Asistente IA** (`asistente-ia.html`) — igual que en Administrador: demo estática, fuera de alcance.

**Resource Manager y Project Manager — sin cambios, 100% mock** (`mock-data.js`), incluyendo su AI Talent Matching y su Asistente IA: no se tocó nada de estos dos roles en esta entrega, quedan exactamente como en la entrega anterior, servidos como passthrough por `SitioController`.

## Supuestos y simplificaciones declaradas

**De la primera entrega (sin cambios):**
- **"Demanda"** en el catálogo de habilidades no es una columna del esquema: se calcula como una heurística simple a partir de cuántos proyectos requieren cada habilidad (≥3 = Alta, ≥1 = Media, 0 = Baja). Documentado en el código (`HabilidadFila`).
- **"Cuenta desde"** usa `usuarios.fecha_creacion` (no hay "último acceso" en el esquema).
- El mapeo de nivel textual (Básico/Intermedio/Avanzado/Experto) a la escala numérica 1-5 de `perfil_habilidad.nivel` es una convención propia (1/3/4/5): el documento no fija la correspondencia exacta. Se repite igual en `HabilidadService.agregarAlPerfil` (Mi perfil de Colaborador) para no introducir una segunda escala.
- El **rol organizacional fijo** (`usuarios.rol_organizacional`) solo admite `administrador` / `resource_manager` / `NULL`. El "rol efectivo" (que además distingue Project Manager y Colaborador) se calcula en tiempo real según si el perfil tiene una asignación activa como `project_manager` (`AuthService.calcularRolEfectivo`), no es una columna nueva.
- Un usuario no puede cambiarse el rol, suspenderse ni eliminarse a sí mismo.
- El selector de "aprobación de manager" del wizard de registro es decorativo (viene del mockup); el registro real solo depende del correo pre-autorizado.
- Se corrigió un bug del mockup original en el wizard de registro (dejaba avanzar con solo 2 de los 4 requisitos de contraseña cumplidos); ahora exige los 4, igual que `PasswordPolicy` en el backend.

**De esta ampliación (Administrador y Colaborador completos):**
- **Avance de proyecto (`avance`/"% Avance")** no es una columna del esquema (no hay tabla de tareas/tickets con progreso real). Se calcula como una heurística por tiempo transcurrido entre `fecha_inicio` y `fecha_fin_estimada` (`ProyectoService.avanceHeuristico`) — 100% si está `completado`, 0% si `cancelado`, y proporcional al tiempo transcurrido en los demás casos. Se documenta en la propia UI ("Avance (heurístico por tiempo transcurrido)") para no aparentar que mide trabajo completado.
- **Auditoría** — el esquema `auditoria_logs` no tiene columnas de "tipo"/"severidad"/"origen": `AuditoriaFila` las deriva del texto de la acción (p.ej. `*_ELIMINADA` o `*_SUSPENDIDO` se marcan como severidad alta) y de si el `usuario_id` es nulo (se etiqueta "Sistema"). Es una clasificación visual, no una fuente de verdad nueva.
- **Reportes/"Habilidades más declaradas"** reemplaza la idea de "vacantes por habilidad" del mockup original (que implicaría un módulo de postulaciones que no existe en el esquema v4) por un ranking real de cuántos colaboradores declararon cada habilidad.
- **Configuración (`configuracion_global`)** — de las 13 claves semilla, estas SÍ controlan comportamiento real:
  - `dominio_correo_permitido` → dominio exigido en el registro (`AuthService`).
  - `expiracion_sesion_minutos` → duración real de la sesión HTTP (`AuthController`).
  - `limite_carga_colaborador` → tope de dedicación (%) al asignar a un proyecto, sumando todas las asignaciones activas (`ProyectoService.asignarColaborador`).
  - `maximo_proyectos_simultaneos` → tope de proyectos activos simultáneos por colaborador (mismo método).
  Las 9 restantes (`peso_matching_*`, `resumenes_ia_foros_activos`, `asistente_ia_habilitado`, `doble_factor_obligatorio_admin`, `auto_aprobar_asignaciones_menores_a`, `idioma_por_defecto`, `zona_horaria_defecto`) se guardan y se pueden editar desde la pantalla, pero **no tienen ningún consumidor funcional todavía** — la mayoría corresponde a AI Talent Matching, al Asistente IA o a 2FA, todos fuera de alcance de esta entrega. No se fabricó un efecto falso para ellas.
- **Mi cuenta** deja fuera del mockup original: activar/desactivar 2FA, una segunda "sesión activa" fabricada, y el aviso por correo de nuevos inicios de sesión — ninguno tiene soporte real en el esquema. Sí quedaron reales: editar cargo/biografía/años de experiencia (`perfiles`) y cambiar la contraseña (BCrypt + `PasswordPolicy`, la misma del registro).
- **Foros** — el esquema v4 modela `foro_publicaciones` como una sola tabla autorreferencial donde cada post cuelga obligatoriamente de un proyecto; no existe un catálogo de "categorías" genéricas (Backend/Frontend/...) como mostraba el mockup. Se reinterpretó "Categorías" como **"tus proyectos"** (la única agrupación real disponible). El resumen de hilo generado por IA y el contador de votos "útil" del mockup NO se implementaron: el primero necesitaría un motor de IA (fuera de alcance) y el segundo no tiene columna en el esquema. "Marcar como solución" sí es real (columna `es_solucion`), limitado a quien abrió el hilo o a un Administrador.
- **Mi perfil (Colaborador) / "Datos de colaborador"** — el mockup mostraba ID de colaborador, Área, Ubicación, Ingreso y Manager fabricados: la tabla `perfiles` del esquema v4 no tiene esas columnas. El panel se redujo a los campos reales (cargo, disponibilidad, años de experiencia, correo, fecha de alta).
- **Inicio (Colaborador) / "Mis asignaciones de la semana"** y el mini-panel del Asistente IA del mockup se eliminaron por completo: no existe una tabla de tareas/tickets a nivel de sub-asignación en el esquema (solo asignaciones a nivel de proyecto completo), y el Asistente IA está fuera de alcance. Se reemplazaron por un desglose real de la carga activa por proyecto.
- **"Habilidades validadas"** (KPI de Colaborador/Inicio) usa la columna real `perfil_habilidad.validado_por_id`, pero como todavía no existe una pantalla para que alguien valide una habilidad declarada, este número siempre será 0 en la práctica hasta que se construya ese flujo — se deja el conteo real (no fabricado) a la espera de esa función futura.
- **DataSeeder** (`config/DataSeeder`, `ApplicationRunner` idempotente) agrega 2 filas a `tipos_notificacion` si no existen (`asignacion`, `foro_respuesta`): son los únicos 2 disparadores de notificación reales de esta entrega (asignar/finalizar una asignación de proyecto, y responder en un hilo que abrió otra persona).
- **IA / chatbot** — a pedido explícito del usuario, esta entrega NO implementa el Asistente IA de ningún rol ni ninguna forma de "AI Talent Matching" (esa función solo existe en las pantallas de Resource Manager/Project Manager, que quedaron fuera de alcance). No hay integración con ningún LLM en todo el proyecto.

## Estructura

```
src/main/java/com/skillbridge/ai/
  model/        entidades JPA (mapeo 1:1 con skillbridge_db_v4.sql)
  repository/   Spring Data JPA
  service/      lógica de negocio: auth, usuarios, habilidades, auditoría,
                proyectos, foros, notificaciones, preferencias de aviso,
                configuración global, cuenta, reportes, nav del sidebar,
                y ShellModelBuilder (arma el modelo común de todas las
                pantallas: sidebar, topbar, notificaciones del campanario)
  web/          controladores MVC (uno por pantalla o grupo de pantallas
                que comparten datos - ver Javadoc de cada uno)
  dto/          objetos de transferencia hacia las plantillas
  util/         constantes de roles, política de contraseñas, excepciones
  config/       WebConfig (registro del interceptor) + DataSeeder
  interceptor/  SesionInterceptor (control de acceso por sesión + rol)
```

Los controladores de Administrador/Colaborador de esta ampliación:
`AdminInicioController`, `AdminProyectosController`, `AdminReportesController`,
`AdminAuditoriaController`, `AdminConfiguracionController`,
`ColaboradorInicioController`, `ColaboradorPerfilController`,
`ColaboradorProyectosController`, `ForosController`, `ForoHiloController`,
y los dos compartidos entre ambos roles: `CuentaController` y
`NotificacionesController`. `SitioController` solo sirve ya lo que sigue
siendo mock: Resource Manager, Project Manager, y el Asistente IA de
Administrador/Colaborador.
