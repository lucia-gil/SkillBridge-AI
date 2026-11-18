# Servicio REST: Chat (salas de proyecto y mensajes directos)

Dos tipos de sala, con los mismos servicios para leer y escribir:

- **Proyecto** (RF07): una por proyecto, para sus integrantes activos (PM y colaboradores).
- **Directo:** una por pareja de personas; solo la ven ellas dos. Iniciar una conversación: el Resource
  Manager y el Administrador pueden escribirle a cualquier cuenta activa; un Miembro (Colaborador o PM),
  solo a un Resource Manager. Quien recibe siempre puede responder.

El navegador consulta los mensajes nuevos cada 3 s (*polling*).

- **Controlador:** `controller/ChatApiController`
- **Ruta base:** `/api/chat`
- **Acceso:** sesión iniciada; en una sala de proyecto, ser integrante activo; en un mensaje directo, ser
  una de sus dos personas (si no, `403`).

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
    "ultimoCuando": "hace 5 min", "ultimoIso": "2026-11-18T10:42:00", "noLeidos": 2,
    "tipo": "proyecto", "contactoPerfilId": null, "contactoUsuarioId": null, "contactoTieneFoto": false,
    "contactoIniciales": null, "contactoDetalle": null
  },
  {
    "id": 9, "proyectoId": null, "proyecto": "Luis Peña", "estadoProyecto": null,
    "soloLectura": false, "miRol": "Mensaje directo", "miembros": 2,
    "ultimoId": 131, "ultimoAutor": "Luis", "ultimoTexto": "¿Puedes tomar 20% más?",
    "ultimoCuando": "10:50", "ultimoIso": "2026-11-18T10:50:00", "noLeidos": 1,
    "tipo": "directo", "contactoPerfilId": 2, "contactoUsuarioId": 4, "contactoTieneFoto": false,
    "contactoIniciales": "LP", "contactoDetalle": "Resource Manager"
  }
]
```
En un mensaje directo, `proyecto` lleva el nombre de la otra persona.

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
| 400 | Hay muy pocos mensajes, la IA no está disponible o es un mensaje directo (solo salas de proyecto) |
| 403 | No es integrante del proyecto |
| 404 | La sala no existe |

## 8. Personas a las que puedo escribir

| | |
|---|---|
| **Ruta** | `/api/chat/contactos` |
| **Método** | `GET` |
| **Parámetros** | `q` (query, String, opcional): nombre, correo o cargo |

**Ejemplo de solicitud:** `GET /api/chat/contactos?q=fio`

**Ejemplo de respuesta (200):**
```json
[ { "perfilId": 9, "nombre": "Fiorella Torres", "iniciales": "FT", "detalle": "DevOps Engineer", "usuarioId": 10, "tieneFoto": false } ]
```
Para un Miembro solo aparecen Resource Managers; para un RM o Administrador, todas las cuentas activas
(hasta 30).

| Código | Significado |
|---|---|
| 200 | Lista (vacía si nadie coincide) |

## 9. Abrir un mensaje directo

| | |
|---|---|
| **Ruta** | `/api/chat/directos` |
| **Método** | `POST` (con `X-CSRF-TOKEN`) |
| **Parámetros** | cuerpo JSON `{"perfilId": Long}`: la otra persona |

Devuelve la sala de esa pareja; la crea la primera vez (nunca hay dos salas para las mismas personas).

**Ejemplo de solicitud:** `{ "perfilId": 3 }`

**Ejemplo de respuesta (200):** una sala con la forma del punto 1 y `"tipo": "directo"`.

| Código | Significado |
|---|---|
| 200 | Sala lista para usar |
| 400 | Falta `perfilId` o es uno mismo |
| 403 | Un Miembro intenta escribirle a alguien que no es Resource Manager, o la cuenta no está activa |
| 404 | La persona no existe |
