# Servicio REST: Catálogo de habilidades (Administrador)

Consulta paginada del catálogo para autocompletado y tablas.

- **Controlador:** `controller/AdminHabilidadesApiController`
- **Acceso:** `hasAuthority('administrador')` (método protegido con `@PreAuthorize`).

---

## 1. Buscar habilidades

| | |
|---|---|
| **Ruta** | `/administrador/api/habilidades` |
| **Método** | `GET` |
| **Parámetros** | `q` (query, String, opcional) · `categoriaId` (query, Long, opcional, > 0) · `page` (query, int, por defecto 0) · `size` (query, int, 1 a 50, por defecto 12) |

**Ejemplo de solicitud:** `GET /administrador/api/habilidades?q=spr&page=0&size=5`

**Ejemplo de respuesta (200):**
```json
{
  "contenido": [
    { "id": 8, "nombre": "Spring Boot", "categoriaId": 2, "categoriaNombre": "Backend",
      "personas": 9, "proyectos": 4, "nivelMedio": "Avanzado",
      "demanda": "Alta", "demandaBadgeClass": "badge-red" }
  ],
  "pagina": 0, "tamano": 5, "totalElementos": 1, "totalPaginas": 1, "primera": true, "ultima": true
}
```

| Código | Significado |
|---|---|
| 200 | Página de resultados |
| 400 | `categoriaId` no válido |
| 403 | El usuario no es administrador |
