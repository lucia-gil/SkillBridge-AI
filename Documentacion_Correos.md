# Correos de SkillBridge AI

Mapa completo de **qué correo llega, a quién y cuándo**, revisado contra el código (`notificacionService.crear(...)`
y `emailService.enviar(...)`). Si agregas un aviso nuevo, agrégalo también aquí.

## Cómo funciona

- **Dos caminos:**
  1. **Aviso en la campana + correo** (`NotificacionService.crear`): la mayoría. Se guarda la notificación en la app
     y, según su tipo, también se manda por correo.
  2. **Solo correo** (`EmailService.enviar`): cuando la persona todavía no tiene cuenta o no puede entrar
     (invitación, recuperar contraseña, cuenta suspendida/reactivada).
- **¿Se puede apagar?** Tres tipos tienen preferencia en **Mi cuenta → Notificaciones** ("también por correo"):
  `asignacion` → *Alertas críticas*, `solicitud` → *Solicitudes de aprobación*, `resultado_ia` → *Resultados de IA*.
  Vienen **activados por defecto**. Todos los demás tipos **siempre** mandan correo.
- **Envío:** en segundo plano (la pantalla no espera a Gmail) y **después de que se guarda el cambio**: si la acción
  falla y se revierte, no sale el correo. Un error de SMTP no rompe nada; queda en el log.
- **Diseño:** plantilla HTML con el logo incrustado en el propio correo (se ve aunque la app no tenga dominio con
  HTTPS), categoría, título, detalle, botón con enlace directo a la pantalla de cada rol, versión en texto plano y
  pie que explica cómo apagar los avisos cuando se puede.
- **Remitente y asunto:** "SkillBridge AI" `<spring.mail.username>`; el asunto es el título del aviso.
- **Enlaces:** se arman con `app.base-url` + la vista del rol de quien recibe (por ejemplo `/colaborador/proyectos.html`).

## Cuándo llega correo

### Cuenta y acceso

| Evento | Quién recibe | Tipo | ¿Correo? | Botón | Código |
|---|---|---|---|---|---|
| Un administrador invita un correo | La persona invitada | solo correo | Siempre | Crear mi cuenta | `UsuarioService.invitar` |
| Crear la cuenta (registro) | La persona registrada | `info` | Siempre | Ir a SkillBridge AI | `AuthService.registrar` |
| Pedir "¿Olvidaste tu contraseña?" | Dueño del correo (si existe) | solo correo | Siempre | Restablecer contraseña (válido 30 min, un uso) | `AuthService.solicitarRecuperacion` |
| Suspender una cuenta | La persona suspendida | solo correo | Siempre (con motivo) | — | `UsuarioService.cambiarEstado` |
| Reactivar una cuenta | La persona reactivada | solo correo | Siempre | Iniciar sesión | `UsuarioService.cambiarEstado` |
| Cambiar el rol organizacional | La persona | `info` | Siempre | — | `UsuarioService.cambiarRol` |

### Proyectos y asignaciones (`asignacion`: se puede apagar)

| Evento | Quién recibe | Código |
|---|---|---|
| Crear un proyecto | El PM elegido ("Nuevo proyecto asignado") | `ProyectoService.crear` |
| Asignar a un colaborador | El colaborador | `ProyectoService.asignarColaborador` |
| Cambiar la dedicación (Ajustar carga) | El colaborador | `ProyectoService.ajustarDedicacion` |
| Finalizar una asignación | El colaborador | `ProyectoService.finalizarAsignacion` |
| Pausar, cancelar o completar el proyecto | Cada colaborador que sale ("Saliste de un proyecto") y el PM | `ProyectoService` (cambio de estado) |
| Cambiar el PM (RM) o transferir el liderazgo (PM) | El PM saliente (sigue como colaborador o sale) y el nuevo PM | `ProyectoService.cambiarLiderazgo` |

### Propuestas de AI Talent Matching

| Evento | Quién recibe | Tipo | ¿Se puede apagar? |
|---|---|---|---|
| El PM propone un candidato (manual o con IA) | Todos los Resource Managers | `solicitud` | Sí |
| El RM aprueba o rechaza la propuesta | Quien la pidió | `resultado_ia` | Sí |
| Una propuesta se cierra sola (el proyecto se canceló, pausó, etc.) | Quien la pidió | `resultado_ia` | Sí |

### Entregables, foros, calendario y mensajes (siempre llegan)

| Evento | Quién recibe | Tipo |
|---|---|---|
| Se publica un entregable nuevo | Los colaboradores del proyecto | `entregable_nuevo` |
| El PM revisa una entrega | Quien entregó | `entregable_revisado` |
| Se abre un hilo nuevo en el foro | El equipo del proyecto (menos el autor) | `info` |
| Alguien responde tu hilo | El autor del hilo | `foro_respuesta` |
| Alguien responde tu mensaje (cita) | El autor del mensaje citado | `foro_cita` |
| Tu respuesta se marca como solución | El autor de la respuesta | `foro_respuesta` |
| Se crea un evento en el calendario de un proyecto | El equipo, según la audiencia del evento (menos quien lo crea) | `info` |
| Mensaje directo nuevo | La otra persona, **solo con el primer mensaje sin leer** (no uno por mensaje) | `info` |
| Se elimina un proyecto cancelado | Todos los Resource Managers | `info` |

## Cuándo NO llega correo

Estos casos hoy no generan aviso ni correo (algunos solo se ven en la app):

| Caso | Qué pasa hoy | ¿Conviene agregarlo? |
|---|---|---|
| Un colaborador **entrega** un entregable | El PM no recibe aviso; lo ve en Entregables | Sí: aviso al PM |
| Cambiar la contraseña (Mi cuenta o con el enlace) | Sin correo de confirmación | Sí: correo de seguridad ("tu contraseña cambió") |
| Activar o desactivar la verificación en dos pasos (2FA) | Sin correo | Sí, por seguridad |
| Mensajes en el **chat de un proyecto** | Solo el contador de no leídos | No: sería demasiado correo |
| Editar o eliminar un evento | Sin aviso | Opcional |
| Eventos de la agenda personal del RM | Sin aviso (solo los ve él) | No |
| Reanudar un proyecto en pausa | Sin aviso | Opcional |
| Editar los datos de un proyecto | Sin aviso | No |
| Acciones del Asistente IA | Avisan las mismas acciones de negocio que ejecuta (p. ej. proponer un candidato avisa al RM) | — |
| Preferencia "Resumen semanal" | Se guarda en Mi cuenta, pero **todavía no se envía** ningún resumen | Implementarlo o quitar la opción |

## Probar los correos sin Gmail

Con [Mailpit](https://mailpit.axllent.org/) (servidor de correo de prueba) se ven los correos tal como llegan, logo
incluido:

```bash
docker run -d --name mailpit -p 1025:1025 -p 8025:8025 axllent/mailpit
```

```bash
java -jar target/skillbridge-ai.jar --spring.mail.host=localhost --spring.mail.port=1025 --spring.mail.properties.mail.smtp.auth=false --spring.mail.properties.mail.smtp.starttls.enable=false --app.base-url=http://localhost:8080
```

Luego abre `http://localhost:8025`.
