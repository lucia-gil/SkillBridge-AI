# Servicio REST: Buscador global (Ctrl+K)

- **Controlador:** `controller/BuscadorController`
- **Acceso:** sesión iniciada; el alcance depende del rol (el Colaborador no ve a otras personas).

---

## 1. Búsqueda instantánea

| | |
|---|---|
| **Ruta** | `/cuenta/buscar` |
| **Método** | `GET` |
| **Parámetros** | `q` (query, String, opcional): texto escrito |

**Ejemplo de solicitud:** `GET /cuenta/buscar?q=port`

**Ejemplo de respuesta (200):**
```json
[ { "tipo": "Proyectos", "icono": "icon-folder", "titulo": "Portal Andes", "detalle": "Activo", "url": "/resource-manager/proyectos/7.html" } ]
```

| Código | Significado |
|---|---|
| 200 | Coincidencias (lista vacía si no hay) |

## 2. Preguntar a la IA

| | |
|---|---|
| **Ruta** | `/cuenta/buscar-ia` |
| **Método** | `POST` (con `X-CSRF-TOKEN`) |
| **Parámetros** | cuerpo JSON `{"q": String}` |

**Ejemplo de solicitud:**
```json
{ "q": "¿quién sabe Docker y está libre?" }
```

**Ejemplo de respuesta (200):**
```json
{
  "interpretacion": "Personas con Docker y al menos 30 % libre",
  "chips": ["Docker", "≥ 30 % libre"], "porIa": true, "aviso": null,
  "resultados": [ { "tipo": "Personas", "icono": "icon-user-check", "titulo": "Diego Salazar", "detalle": "Docker (Avanzado) · 60 % libre", "url": "/resource-manager/colaboradores/12.html" } ]
}
```

| Código | Significado |
|---|---|
| 200 | Filtros interpretados y resultados |
| 400 | Pregunta vacía o inválida |
| 401 | La sesión expiró |
