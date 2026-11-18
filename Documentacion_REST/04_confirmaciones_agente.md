# Servicio REST: Confirmaciones del agente IA

Cuando el agente prepara un cambio (crear/editar proyecto, proponer candidato, resolver propuesta,
cambiar estado de cuenta, actualizar biografía), queda una **confirmación pendiente** de 5 minutos.
Solo se ejecuta si la persona pulsa *Confirmar cambio*; un "sí" escrito en el chat no basta.

- **Controlador:** `controller/ConfirmacionAgenteController`
- **Ruta base:** `/api/asistente/confirmaciones`
- **Acceso:** sesión iniciada y agente habilitado (`agente.enabled=true`).

---

## 1. Confirmación pendiente

| | |
|---|---|
| **Ruta** | `/api/asistente/confirmaciones` |
| **Método** | `GET` |
| **Parámetros** | Ninguno |

**Ejemplo de respuesta (200):**
```json
[
  { "id": "3f1c2a9e-8d0b-4b7e-9a51-6a2f0e4c7d11", "herramienta": "proponer_candidato",
    "argumentos": "{\"proyectoId\":7,\"candidatoId\":12,\"dedicacion\":50}",
    "expira": "2026-11-18T09:25:00" }
]
```
Lista vacía si no hay nada pendiente.

## 2. Confirmar

| | |
|---|---|
| **Ruta** | `/api/asistente/confirmaciones/{id}/confirmar` |
| **Método** | `POST` (con `X-CSRF-TOKEN`) |
| **Parámetros** | `id` (path, UUID de 36 caracteres) |

**Ejemplo de respuesta (200):** `{ "ok": true }`

| Código | Significado |
|---|---|
| 200 | Cambio ejecutado y auditado |
| 400 | Los datos ya no cumplen las reglas de negocio (no se ejecutó nada) |
| 403 | Confirmación inexistente, vencida, ya usada o de otro usuario; o agente deshabilitado |
| 429 | Límite de solicitudes del agente |

## 3. Cancelar

| | |
|---|---|
| **Ruta** | `/api/asistente/confirmaciones/{id}/cancelar` |
| **Método** | `POST` (con `X-CSRF-TOKEN`) |
| **Parámetros** | `id` (path, UUID) |

**Ejemplo de respuesta (200):** `{ "ok": true }`

| Código | Significado |
|---|---|
| 200 | Cancelada |
| 403 | Confirmación inexistente, vencida, ya usada o de otro usuario |
