# Correos de SkillBridge AI

Mapa completo de **qué correo llega, a quién y cuándo**, revisado contra el código (`notificacionService.crear(...)`
y `emailService.enviar(...)`). Si agregas un aviso nuevo, agrégalo también aquí.

## Cómo funciona

- **Dos caminos:**
  1. **Aviso en la campana + correo** (`NotificacionService.crear`): la mayoría. Se guarda la notificación en la app
     y, según su tipo, también se manda por correo.
  2. **Solo correo** (`EmailService.enviar`): cuando la persona todavía no tiene cuenta o no puede entrar
     (invitación, recuperar contraseña, cuenta suspendida/reactivada).
- **¿Se puede apagar?** Cuatro avisos tienen preferencia en **Notificaciones → Preferencias de aviso**:
  `asignacion` → *Alertas críticas*, `solicitud` → *Solicitudes de aprobación*, `resultado_ia` → *Resultados de IA*
  y el *Resumen semanal*. Vienen **activados por defecto**. Los canales `app_mail`, `mail` y `solo_mail` cuentan
  como "con correo"; `app` y `solo_app`, como "sin correo". Todos los demás tipos **siempre** mandan correo.
- **Envío:** en segundo plano (la pantalla no espera a Gmail) y **después de que se guarda el cambio**: si la acción
  falla y se revierte, no sale el correo. Un error de SMTP no rompe nada; queda en el log.
- **Diseño:** plantilla HTML (`EmailService.html`) con el logo incrustado en el propio correo (se ve aunque la app no
  tenga dominio con HTTPS), cabecera azul de SkillBridge, saludo con el nombre ("Hola, Ana:"), título, detalle,
  botón con enlace directo a la pantalla de cada rol, versión en texto plano y pie que explica cómo apagar los avisos
  cuando se puede. Se adapta a celular (ancho máximo 580 px).
- **Color e ícono por categoría:** la franja, el ícono, la etiqueta, el borde del detalle y el botón usan el mismo
  color. Los íconos son los mismos de la app (`static/img/icons.svg`), guardados como PNG en `correo/iconos/`
  (Gmail no muestra SVG) y adjuntos dentro del correo como el logo. Sin emojis.

  | Categoría | Color | Ícono (`icons.svg`) | Archivo |
  |---|---|---|---|
  | Proyectos y asignaciones | Azul `#2563eb` | folder | `proyecto.png` |
  | Resumen semanal | Azul `#2563eb` | bar-chart | `resumen.png` |
  | Cuenta | Azul `#2563eb` | user | `cuenta.png` |
  | Foros | Violeta `#7c3aed` | message | `foro.png` |
  | Mensajes | Turquesa `#0d9488` | mail | `mensaje.png` |
  | Invitación | Turquesa `#0d9488` | users | `invitacion.png` |
  | Entregables | Ámbar `#d97706` | file-text | `entregable.png` |
  | Calendario | Verde `#16a34a` | calendar | `calendario.png` |
  | Solicitudes de aprobación | Índigo `#4f46e5` | user-check | `solicitud.png` |
  | AI Talent Matching | Morado `#9333ea` | sparkles | `matching.png` |
  | Seguridad de tu cuenta | Rojo `#dc2626` | lock | `seguridad.png` |
  | Alerta | Rojo `#dc2626` | alert-triangle | `alerta.png` |
  | Administración | Gris pizarra `#475569` | shield | `administracion.png` |
  | Cualquier otro aviso | Azul `#2563eb` | bell | `aviso.png` |
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
| Un colaborador entrega o reenvía un entregable | El/los PM del proyecto | `entregable_entregado` |
| El PM revisa una entrega | Quien entregó | `entregable_revisado` |
| Se abre un hilo nuevo en el foro | El equipo del proyecto (menos el autor) | `info` |
| Alguien responde tu hilo | El autor del hilo | `foro_respuesta` |
| Alguien responde tu mensaje (cita) | El autor del mensaje citado | `foro_cita` |
| Tu respuesta se marca como solución | El autor de la respuesta | `foro_respuesta` |
| Se crea un evento en el calendario de un proyecto | El equipo, según la audiencia del evento (menos quien lo crea) | `info` |
| Mensaje directo nuevo | La otra persona, **solo con el primer mensaje sin leer** (no uno por mensaje) | `info` |
| Se elimina un proyecto cancelado | Todos los Resource Managers | `info` |

### Administración (respeta *Alertas críticas* de cada administrador)

| Evento | Quién recibe | Código |
|---|---|---|
| Alguien crea su cuenta o usa una invitación | Todos los administradores activos | `AuthService.registrar`, `UsuarioService` |
| Se cambia el rol de una cuenta | Todos los administradores activos | `UsuarioService.cambiarRol` |
| Se suspende o reactiva una cuenta | Todos los administradores activos | `UsuarioService.cambiarEstado` |
| Inicio de sesión en Administración (después del 2FA) | El propio administrador | `SesionSegura` |

### Resumen semanal (se puede apagar)

Cada **lunes a las 8:00 (hora de Perú)**, a cada cuenta activa que no lo apagó (`ResumenSemanalService`):

| Rol | Contenido |
|---|---|
| Colaborador | Carga actual, proyectos activos, entregables que cierran en 7 días (sin entregar) y agenda de sus proyectos |
| Colaborador que lidera proyectos | Lo anterior, más entregas por revisar y vacantes de sus proyectos |
| Resource Manager | Solicitudes por resolver, banca, sobreasignados, proyectos por iniciar con vacantes, quién se libera pronto y su agenda (eventos propios e hitos) |
| Administrador | Cuentas y proyectos nuevos en 7 días, invitaciones sin usar, cuentas activas y suspendidas, proyectos en curso y en pausa, propuestas pendientes |

Todos incluyen cuántos avisos y mensajes tiene sin leer, y el botón **Ir a mi Inicio**. Con
**"Enviarme el resumen ahora"** (Notificaciones) se recibe al momento para revisarlo. Se configura con
`RESUMEN_SEMANAL_CRON` (formato cron de Spring) y se apaga para todos con `RESUMEN_SEMANAL_HABILITADO=false`.
Está pensado para una sola instancia de la app: con varias, cada una lo enviaría.

## Cuándo NO llega correo

Estos casos hoy no generan aviso ni correo (algunos solo se ven en la app):

| Caso | Qué pasa hoy | ¿Conviene agregarlo? |
|---|---|---|
| Cambiar la contraseña (Mi cuenta o con el enlace) | Sin correo de confirmación | Sí: correo de seguridad ("tu contraseña cambió") |
| Activar o desactivar la verificación en dos pasos (2FA) | Sin correo | Sí, por seguridad |
| Mensajes en el **chat de un proyecto** | Solo el contador de no leídos | No: sería demasiado correo |
| Editar o eliminar un evento | Sin aviso | Opcional |
| Eventos de la agenda personal del RM | Sin aviso (solo los ve él) | No |
| Reanudar un proyecto en pausa | Sin aviso | Opcional |
| Editar los datos de un proyecto | Sin aviso | No |
| Acciones del Asistente IA | Avisan las mismas acciones de negocio que ejecuta (p. ej. proponer un candidato avisa al RM) | — |

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
