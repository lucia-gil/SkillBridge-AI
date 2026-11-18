# Servicio REST: Ajustar carga (Resource Manager)

Lo usa el modal "Ajustar carga" de Colaboradores para cambiar la dedicación de una asignación o
finalizarla **sin recargar la página**. Cada respuesta trae la carga actualizada de la persona, con la
que el navegador repinta su fila, el modal y las tarjetas de capacidad.

- **Controlador:** `controller/RmOcupacionController`
- **Ruta base:** `/resource-manager/api/asignaciones`
- **Acceso:** solo Resource Manager (`hasAuthority('resource_manager')` por estar bajo `/resource-manager/**`).

---

## 1. Cambiar la dedicación

| | |
|---|---|
| **Ruta** | `/resource-manager/api/asignaciones/{id}/dedicacion` |
| **Método** | `PUT` (con `X-CSRF-TOKEN`) |
| **Parámetros** | `id` (path, Long): id de la asignación · cuerpo JSON `{"cargaPorcentaje": Integer, "motivo": String?}` (1 a límite de carga; el motivo queda en Auditoría) |

**Ejemplo de solicitud:**
```json
{ "cargaPorcentaje": 75, "motivo": "Cambio de prioridades" }
```

**Ejemplo de respuesta (200):**
```json
{
  "perfilId": 12, "ocupacion": 75, "limite": 100, "cargaPct": 75,
  "cargaClave": "opt", "cargaLabel": "Óptimo", "mensaje": "Dedicación actualizada a 75%.",
  "asignaciones": [
    { "asignacionId": 31, "proyecto": "App Móvil Aurora", "rolLabel": "Colaborador", "esProjectManager": false, "carga": 75 }
  ]
}
```
`cargaClave`: `banca` (0%), `sub`, `opt`, `lim` (a 10 puntos del límite o menos) o `sobre` (pasa el límite).

| Código | Significado |
|---|---|
| 200 | Dedicación actualizada |
| 400 | Valor fuera de rango, igual al actual o asignación no activa |
| 403 | No es Resource Manager o falta el token CSRF |
| 404 | La asignación no existe |

## 2. Finalizar una asignación

| | |
|---|---|
| **Ruta** | `/resource-manager/api/asignaciones/{id}/finalizar` |
| **Método** | `POST` (con `X-CSRF-TOKEN`) |
| **Parámetros** | `id` (path, Long) · cuerpo JSON opcional `{"motivo": String?}` |

**Ejemplo de respuesta (200):** misma forma que el punto 1, con `"mensaje": "Asignación finalizada."` y la
asignación ya fuera de la lista.

| Código | Significado |
|---|---|
| 200 | Asignación finalizada |
| 400 | Es la asignación del Project Manager (se reemplaza con "Cambiar PM") o ya no está activa |
| 403 | No es Resource Manager o falta el token CSRF |
| 404 | La asignación no existe |
