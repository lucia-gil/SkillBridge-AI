# SkillBridge AI — Backend Spring Boot

Backend Spring Boot 3.2.5 (Java 17, empaquetado WAR) para el frontend Thymeleaf de SkillBridge AI, conectado a la base de datos MySQL de `db/skillbridge_db_v4.sql`.

## ⚠️ Importante: este código NO fue compilado en el entorno donde se generó

El entorno de trabajo usado para escribir este proyecto no tenía acceso a Maven Central (todas las peticiones a `repo.maven.apache.org` y mirrors devolvían HTTP 403 por la política de red del sandbox), así que **no fue posible correr `mvn compile` ni `mvn package` para verificarlo**. Todo el código fue revisado manualmente línea por línea (imports, firmas de métodos, mapeos JPA, convenciones de Spring Data, sintaxis Thymeleaf, binding de Jackson), pero la única verificación real es la que tú vas a hacer al compilarlo en tu máquina, que sí tiene internet. Si `mvn clean package` marca algún error, dime el mensaje exacto y lo corrijo.

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

**Con persistencia real contra MySQL (los 2 CRUD pedidos + autenticación):**
- **Login / registro / logout** (`/auth/login.html`, `/auth/registro.html`, `/auth/logout`) — adaptado funcionalmente de `LoginServlet`/`RegistroServlet`/`PasswordPolicy` de QuintaOla-SGA: hash BCrypt, límite de 5 intentos fallidos por IP cada 10 min, mensajes de error genéricos (no revelan si el correo existe), registro solo permitido para correos pre-autorizados en `correos_autorizados`, invalidación + regeneración de sesión al iniciar sesión (previene session fixation), auditoría de cada intento en `auditoria_logs`.
- **Usuarios y roles** (`/administrador/usuarios.html`) — listar, invitar (autoriza el correo para autoregistro), cambiar rol organizacional, suspender/reactivar, eliminar. Todo contra las tablas `usuarios`, `correos_autorizados`, `perfiles`.
- **Catálogo de habilidades** (`/administrador/habilidades.html`) — listar (con conteos reales de personas/proyectos y nivel promedio), crear, editar, eliminar. Contra `habilidades`, `categorias_habilidad`, `perfil_habilidad`, `proyecto_habilidad_requerida`.

**El resto del frontend** (unas 36 páginas entre los 4 roles: inicio, proyectos, reportes, foros, asistente IA, etc.) ahora se sirve correctamente vía Spring MVC/Thymeleaf — con las rutas de CSS/JS/imágenes corregidas y el shell (sidebar/topbar) funcionando — pero sigue usando los datos simulados de `mock-data.js` que ya traía el proyecto. No se le agregó persistencia real porque no fue parte del alcance pedido (2 CRUD + login/registro).

## Supuestos declarados

- **"Demanda"** en el catálogo de habilidades no es una columna del esquema: se calcula como una heurística simple a partir de cuántos proyectos requieren cada habilidad (≥3 = Alta, ≥1 = Media, 0 = Baja). Está documentado en el código (`HabilidadFila`) para que no se confunda con un dato cargado a mano.
- **"Cuenta desde"** en la tabla de usuarios usa `usuarios.fecha_creacion` (el esquema no tiene "último acceso").
- El mapeo de nivel textual del wizard de registro (Básico/Intermedio/Avanzado/Experto) a la escala numérica 1-5 de `perfil_habilidad.nivel` es una convención mía (1/3/4/5) porque el documento no fija la correspondencia exacta.
- El **rol organizacional fijo** (`usuarios.rol_organizacional`) solo admite `administrador` / `resource_manager` / `NULL` en el schema. El "rol efectivo" que decide qué sidebar ve cada quien (que además distingue Project Manager y Colaborador) se calcula en tiempo real revisando si el perfil tiene una asignación activa como `project_manager` en la tabla `asignaciones` — no es una columna nueva, es una función de negocio (`AuthService.calcularRolEfectivo`).
- Un usuario no puede cambiarse el rol, suspenderse ni eliminarse a sí mismo (guardas explícitas en `UsuarioService`).
- El selector de "aprobación de manager" en el paso 3 del wizard de registro es decorativo (viene del mockup original); el registro real solo depende del correo pre-autorizado.
- Corregí un bug del mockup original: el wizard de registro dejaba avanzar de paso con solo 2 de los 4 requisitos de contraseña cumplidos, aunque mostraba los 4 como obligatorios y el servidor los exige todos — ahora el frontend exige los 4 antes de avanzar, igual que `PasswordPolicy` en el backend.

## Estructura

```
src/main/java/com/skillbridge/ai/
  model/        entidades JPA (mapeo 1:1 con skillbridge_db_v4.sql)
  repository/   Spring Data JPA
  service/      lógica de negocio (auth, usuarios, habilidades, auditoría)
  web/          controladores MVC
  dto/          objetos de transferencia hacia las plantillas
  util/         constantes de roles, política de contraseñas, excepciones
  config/       interceptor de sesión por rol
```
