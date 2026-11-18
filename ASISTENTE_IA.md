# Agente IA de SkillBridge

La implementación actual usa Anthropic Messages API con herramientas por rol y confirmaciones de escritura en el backend. La documentación previa de Gemini y el chat sin herramientas ha sido sustituida por:

- [Cambios y arquitectura detallada](Documentacion_IA/Transicion_Chat_Agente_IA.md)
- [Guía de pruebas](Documentacion_IA/Guia_Pruebas_Agente_IA.pdf)
- [Arquitectura y cambios](Documentacion_IA/Arquitectura_Agente_IA.pdf)
- [Resultados automatizados](Documentacion_IA/Resultados_Pruebas_Agente.md)
- [Aplicación del parche](LEEME_ACTUALIZACION.md)

Configuración: ANTHROPIC_API_KEY, CLAUDE_MODELOS, AGENTE_ENABLED (true por defecto), AGENTE_SOLICITUDES_MINUTO (8). El modelo debe estar disponible para tu cuenta. El agente no usa el proveedor de respaldo de otros módulos.

No se entrenó un modelo nuevo. Se habilitaron consultas y propuestas de cambios con autorización por rol/recurso y confirmación explícita. Los archivos adjuntos del asistente están deshabilitados. El modelo recibe solamente el turno actual; el historial se conserva en la interfaz cuando sus permisos continúan vigentes.
