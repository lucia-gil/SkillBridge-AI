# SkillBridge AI — backend (Entregable 2, v2)

Proyecto Spring Boot 3.3 / Java 21 con **las 19 entidades JPA del modelo
completo** (una por tabla de `schema.sql`, en la raíz del entregable),
reconstruido para reflejar el documento de diseño de base de datos revisado
con el jefe de práctica (separación `usuarios`/`perfiles`, rol organizacional
vs. rol por proyecto, chat, foro autorreferencial, embeddings, etc. — ver
`Entregable2-Informe.md` para el detalle de cada decisión).

> **Nota sobre esta plantilla:** este scaffold se generó desde cero para este
> entregable porque no fue posible acceder a la plantilla que ya tienen en su
> equipo (estaba en un computador sin la carpeta compartida en esta sesión).
> Si su plantilla ya trae paquetes, `pom.xml` o convenciones propias, hay que
> fusionar esto con eso — a nivel de negocio no debería sobrar nada: las
> entidades siguen exactamente las tablas de `schema.sql`.

## Qué incluye

- **19 entidades JPA + 19 repositorios Spring Data**, una por tabla:
  `Usuario`, `CorreoAutorizado`, `Perfil`, `Habilidad`, `PerfilHabilidad`,
  `Proyecto`, `ProyectoHabilidadRequerida`, `Asignacion`, `ExcepcionCarga`,
  `ForoPublicacion`, `ResumenIa`, `ChatSala`, `ChatMensaje`, `Notificacion`,
  `PreferenciaNotificacion`, `AuditoriaLog`, `ConfiguracionGlobal`,
  `ConocimientoEmbedding`, `RecomendacionIaLog`. Cada campo se contrastó a
  mano contra `schema.sql` (nombre de columna, FKs, valores de enum) — ver
  el informe, sección de verificación.
- **`ProyectoService.crearProyecto(...)`**: implementa en una sola
  transacción el patrón "crear proyecto + crear su fila de asignación como
  project_manager" que el documento de diseño exige explícitamente (si no,
  queda un proyecto con `creado_por_id` pero sin nadie formalmente asignado
  como PM).
- **`RegistroService.registrar(...)`**: implementa el flujo de lista blanca
  — valida que el correo esté en `correos_autorizados` con `utilizado=false`,
  crea `usuarios`+`perfiles`, hashea la contraseña con BCrypt, y marca el
  correo como usado, todo en una transacción.
- Conversores JPA (`StringListJsonConverter`, `DoubleListJsonConverter`)
  para las columnas `JSON` (`proyectos.tecnologias`,
  `foro_publicaciones.etiquetas`, `conocimiento_embeddings.vector_embedding`)
  — se leen/escriben como `List<String>`/`List<Double>` normales en vez de
  manipular JSON como texto en el código de negocio.
- `GET /api/estado`: consulta conteos reales (usuarios, perfiles,
  habilidades, proyectos, parámetros de configuración) para comprobar que la
  app está leyendo de la base de datos desplegada, no de datos en memoria.
- `GET /actuator/health`: expone el estado de la conexión JDBC
  (`components.db.status = UP` si Cloud SQL responde).
- `SecurityConfig` temporal con todo abierto (permitAll) — RF01 (sesión,
  roles, CSRF) se implementa en el sprint del tema "Sesión, security" del
  curso; no desplegar esta versión de `SecurityConfig` más allá de este
  entregable.
- `Dockerfile` multi-stage (Maven + JDK 21 para build, JRE 21 Alpine para
  runtime).

## 1. Probar en local

Necesitas un MySQL 8 corriendo (local o el Cloud SQL de abajo a través del
Auth Proxy).

```bash
# 1. Crear la base y cargar el esquema
mysql -u root -p -e "CREATE DATABASE skillbridge CHARACTER SET utf8mb4;"
mysql -u root -p skillbridge < ../schema.sql

# 2. Levantar la app apuntando a esa base
export DB_HOST=localhost DB_PORT=3306 DB_NAME=skillbridge DB_USER=root DB_PASSWORD=tu_password
mvn spring-boot:run
```

Verificar:

```bash
curl http://localhost:8080/api/estado
curl http://localhost:8080/actuator/health
```

## 2. Despliegue en GCP (Cloud SQL + Cloud Run)

Asume que ya tienen un proyecto de GCP con los créditos asignados y
`gcloud` autenticado (`gcloud auth login`, `gcloud config set project TU_PROYECTO`).

### 2.1 Crear la instancia de Cloud SQL (MySQL)

```bash
gcloud sql instances create skillbridge-db \
  --database-version=MYSQL_8_0 \
  --tier=db-f1-micro \
  --region=us-central1 \
  --storage-size=10GB \
  --storage-type=HDD

# Password del usuario root de la instancia
gcloud sql users set-password root --host=% \
  --instance=skillbridge-db --password=UNA_PASSWORD_SEGURA

# Crear la base de datos
gcloud sql databases create skillbridge --instance=skillbridge-db
```

Anota el `connectionName` (formato `PROYECTO:REGION:skillbridge-db`):

```bash
gcloud sql instances describe skillbridge-db --format='value(connectionName)'
```

### 2.2 Cargar el esquema en Cloud SQL

Opción rápida con el Cloud SQL Auth Proxy corriendo en tu máquina:

```bash
# Descarga el proxy: https://cloud.google.com/sql/docs/mysql/sql-proxy
./cloud-sql-proxy PROYECTO:REGION:skillbridge-db &
mysql -h 127.0.0.1 -u root -p skillbridge < ../schema.sql
```

### 2.3 Construir y publicar la imagen

```bash
gcloud artifacts repositories create skillbridge-repo \
  --repository-format=docker --location=us-central1

gcloud builds submit --tag \
  us-central1-docker.pkg.dev/TU_PROYECTO/skillbridge-repo/skillbridge-ai:entregable2
```

### 2.4 Desplegar en Cloud Run

```bash
gcloud run deploy skillbridge-ai \
  --image us-central1-docker.pkg.dev/TU_PROYECTO/skillbridge-repo/skillbridge-ai:entregable2 \
  --region us-central1 \
  --allow-unauthenticated \
  --add-cloudsql-instances PROYECTO:REGION:skillbridge-db \
  --set-env-vars SPRING_PROFILES_ACTIVE=gcp,DB_NAME=skillbridge,INSTANCE_CONNECTION_NAME=PROYECTO:REGION:skillbridge-db \
  --set-secrets DB_USER=db-user:latest,DB_PASSWORD=db-password:latest
```

(`db-user` y `db-password` como Secret Manager es lo recomendado; para ir
más rápido en este entregable también se puede pasar
`--set-env-vars DB_USER=root,DB_PASSWORD=...` directo, pero no lo dejen así
para producción.)

### 2.5 Verificar el despliegue

```bash
SERVICE_URL=$(gcloud run services describe skillbridge-ai --region us-central1 --format='value(status.url)')
curl $SERVICE_URL/api/estado
curl $SERVICE_URL/actuator/health
```

Si `api/estado` devuelve los conteos y `actuator/health` dice `"status":"UP"`
con `"db":{"status":"UP"}`, el Entregable 2 (Base de Datos + Spring en Cloud)
está demostrablemente cumplido.

### 2.6 No quemar los créditos

Con 50 USD de créditos GCP para 4 meses (ver
`claude/analisis-proyecto-skillbridge-ai.md`, sección "Presupuesto y
despliegue"):

```bash
# Alerta de presupuesto (ajusta el ID de facturación)
gcloud billing budgets create \
  --billing-account=TU_BILLING_ACCOUNT_ID \
  --display-name="SkillBridge AI - alerta 80%" \
  --budget-amount=50 \
  --threshold-rule=percent=0.8

# Cloud Run ya escala a cero solo; para Cloud SQL, si no la vas a usar por
# unos días, para la instancia (no la borres, solo detenla):
gcloud sql instances patch skillbridge-db --activation-policy=NEVER
# y para reactivarla:
gcloud sql instances patch skillbridge-db --activation-policy=ALWAYS
```
