# Servicio REST: AI Talent Matching (RF05)

Ranking explicable de candidatos para un proyecto. Java calcula el puntaje con los datos reales; la IA
solo interpreta la petición y redacta las explicaciones.

- **Controlador:** `controller/MatchingApiController`
- **Ruta base:** `/api/matching`
- **Acceso:** Resource Manager (cualquier proyecto) o Project Manager (solo proyectos que lidera).

---

## 1. Analizar un proyecto

| | |
|---|---|
| **Ruta** | `/api/matching/proyectos/{id}/analizar` |
| **Método** | `POST` (con `X-CSRF-TOKEN`) |
| **Parámetros** | `id` (path, Long): id del proyecto · cuerpo JSON opcional `{"dedicacion": Integer}` (% de dedicación; por defecto `MatchingIaService.DEDICACION_POR_DEFECTO`) |

**Ejemplo de solicitud:**
```json
{ "dedicacion": 50 }
```

**Ejemplo de respuesta (200):**
```json
{
  "proyectoId": 7, "proyecto": "Portal Andes", "vacantes": 2, "dedicacion": 50,
  "resumen": "Prioriza a Diego Salazar: cubre Spring Boot y Docker y está libre al 60 %.",
  "ia": true, "aviso": null,
  "candidatos": [
    {
      "perfilId": 12, "nombre": "Diego Salazar", "iniciales": "DS", "cargo": "Backend Developer",
      "score": 86, "scoreHabilidades": 90, "scoreExperiencia": 75, "scoreDisponibilidad": 92,
      "experienciaAnios": 3, "disponibilidad": 60, "cargaActual": 40, "cargaProyectada": 90,
      "habilidades": [ { "nombre": "Spring Boot", "cumple": true, "requerido": "Avanzado", "tiene": "Avanzado" } ],
      "explicacion": "Cumple las 3 habilidades requeridas y tiene capacidad.", "recomendado": true
    }
  ],
  "interpretacion": null
}
```

| Código | Significado |
|---|---|
| 200 | Ranking calculado (si la IA falla, `ia=false` y explicaciones calculadas por el sistema) |
| 400 | Datos inválidos |
| 403 | El usuario no es RM ni PM de ese proyecto |
| 404 | El proyecto no existe |

## 2. Consulta en lenguaje natural

| | |
|---|---|
| **Ruta** | `/api/matching/consulta` |
| **Método** | `POST` (con `X-CSRF-TOKEN`) |
| **Parámetros** | cuerpo JSON `{"texto": String}` |

**Ejemplo de solicitud:**
```json
{ "texto": "Necesito 2 backend con Spring Boot y Docker para Portal Andes al 50%" }
```

**Ejemplo de respuesta (200):** misma forma que el punto 1, con `interpretacion` completada:
```json
{ "interpretacion": { "habilidades": ["Spring Boot", "Docker"], "cantidad": 2, "dedicacion": 50, "porIa": true } }
```

| Código | Significado |
|---|---|
| 200 | Ranking calculado |
| 400 | No se reconoció un proyecto o el texto está vacío |
| 403 | El usuario no es RM ni PM |
| 404 | El proyecto indicado no existe |
