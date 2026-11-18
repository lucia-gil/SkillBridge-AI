# Servicio REST: Asistente IA (agente)

Conversaciones del Asistente IA de los 4 roles. Cada mensaje pasa por el agente (Anthropic Messages API
con herramientas por rol); las escrituras solo se preparan y se confirman aparte
(ver [Confirmaciones del agente](04_confirmaciones_agente.md)).

- **Controlador:** `controller/AsistenteIaController`
- **Ruta base:** `/api/asistente`
- **Acceso:** sesión iniciada (sin sesión responde `401`). Cada conversación solo la ve su dueño en el rol con que la creó.

---

## 1. Listar conversaciones

| | |
|---|---|
| **Ruta** | `/api/asistente/conversaciones` |
| **Método** | `GET` |
| **Parámetros** | Ninguno |

**Ejemplo de respuesta (200):**
```json
[ { "id": 21, "titulo": "Candidatos para Portal Andes", "cuando": "Hoy, 09:15", "fijada": true } ]
```

## 2. Mensajes de una conversación

| | |
|---|---|
| **Ruta** | `/api/asistente/conversaciones/{id}` |
| **Método** | `GET` |
| **Parámetros** | `id` (path, Long) |

**Ejemplo de respuesta (200):**
```json
[
  { "id": 301, "deUsuario": true, "texto": "¿Quién está en la banca?", "hora": "09:15", "adjuntos": [] },
  { "id": 302, "deUsuario": false, "texto": "Hay 3 personas sin proyecto: ...", "hora": "09:15", "adjuntos": [] }
]
```

| Código | Significado |
|---|---|
| 200 | Mensajes |
| 401 | Sin sesión |
| 404 | La conversación no existe o no es del usuario |

## 3. Renombrar o fijar

| | |
|---|---|
| **Ruta** | `/api/asistente/conversaciones/{id}` |
| **Método** | `PUT` (con `X-CSRF-TOKEN`) |
| **Parámetros** | `id` (path, Long) · cuerpo JSON `{"titulo": String?, "fijada": Boolean?}` (al menos uno) |

**Ejemplo de solicitud:** `{ "titulo": "Banca de noviembre" }`

**Ejemplo de respuesta (200):** `{ "id": 21, "titulo": "Banca de noviembre", "cuando": "Hoy, 09:15", "fijada": true }`

| Código | Significado |
|---|---|
| 200 | Actualizada |
| 400 | Título vacío, de más de 120 caracteres o sin cambios |
| 404 | La conversación no existe o no es del usuario |

## 4. Eliminar

| | |
|---|---|
| **Ruta** | `/api/asistente/conversaciones/{id}` |
| **Método** | `DELETE` (con `X-CSRF-TOKEN`) |
| **Parámetros** | `id` (path, Long) |

**Ejemplo de respuesta (200):** `{ "ok": true }`

| Código | Significado |
|---|---|
| 200 | Eliminada |
| 404 | La conversación no existe o no es del usuario |

## 5. Enviar mensaje

| | |
|---|---|
| **Ruta** | `/api/asistente/mensaje` |
| **Método** | `POST` (con `X-CSRF-TOKEN`) |
| **Parámetros** | cuerpo JSON `{"conversacionId": Long?, "mensaje": String}` (sin `conversacionId` se crea una nueva). También acepta `multipart/form-data`, pero los adjuntos están deshabilitados y se rechazan con `400`. |

**Ejemplo de solicitud:**
```json
{ "conversacionId": null, "mensaje": "¿Qué entregables tengo pendientes esta semana?" }
```

**Ejemplo de respuesta (200):**
```json
{
  "conversacionId": 22, "titulo": "Entregables pendientes", "nueva": true,
  "respuesta": "Tienes 2 entregables: ...", "ok": true, "hora": "09:20",
  "acciones": [ { "etiqueta": "Ver entregables", "url": "/colaborador/entregables.html" } ],
  "adjuntos": []
}
```

| Código | Significado |
|---|---|
| 200 | Respuesta generada (`ok=false` si la IA no respondió; el texto explica el motivo) |
| 400 | Mensaje vacío, demasiado largo o con adjuntos |
| 401 | Sin sesión |
| 404 | `conversacionId` no existe o no es del usuario |
| 429 | Se superó el límite de solicitudes por minuto (`agente.solicitudes-minuto`) |

## 6. Ver o descargar un adjunto

| | |
|---|---|
| **Ruta** | `/api/asistente/adjuntos/{id}` |
| **Método** | `GET` |
| **Parámetros** | `id` (path, Long) |
| **Respuesta** | El archivo binario: imágenes y PDF *inline*, el resto como descarga (`Content-Disposition`). |

| Código | Significado |
|---|---|
| 200 | Archivo |
| 404 | El adjunto no existe o no es del usuario |

## 7. Respuesta en curso

Permite reabrir la conversación si la persona salió de la página mientras la IA respondía.
El navegador la consulta al cargar el asistente y luego cada 3 s hasta que termine.

| | |
|---|---|
| **Ruta** | `/api/asistente/en-curso` |
| **Método** | `GET` |
| **Parámetros** | Ninguno |

**Ejemplo de respuesta (200), con una respuesta generándose:** `{ "conversacionId": 22 }`

**Ejemplo de respuesta (200), sin nada pendiente:** `{ "conversacionId": null }`

| Código | Significado |
|---|---|
| 200 | Estado actual |
| 401 | Sin sesión |
