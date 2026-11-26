# SkillBridge AI: cambios a incluir en la documentación

Rama: `nube` del repositorio lucia-gil/SkillBridge-AI. Fecha de corte: 26/11/2026.
Este documento resume lo implementado y los datos que la documentación debe reflejar. Cada punto indica en qué sección de la documentación encaja.

---

## 1. Bridgie, el asistente de IA con acciones (agente)

**Sección sugerida:** Funcionalidades de IA → Asistente IA (Bridgie); Arquitectura → Módulo de IA; Seguridad.

### 1.1 Cómo funciona

- Bridgie usa la API de Claude con *tool use*. El modelo no accede a la base de datos: solo puede pedir herramientas que el backend define y valida (`HerramientasAgente`).
- Ciclo de hasta **3 rondas** por mensaje: el modelo pide una herramienta → el backend la ejecuta con los permisos del usuario → el resultado vuelve al modelo → responde.
- **Herramientas de lectura** (consultas): se ejecutan al momento.
- **Herramientas de escritura** (acciones): Bridgie **no** las ejecuta. Prepara la acción y muestra una **tarjeta de confirmación** con un botón; el cambio solo ocurre cuando el usuario lo confirma. La confirmación vence a los **5 minutos** y no se puede usar dos veces (`ConfirmacionAgenteService`).
- Cada herramienta pertenece a **un rol**. El catálogo que recibe el modelo solo contiene las del rol del usuario; si pide una de otro rol, el backend la rechaza (`PoliticaAgente`).
- Los argumentos se validan de forma estricta (Jackson sin campos desconocidos + Bean Validation) y se verifica que el recurso pertenezca al usuario (`autorizarRecurso`). Ejemplo: un PM solo puede editar proyectos que lidera.

### 1.2 Instrucciones por rol, memoria y límites (commit b7d1703)

- Instrucciones de sistema distintas para cada rol, con el alcance del rol, el nombre del usuario saneado y la fecha en hora de Lima.
- Reglas contra:
  - suplantación de rol;
  - inyección de instrucciones desde mensajes, archivos adjuntos, publicaciones del foro o resultados de herramientas;
  - revelar sus instrucciones, sus herramientas o datos de otras personas.
- Todo texto escrito por usuarios (foro, chat, archivos) se envía al modelo **delimitado como datos**, sin que el contenido pueda cerrar ese bloque.
- **Memoria:** recibe los últimos **10 turnos** de la conversación (solo texto, recortados a 2 500 caracteres).
- **Límites:** 8 mensajes por minuto (`LimiteAgenteService`) y **150 mensajes al día** por usuario (`agente.mensajes-dia`).
- Con archivos adjuntos funciona en **modo solo lectura**: no puede preparar acciones.

### 1.3 Qué puede hacer cada rol

| Rol | Consultas (al momento) | Acciones (con confirmación) |
|---|---|---|
| Colaborador | Su perfil, habilidades, carga y pendientes; su agenda (eventos próximos de sus proyectos); su resumen profesional IA | Actualizar su biografía (redactada a partir de su resumen IA); crear un evento en un proyecto donde tiene asignación activa |
| Project Manager | Sus proyectos y cobertura; listar sus proyectos; **ver el detalle de un proyecto**; buscar candidatos (ranking explicado); su agenda | Crear proyecto; **editar proyecto (edición parcial)**; proponer candidato al RM; crear evento de proyecto |
| Resource Manager | Capacidad y solicitudes; propuestas pendientes; su agenda (eventos personales e hitos de proyectos) | Aprobar o rechazar propuestas; **mover o borrar eventos de su agenda personal** |
| Administrador | Métricas globales; lista de cuentas (sin secretos); auditoría del agente (últimas 30 operaciones, sin prompts) | Activar o suspender cuentas |

### 1.4 Novedades de esta etapa

- **Agenda para todos los roles** (commit 314321c):
  - Colaborador y PM consultan su agenda y preparan eventos de proyecto, solo en proyectos con asignación activa.
  - El RM edita o elimina sus eventos personales; se verifica que el evento sea suyo (`EventoService.esPersonalDe`).
- **Biografía desde el resumen IA:** el colaborador puede pedir "redacta mi biografía con mi resumen IA". Bridgie lee el resumen (solo lectura) y prepara la biografía, que se guarda solo si el usuario confirma.
- **Edición parcial de proyectos** (commit e6b331d): antes `editar_proyecto` exigía todos los campos. Ahora solo pide el id, y lo que no se envía se conserva. Se agregó `ver_proyecto` para que Bridgie conozca el proyecto antes de editarlo. Debe venir al menos un cambio y el nombre no puede quedar vacío.
- **Panel "Qué puede hacer Bridgie"** (commit 234a9e9): en el lado derecho de la pantalla del asistente se listan, según el rol, las acciones y consultas disponibles, cada una con una frase de ejemplo. Al tocarla, la frase se escribe en el cuadro de mensaje.
- La tarjeta de confirmación muestra un nombre legible de la acción.

### 1.5 Pruebas

`BridgieSeguridadTest` (9 pruebas) verifica:
- el catálogo por rol;
- el rechazo de herramientas de otro rol;
- el modo solo lectura con archivos;
- los argumentos estrictos;
- el dueño de los eventos y los proyectos ajenos;
- la edición parcial;
- la lectura del resumen IA propio.

---

## 2. AI Talent Matching (Resource Manager y Project Manager)

**Sección sugerida:** Funcionalidades de IA → AI Talent Matching.

### 2.1 Cómo funciona

- `TalentMatchingService` calcula el **puntaje de match** (habilidades, disponibilidad y carga). No usa IA, así que el número es reproducible.
- `MatchingIaService` usa la IA para interpretar la búsqueda en lenguaje natural, explicar cada recomendación y detectar brechas.

### 2.2 Mejoras (commit 8a18ff1)

- **"Recomendado"** solo aparece para quien cumple al menos el **50 %** de las habilidades pedidas. Antes se marcaba a los primeros aunque tuvieran 0 %.
- **Aviso de brecha de habilidades:** si nadie libre cumple lo pedido, aparece un aviso con enlace a Cursos, y cada tarjeta afectada lleva la etiqueta "Brecha técnica". La IA debe decir claramente cuando la coincidencia es baja.
- **Propuestas pendientes del RM**, con contexto para decidir:
  - match y subpuntajes recalculados hoy, junto con el valor que tenían al proponer;
  - habilidades del proyecto que el candidato cumple al nivel pedido (X de N);
  - mejor alternativa libre (al menos 5 puntos más y no menos habilidades), o aviso de que el candidato es de las mejores opciones;
  - enlaces al perfil y al proyecto, y vacantes del proyecto.
- Rechazar exige un comentario para el PM, validado también en el servidor. Aprobar pide confirmación mostrando la carga resultante.

### 2.3 Proponer y asignar sin recargar (commit c66a0d6)

- Endpoints JSON nuevos:
  - `POST /api/matching/proyectos/{id}/proponer`: PM, solo en proyectos que lidera.
  - `POST /api/matching/proyectos/{id}/asignar`: RM.
- Validan la persona y la dedicación (1–100 %) con las mismas reglas de negocio que los formularios.
- Antes, la página se recargaba y se perdía el análisis de la IA. Ahora la tarjeta se marca "Propuesto al RM" o "Asignado", el análisis queda en pantalla y se puede elegir a varias personas.

---

## 3. Reportes PM y RM (commits 544ea6e y 4d4ce50)

**Sección sugerida:** Funcionalidades → Reportes; Funcionalidades de IA → Resumen IA de proyectos.

- **Ficha por proyecto** (`FichaProyectoService`, compartida por PM y RM), con:
  - descripción, tecnologías, estado, fechas, avance, situación y riesgo;
  - PM, vacantes, y equipo con su dedicación;
  - cobertura de habilidades requeridas;
  - entregables abiertos y vencidos, próximas fechas y alertas.
  - Máximo 20 fichas por reporte.
- **Resumen IA por proyecto:** 2–3 oraciones por proyecto, todas en una sola llamada a la IA. El texto de los proyectos viaja delimitado como datos. Se activa con el botón "Generar resúmenes IA" en la pestaña **Fichas**, o con la casilla "Incluir resumen IA por proyecto" para el Excel y el PDF.
- **Exportación** (`FichaProyectoExport`):
  - Excel con **una hoja por proyecto**, enlazada desde la hoja de resumen.
  - PDF con **una página por proyecto**.
- **Filtros del PM:**
  - periodo rápido: últimos 6 meses, último año o últimos 3 años;
  - "Mostrar últimos 5/10/20 proyectos", del más reciente al más antiguo;
  - el rango de fechas incluye los proyectos que estuvieron activos en algún momento del rango.
- **Filtros del RM:**
  - el filtro de proyectos pasó a ser de **selección múltiple** (antes era uno solo);
  - Excel y PDF con una hoja o página por proyecto elegido, o por cada proyecto en curso si no se elige ninguno;
  - se corrigió la fila de filtros, que se desalineaba al elegir varios proyectos. Ahora el selector muestra "N proyectos" y los nombres al pasar el mouse.
- Pruebas: `FiltrosReporteTest` (3 pruebas) cubre los periodos, los valores inválidos y la selección múltiple.

---

## 4. Acceso con Google (commits 310a30a de Rosell y a199af6)

**Sección sugerida:** Autenticación y seguridad; Despliegue; Modelo de datos; Pruebas.

- Botón **"Continuar con Google"** en el login (OAuth 2.0 / OpenID Connect).
  - Solo funciona si `GOOGLE_LOGIN_ENABLED=true`; si no, aparece deshabilitado.
  - Solo pueden entrar cuentas que **ya existen** en SkillBridge.
  - El vínculo se guarda en la tabla **`usuarios_google`** (`usuario_id`, `google_sub` único).
  - Clases: `GoogleAccessService`, `IdentidadGoogle`, `GoogleLoginHandler`, `LoginExitosoHandler`.
- Se agregó al repositorio:
  - `GOOGLE_LOGIN.md`: guía de configuración (credenciales de Google Cloud, URI de redirección y variables de entorno). Se activa después de publicar con HTTPS (Caddy).
  - `GoogleLoginFlowTest`: 6 pruebas.
  - La tabla `usuarios_google` en `db/skillbridge_db.sql`. Antes faltaba, y una instalación nueva no podía entrar con Google. En una base existente se usa `db/migracion_google_login.sql`, que se puede ejecutar más de una vez.
- El hallazgo H08 (acceso con Google) queda **resuelto**.

---

## 5. Interfaz

**Sección sugerida:** Diseño de interfaz.

- **Favicon nuevo** (commit e089a21), generado desde el nuevo logo: `favicon.ico` (16/32/48 px), PNG de 16/32/48/192 px y `apple-touch-icon.png` (180 px). Ahora lo declaran las **66 páginas** (antes solo 8).
- **Sidebar del colaborador** (commit 75dc795): se quitó "Notificaciones" del pie, porque se duplicaba con la campanita del topbar, que sigue disponible.
- Los recuadros informativos (callout) muestran el título en su propia línea.

---

## 6. Costos: CAPEX y OPEX (commits 232f633 y d5728ee)

**Sección sugerida:** Análisis de costos.

- Archivo: `Documentacion_Costos/SkillBridge_CAPEX_OPEX.xlsx`. Contiene hoja de resumen, detalle de CAPEX, detalle de OPEX, proyección a 3 años y supuestos editables con las fuentes de precios.
- Cifras:

| Concepto | Monto |
|---|---|
| CAPEX (inversión inicial) | US$ 5 735,40 |
| OPEX mensual | US$ 212,00 |
| OPEX anual | US$ 2 544,01 |
| TCO a 3 años | US$ 13 367,42 |

- Infraestructura: AWS EC2 con **MySQL 8 en Docker dentro de la misma EC2**, que no agrega costo. Amazon RDS queda como mejora de la fase 2 y se activa con el supuesto "¿Migrar la base a Amazon RDS?" (por defecto 0 = no).
- La IA presupuestada es el consumo de la API de Claude.
- Dominio: hoy se usa DuckDNS; se presupuesta un dominio propio.

---

## 7. Cambios de otros integrantes que también deben figurar

- **Alvaro**:
  - Buscador del colaborador (commit c83851c): entregables, foros, calendario y cursos, con `BuscadorColaboradorServiceTest` (3 pruebas).
  - Revisión de entregables por estados con comentario obligatorio (commit e1d498b).
- **Rosell**: acceso con Google y mejora del login (commit 310a30a).

---

## 8. Cifras actualizadas para la documentación

### 8.1 Pruebas automatizadas

124 pruebas en 15 clases. Las 124 pasan (0 fallos, 0 errores, 0 omitidas).

| Clase de prueba | Pruebas |
|---|---|
| AuthServiceBloqueoTest | 2 |
| AuthServiceClaveDemoTest | 2 |
| BridgieSeguridadTest | 9 |
| BuscadorColaboradorServiceTest | 3 |
| BuscadorServiceAlcanceTest | 10 |
| EmailServiceTest | 22 |
| FiltrosReporteTest | 3 |
| GoogleLoginFlowTest | 6 |
| LectorAdjuntosIaTest | 4 |
| PasswordPolicyTest | 13 |
| PreferenciaNotificacionServiceTest | 11 |
| ProyectoServiceDedicacionTest | 11 |
| RmOcupacionControllerTest | 7 |
| TotpTest | 14 |
| UsuarioServiceTest | 7 |
| **Total** | **124** |

### 8.2 Cobertura (JaCoCo)

Total del proyecto: **8,1 % de líneas** (1 308 de 16 166) y **6,9 % de ramas**.

| Clase | Líneas | Ramas |
|---|---|---|
| PasswordPolicy | 100 % | 100 % |
| GoogleAccessService | 100 % | 87,5 % |
| IdentidadGoogle | 100 % | 68,8 % |
| RmOcupacionController | 93,5 % | 70,0 % |
| Totp | 93,2 % | 95,8 % |
| PreferenciaNotificacionService | 91,9 % | 87,5 % |
| LectorAdjuntosIa | 90,0 % | 73,2 % |
| EmailService | 83,4 % | 68,6 % |
| GoogleLoginHandler | 81,4 % | 75,0 % |
| BuscadorService | 75,2 % | 52,1 % |
| LoginExitosoHandler | 62,1 % | 45,8 % |
| HerramientasAgente (Bridgie) | 49,5 % | 46,1 % |
| UsuarioService | 29,1 % | 13,3 % |
| AuthService | 19,1 % | 12,1 % |
| BuscadorColaboradorService | 15,2 % | 16,2 % |
| ProyectoService | 8,8 % | 5,6 % |

### 8.3 Tamaño del código

| Paquete | Clases |
|---|---|
| controller | 60 |
| service | 60 (más 6 en service/agente para Bridgie) |
| repository | 43 |
| model | 46 |
| dto | 76 |

### 8.4 Base de datos

- Esquema `skillbridge_db` con **56 tablas**.
- Para levantar la web desde cero se ejecuta solo **`db/skillbridge_db.sql`**, que crea 50 tablas. La aplicación crea el resto al arrancar: las 2 de Spring Session, los tokens de recuperación de contraseña y algunas tablas del agente.
- En una base existente, para agregar el acceso con Google, se ejecuta `db/migracion_google_login.sql`.
