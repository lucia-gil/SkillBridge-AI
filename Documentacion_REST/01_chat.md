# Servicio REST: Chat de proyectos (RF07)

Chat por proyecto para sus integrantes activos (PM y colaboradores). El navegador consulta los mensajes
nuevos cada 3 s (*polling*).

- **Controlador:** `controller/ChatApiController`
- **Ruta base:** `/api/chat`
- **Acceso:** sesión iniciada e integrante activo del proyecto de la sala (si no, `403`).

---

## 1. Listar mis salas

| | |
|---|---|
| **Ruta** | `/api/chat/salas` |
| **Método** | `GET` |
| **Parámetros** | Ninguno |

**Ejemplo de respuesta (200):**
```json
[
  {
    "id": 3, "proyectoId": 7, "proyecto": "Portal Andes", "estadoProyecto": "activo",
    "soloLectura": false, "miRol": "project_manager", "miembros": 5,
    "ultimoId": 128, "ultimoAutor": "Ana Ríos", "ultimoTexto": "Subí el diseño final",
    "ultimoCuando": "hace 5 min", "ultimoIso": "2026-11-18T10:42:00", "noLeidos": 2
  }
]
```

| Código | Significado |
|---|---|
| 200 | Lista (vacía si no integra ningún proyecto) |

## 2. Integrantes de una sala

| | |
|---|---|
| **Ruta** | `/api/chat/salas/{id}/miembros` |
| **Método** | `GET` |
| **Parámetros** | `id` (path, Long): id de la sala |

**Ejemplo de respuesta (200):**
```json
[
  { "perfilId": 4, "nombre": "Ana Ríos", "iniciales": "AR", "cargo": "Backend Developer",
    "rol": "project_manager", "usuarioId": 5, "tieneFoto": true }
]
```

| Código | Significado |
|---|---|
| 200 | Lista de integrantes |
| 403 | No es integrante del proyecto |
| 404 | La sala no existe |

## 3. Mensajes de una sala

| | |
|---|---|
| **Ruta** | `/api/chat/salas/{id}/mensajes` |
| **Método** | `GET` |
| **Parámetros** | `id` (path, Long) · `despues` (query, opcional): solo mensajes con id mayor (polling) · `antes` (query, opcional): mensajes anteriores (paginación hacia atrás). Sin ninguno: últimos 40. |

**Ejemplo de solicitud:** `GET /api/chat/salas/3/mensajes?despues=125`

**Ejemplo de respuesta (200):**
```json
[
  { "id": 126, "autorId": 4, "autor": "Ana Ríos", "iniciales": "AR", "cargo": "Backend Developer",
    "usuarioId": 5, "tieneFoto": true, "contenido": "¿Revisamos el endpoint hoy?",
    "iso": "2026-11-18T10:40:00", "hora": "10:40", "dia": "Hoy",
    "fechaCompleta": "18 de noviembre de 2026, 10:40", "propio": false }
]
```

| Código | Significado |
|---|---|
| 200 | Lista de mensajes |
| 403 | No es integrante del proyecto |
| 404 | La sala no existe |

## 4. Enviar mensaje

| | |
|---|---|
| **Ruta** | `/api/chat/salas/{id}/mensajes` |
| **Método** | `POST` (con `X-CSRF-TOKEN`) |
| **Parámetros** | `id` (path, Long) · cuerpo JSON `{"contenido": String}` (obligatorio, con límite de caracteres) |

**Ejemplo de solicitud:**
```json
{ "contenido": "Listo, ya hice el merge." }
```

**Ejemplo de respuesta (201):** el mensaje creado, con la misma forma que en el punto 3.

| Código | Significado |
|---|---|
| 201 | Mensaje creado |
| 400 | Mensaje vacío, demasiado largo o proyecto en solo lectura (cerrado/cancelado) |
| 403 | No es integrante del proyecto |
| 404 | La sala no existe |

## 5. Marcar como leído

| | |
|---|---|
| **Ruta** | `/api/chat/salas/{id}/leido` |
| **Método** | `POST` (con `X-CSRF-TOKEN`) |
| **Parámetros** | `id` (path, Long) · cuerpo JSON `{"ultimoId": Long}` |

**Ejemplo de respuesta (200):** `{ "ok": true }`

| Código | Significado |
|---|---|
| 200 | Registrado |
| 403 | No es integrante del proyecto |
| 404 | La sala no existe |

## 6. Total de no leídos

| | |
|---|---|
| **Ruta** | `/api/chat/no-leidos` |
| **Método** | `GET` |
| **Parámetros** | Ninguno |

**Ejemplo de respuesta (200):** `{ "total": 4 }`

## 7. Resumir con IA

| | |
|---|---|
| **Ruta** | `/api/chat/salas/{id}/resumen-ia` |
| **Método** | `POST` (con `X-CSRF-TOKEN`) |
| **Parámetros** | `id` (path, Long) |

**Ejemplo de respuesta (200):**
```json
{ "resumen": "- Se acordó cerrar el sprint el viernes\n- Pendiente: pruebas del login (Diego)" }
```

| Código | Significado |
|---|---|
| 200 | Resumen generado |
| 400 | Hay muy pocos mensajes o la IA no está disponible |
| 403 | No es integrante del proyecto |
| 404 | La sala no existe |
