# Servicios web REST de SkillBridge AI

Índice de los servicios REST que expone la aplicación. Cada documento sigue el mismo formato
(Clase 9.2): **descripción, ruta, método, parámetros, ejemplo de solicitud, ejemplo de respuesta y
códigos HTTP**.

| Documento | Ruta base | Controlador | Lo consume |
|---|---|---|---|
| [Chat de proyectos](01_chat.md) | `/api/chat` | `ChatApiController` | `js/chat.js` |
| [AI Talent Matching](02_matching.md) | `/api/matching` | `MatchingApiController` | `js/matching-ia.js` |
| [Asistente IA](03_asistente.md) | `/api/asistente` | `AsistenteIaController` | `js/asistente-ia.js` |
| [Confirmaciones del agente](04_confirmaciones_agente.md) | `/api/asistente/confirmaciones` | `ConfirmacionAgenteController` | `js/asistente-ia.js` |
| [Buscador global](05_buscador.md) | `/cuenta/buscar`, `/cuenta/buscar-ia` | `BuscadorController` | `js/global-search.js` |
| [Catálogo de habilidades (admin)](06_habilidades_admin.md) | `/administrador/api/habilidades` | `AdminHabilidadesApiController` | uso interno / pruebas |
| [Ajustar carga (RM)](07_carga_rm.md) | `/resource-manager/api/asignaciones` | `RmOcupacionController` | `js/colaboradores-rm.js` |

## Reglas comunes

- **Formato:** JSON en UTF-8 (`Content-Type: application/json;charset=UTF-8`), salvo la descarga de adjuntos.
- **Autenticación:** sesión de Spring Security (cookie `SESSION`, guardada en MySQL con Spring Session JDBC).
  Sin sesión, `/api/asistente/**` responde `401`; el resto redirige al login.
- **CSRF:** todo `POST`, `PUT` y `DELETE` debe llevar la cabecera `X-CSRF-TOKEN` (la agrega `js/sbai-api.js`
  leyendo `<meta name="_csrf">`). Sin el token la respuesta es `403`.
- **CORS:** los servicios son de la misma aplicación, por eso no llevan `@CrossOrigin`; abrirlos a otro
  origen expondría datos de la sesión.
- **Errores:** siempre con el cuerpo `{"error": "mensaje para el usuario"}`.

| Código | Cuándo |
|---|---|
| `200 OK` | Consulta o acción correcta |
| `201 Created` | Se creó un recurso (mensaje de chat) |
| `400 Bad Request` | Datos inválidos o regla de negocio incumplida (`OperacionInvalidaException`) |
| `401 Unauthorized` | Sin sesión (solo `/api/asistente/**` y `/cuenta/buscar-ia`) |
| `403 Forbidden` | Sin permiso sobre el recurso o falta el token CSRF |
| `404 Not Found` | El recurso no existe o no es del usuario (`RecursoNoEncontradoException`) |
| `429 Too Many Requests` | Límite de solicitudes del agente IA por minuto |
