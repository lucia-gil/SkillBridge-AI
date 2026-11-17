# Resultados de verificación

Fecha: 17 de noviembre de 2026. Java 17.0.19. `mvn test`: 31 pruebas, 0 fallos, 0 errores y 0 omitidas.

| Suite | Pruebas | Fallos | Errores | Omitidas |
| --- | ---: | ---: | ---: | ---: |
| HerramientasAgenteTest | 10 | 0 | 0 | 0 |
| PersistenciaAgenteTest | 8 | 0 | 0 | 0 |
| PoliticaAgenteTest | 6 | 0 | 0 | 0 |
| ProveedorAgenteTest | 4 | 0 | 0 | 0 |
| SeguridadHttpAgenteTest | 3 | 0 | 0 | 0 |

## Cobertura
- Herramientas: catálogo separado, escalada, argumentos adicionales, límites, campos requeridos, coerción de IDs, proyecto ajeno y UPDATE de biografía acotado al actor.
- Política: identidad de sesión, cuenta suspendida, anonimato, perfil ajeno, revocación de PM y huella de asignaciones.
- Persistencia auxiliar H2/MySQL: confirmación de uso único, doble confirmación concurrente, caducidad, propietario, cancelación, rollback, revocación y cuota/lease.
- Proveedor HTTP simulado: ciclo tool_use/tool_result, escritura únicamente preparada, fallo sin exposición de respuesta interna y adjunto rechazado.
- HTTP con SecurityConfig real: GET anónimo devuelve 401, POST sin CSRF es rechazado y POST autenticado con CSRF llega al servicio. Los servicios de negocio se simulan en esta suite.

## Qué no demuestra
No se arrancó toda la aplicación contra la base MySQL del usuario; no se usaron claves reales de Anthropic ni se efectuó una prueba completa en navegador. H2 no equivale a MySQL para todos los detalles de aislamiento/bloqueo. Las pruebas de integración y prompts de la guía deben ejecutarse en un entorno de pruebas real antes del despliegue productivo.

Los PDF se renderizaron y revisaron visualmente. El ZIP se compara por contenido con el archivo original; el manifiesto incluye hashes de origen y destino. No se distribuyen logs de ejecución, claves, dependencias ni target.
