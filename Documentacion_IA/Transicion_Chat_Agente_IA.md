# SkillBridge AI: del chat al agente con herramientas

## Entrega y alcance
Se integra un agente sobre el modelo Anthropic existente, no se entrena un modelo nuevo. Se conserva Java 17, Spring Boot 3.2.5, Spring Web MVC, Spring Security, Spring Session JDBC y MySQL. El agente está habilitado por defecto; AGENTE_ENABLED=false lo deshabilita sin restaurar el antiguo flujo que mezclaba datos e instrucciones.

El dominio sigue siendo gestión interna de talento: colaborador, project manager, resource manager y administrador. USUARIO equivale a colaborador y ADMIN a administrador. No se agregan empleos externos, postulaciones laborales ni entrevistas, ausentes en el proyecto original. La biografía editable es distinta del resumen IA generado a partir de habilidades validadas.

## Diferencia entre chat, modelo y agente
- Chat: interfaz conversacional e historial.
- Modelo: servicio remoto que interpreta el mensaje y propone una herramienta con argumentos.
- Agente: ciclo controlado por el servidor que valida la herramienta, consulta servicios y devuelve resultados. Una petición de escritura solo crea una propuesta; la ejecución depende de un botón autenticado y protegido por CSRF.
- La autoridad proviene del SecurityContext y de consultas actuales a usuarios, perfiles y asignaciones. Ningún prompt puede establecer usuarioId o rol.

## Flujo
1. Spring Security exige sesión y CSRF en escrituras. El controlador copia la identidad y rol efectivo para fijarlos durante el turno.
2. LimiteAgenteService reserva una ejecución por usuario en MySQL y contabiliza solicitudes por minuto (8 por defecto).
3. El backend entrega al modelo únicamente el catálogo del rol. Cada herramienta revalida identidad, estado activo, rol organizacional y vínculo de perfil; para PM comprueba liderazgo activo por proyecto.
4. La respuesta tool_use se despacha a funciones conocidas. Jackson rechaza campos desconocidos y coerciones peligrosas; DTO records y Bean Validation controlan rangos, tamaños y obligatoriedad.
5. Consultas autorizadas devuelven datos seleccionados, nunca entidades Usuario completas ni SQL generado por el modelo. Los datos viajan en tool_result, separados del system prompt.
6. Las escrituras guardan una propuesta con UUID aleatorio, actor, rol, argumentos exactos y vencimiento de cinco minutos. El identificador de confirmación se entrega al navegador por un endpoint propio, no al modelo.
7. Confirmar bloquea la propuesta y revalida permisos dentro de una transacción. El estado pasa a ejecutada en la misma transacción que la operación. Un segundo intento falla. Cancelar no ejecuta servicios de negocio.
8. La auditoría del agente registra usuario, herramienta, resultado y correlación; no guarda prompts ni resultados sensibles en el log. Las propuestas sí contienen los argumentos necesarios para la confirmación y deben tratarse como datos de negocio privados.

## Matriz de herramientas implementadas
| Rol | Herramientas de lectura | Escrituras con confirmación |
| --- | --- | --- |
| Colaborador | consultar_mi_perfil_proyectos_entregables | actualizar_mi_biografia |
| Project Manager | consultar_mis_proyectos_y_candidatos, listar_mis_proyectos, buscar_candidatos | crear_proyecto, editar_proyecto, proponer_candidato |
| Resource Manager | consultar_capacidad_y_solicitudes, listar_solicitudes | resolver_propuesta |
| Administrador | consultar_metricas_globales, listar_cuentas, consultar_auditoria_agente | cambiar_estado_cuenta |

Las listas están limitadas a 30 registros; el ranking a 10 candidatos. El contexto existente puede resumir subconjuntos (por ejemplo cinco proyectos del PM), por lo que no representa una exportación completa. El resultado destinado al modelo se limita a 18 000 caracteres. Las herramientas del administrador no heredan automáticamente las de otros roles.

## Escrituras y reglas preservadas
- Biografía: UPDATE parametrizado con perfil_id y usuario_id derivados del actor. No cambia validaciones de habilidades ni el resumen generado por IA.
- Crear proyecto: reutiliza ProyectoService.crear, que valida fechas, cupos y capacidad del PM; el creador es el perfil de sesión.
- Editar proyecto: exige liderazgo activo; conserva GitHub y estado actuales, y reutiliza las reglas del servicio de proyectos.
- Proponer candidato: reutiliza TalentMatchingService.proponerManual; la propuesta queda pendiente del RM.
- Resolver propuesta: reutiliza TalentMatchingService.resolver; aprobar aplica reglas de disponibilidad y carga existentes.
- Cambiar estado de cuenta: reutiliza UsuarioService.cambiarEstado; no permite auto-suspensión ni fuerza salida de proyectos. Si requiere resolver liderazgo o integrantes, la operación se rechaza y se gestiona en las pantallas existentes.
- No hay herramientas para borrar usuarios, cambiar roles, ejecutar SQL, leer secretos, enviar correos arbitrarios ni navegar URLs. Servicios existentes pueden emitir sus notificaciones habituales cuando se confirma una operación de negocio.

## Aislamiento de historial y adjuntos
Las conversaciones se verifican por propietario, rol y huella de asignaciones (agente_alcances). Un cambio en las asignaciones invalida el acceso al historial anterior. Las conversaciones antiguas que no tienen huella quedan inaccesibles por el asistente; no se borran. Las descargas de sus adjuntos siguen la misma política.

El modelo recibe solo el mensaje actual: no se reenvían respuestas históricas con información que podría haber perdido autorización. El historial sigue visible cuando su alcance es válido, pero expresiones como «el segundo de antes» pueden requerir repetir el proyecto o ID.

Se deshabilita la carga de adjuntos del asistente tanto en backend como en su botón. No se envían documentos ni imágenes por este flujo. Esto evita afirmar que el sistema cuenta con análisis antimalware o validación documental que no se ha implementado. Los demás módulos de archivos de la plataforma no se modifican.

## Defensas y límites reales
El prompt orienta al modelo, pero la frontera de seguridad es el código. Incluso si una descripción contiene instrucciones maliciosas, el modelo no recibe herramientas de otro rol y cada llamada se valida de nuevo. No existe garantía de que el modelo nunca produzca una respuesta engañosa: las operaciones reales se comprueban en los servicios, la confirmación y la auditoría.

SalidaAgente filtra patrones comunes de claves, Bearer, JWT y hashes BCrypt, además de limitar tamaño. Es una defensa complementaria: no detecta cualquier dato sensible. La protección principal es seleccionar los campos autorizados antes de enviarlos al proveedor. El frontend inserta texto escapado y las confirmaciones usan .text(), no HTML generado por el modelo.

Hay como máximo tres rondas y dos llamadas por ronda, 900 tokens máximos de salida por petición y una ejecución simultánea por usuario con lease de 240 segundos. El límite por minuto y el lease se almacenan en MySQL y funcionan entre instancias. La conexión HTTP conserva 10 segundos de conexión y 40 segundos de lectura. Las respuestas se limitan en aplicación; el timeout de lectura no constituye un límite absoluto frente a un servidor que envía bytes continuamente.

Las escrituras del agente se serializan mediante agente_mutex. Esto impide carreras entre confirmaciones del agente. Las operaciones iniciadas desde otras pantallas mantienen los mecanismos de concurrencia existentes: la entrega no certifica todas las carreras posibles de la aplicación original. La entrega tampoco cambia la semántica de correo externo de los servicios existentes: una transacción de base de datos no revierte un correo ya enviado.

No se hace fallback a otro proveedor en el agente: un fallo se comunica sin ejecutar cambios ni transferir datos a un proveedor adicional. Los otros usos de IA existentes conservan su configuración.

## Archivos y motivos
| Ruta relativa | Cambio |
| --- | --- |
| pom.xml | H2 solo para pruebas transaccionales; sin nueva dependencia de producción |
| service/agente/ArgumentosAgente.java | DTO tipados y restricciones de validación |
| service/agente/PoliticaAgente.java | Identidad, autorización vigente, liderazgo y huella de alcance |
| service/agente/HerramientasAgente.java | Catálogo, esquemas, proyecciones y operaciones de negocio |
| service/agente/ConfirmacionAgenteService.java | Preparación, expiración, uso único, transacción y auditoría |
| service/agente/LimiteAgenteService.java | Cuotas y concurrencia compartidas en MySQL |
| service/agente/SalidaAgente.java | Filtrado complementario y tamaño de salida |
| service/AsistenteIaService.java | Ciclo Anthropic tools/tool_use/tool_result |
| service/AsistenteConversacionService.java | Historial por alcance, selección del agente y rechazo de adjuntos |
| controller/AsistenteIaController.java | Actor fijo por turno y límite alrededor del envío |
| controller/ConfirmacionAgenteController.java | Listar, confirmar y cancelar mediante sesión/CSRF |
| repository/AsistenteAdjuntoRepository.java | Vínculo de adjunto con conversación para autorización |
| config/InicializadorAgente.java | Inicialización idempotente opcional del esquema nuevo |
| src/main/resources/db/agente.sql | Cinco tablas auxiliares; no altera tablas existentes |
| application.properties / application-prod.properties | Activación, cuotas y configuración de despliegue seguro |
| static/js/asistente-ia.js | Panel de confirmación con datos exactos y botones |
| templates/compartido/asistente-ia.html | Oculta/deshabilita botón de adjuntos del asistente |
| src/test/java/.../service/agente | Pruebas de DTO, roles, transacciones, concurrencia y proveedor simulado |

Las rutas Java abreviadas de la tabla están bajo src/main/java/com/skillbridge/ai/. El manifiesto del ZIP enumera las rutas completas y hashes antes/después.

## Base de datos y despliegue
Las tablas nuevas son agente_confirmaciones, agente_limites, agente_auditoria, agente_mutex y agente_alcances. En desarrollo el inicializador ejecuta el SQL idempotente. En producción se propone ejecutarlo previamente con cuenta de migración y usar agente.init-schema=false. Otros inicializadores originales de la aplicación siguen existiendo y requieren una revisión independiente de los permisos DDL del despliegue.

AGENTE_ENABLED=true activa el agente. ANTHROPIC_API_KEY y CLAUDE_MODELOS son la configuración existente. Debe comprobarse que el modelo esté disponible para la cuenta; no se han usado credenciales reales. La versión de API conservada es 2026-10-21.

El perfil prod exige APP_BASE_URL, DB_URL, DB_USERNAME y DB_PASSWORD, activa cookie Secure y desactiva creación automática de tablas de sesión. DB_URL debe configurar TLS con validación de certificado apropiada a tu MySQL (por ejemplo sslMode=VERIFY_IDENTITY y truststore adecuado); no basta copiar una URL local con useSSL=false. El servicio debe exponerse por HTTPS. No se incluyen secretos ni se modificó el despliegue remoto.

Planificar retención y limpieza de propuestas caducadas según política interna. No eliminar auditoría sin una política aprobada. Mantener copias de seguridad antes de aplicar el parche.

## Validación y limitaciones del entorno
La batería automatizada usa Java 17, H2 en modo MySQL para persistencia auxiliar y HTTP simulado para el proveedor. No equivale a arrancar toda la aplicación contra MySQL real ni a una prueba de extremo a extremo en navegador. El informe de pruebas entregado detalla resultados. La guía incluye un checklist para realizar esas comprobaciones con las credenciales de tu entorno.

## Fuentes del protocolo
- https://platform.claude.com/docs/en/agents-and-tools/tool-use/handle-tool-calls
- https://platform.claude.com/docs/en/api/migration-2026-10-21
