# Asistente IA — cómo se implementó

Este documento explica, para el equipo, cómo se conectó el **Asistente IA**
(el chat que ya existía como maqueta visual en `asistente-ia.html` de los 4
roles) con una inteligencia artificial real, qué archivos se tocaron y por
qué, y qué queda pendiente.

## 1. Resumen

Antes: las 4 páginas `administrador/colaborador/project-manager/
resource-manager/asistente-ia.html` ya tenían toda la interfaz de chat
(mensajes, sugerencias, historial), pero las respuestas eran **guionadas**:
un script en `ai-assistant.js` buscaba el texto del usuario entre una lista
de preguntas/respuestas de ejemplo (`mock-data.js`) y mostraba la respuesta
precargada, con un `setTimeout` para simular que "pensaba".

Ahora: cuando el usuario escribe un mensaje, el navegador le hace una
petición real al backend de Spring Boot, que a su vez le pregunta a la
**API de Google Gemini** y devuelve su respuesta real.

## 2. Por qué Gemini (y no Mistral)

Primero se probó con Mistral, pero su capa gratuita resultó ser solo para
la app de chat de Mistral ("Vibe"), no para la API — usar la API desde
nuestro backend requería activar un plan de pago (Pay-as-you-go o Pro),
aunque sea con uso mínimo. Como el equipo no quería pagar, se cambió a
**Google Gemini**, cuya API sí tiene un nivel gratuito real: modelos de la
familia "Flash" (el que usamos, `gemini-3.8-flash`) son gratuitos y no
piden tarjeta de crédito, solo una cuenta de Google.

## 3. El mismo patrón visto en clase

Técnicamente es lo mismo que se vio en "Consumo de Servicios Web con
Spring Boot" (verifica.id / api.atenea): una solicitud `POST` con la clave
de API y un cuerpo en JSON, que responde con JSON. La única diferencia es
cómo viaja la clave: en vez de la cabecera `Authorization: Bearer`, Gemini
usa la cabecera `x-goog-api-key`.

```
POST https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent
x-goog-api-key: {GEMINI_API_KEY}
Content-Type: application/json

{
  "systemInstruction": { "parts": [ { "text": "..." } ] },
  "contents": [
    { "role": "user", "parts": [ { "text": "pregunta del usuario" } ] }
  ]
}
```

## 4. Flujo completo (quién llama a quién)

```
Usuario escribe en el chat (asistente-ia.html)
        │  fetch POST /api/asistente/mensaje { mensaje: "..." }
        ▼
AsistenteIaController.mensaje()
        │  lee el usuario de la sesión (nombre, rol)
        ▼
AsistenteIaService.responder(sesion, mensaje)
        │  arma el "system prompt" (quién es el usuario, qué puede
        │  responder el asistente) + el mensaje del usuario
        │  POST a Gemini con RestClient
        ▼
API de Gemini  →  responde JSON con la respuesta generada
        │
        ▼
AsistenteIaService la extrae y la devuelve como texto plano
        ▼
AsistenteIaController responde { respuesta: "..." }
        ▼
ai-assistant.js la muestra en el chat
```

## 5. Archivos nuevos

- **`src/main/java/com/skillbridge/ai/service/AsistenteIaService.java`**
  Arma la solicitud a Gemini, la envía con `RestClient` (clase de Spring
  Boot 3.2+ para consumir servicios REST, el reemplazo moderno de
  `RestTemplate`), y procesa la respuesta JSON. Si la clave no está
  configurada, o Gemini responde con error (401/403 clave inválida, 429
  límite de la capa gratuita alcanzado), devuelve un mensaje de error
  amigable en vez de romper la página.

- **`src/main/java/com/skillbridge/ai/controller/AsistenteIaController.java`**
  Expone `POST /api/asistente/mensaje`. Recibe `{ "mensaje": "..." }`,
  valida que no esté vacío ni sea excesivamente largo, llama al servicio y
  devuelve `{ "respuesta": "..." }`. Es un único endpoint para los 4 roles
  porque la respuesta depende de la sesión del usuario, no de la URL.

## 6. Archivos modificados

- **`src/main/resources/static/js/ai-assistant.js`**
  - `sendMessage()`: antes buscaba una respuesta guionada en `mock-data.js`;
    ahora hace `fetch("/api/asistente/mensaje", { method: "POST", ... })` y
    muestra la respuesta real (o un mensaje de error si falla la conexión).
  - `renderConversation()`: antes cargaba una conversación de ejemplo
    completa al abrir la página; ahora solo muestra un saludo inicial,
    porque mostrar una conversación falsa sería confuso ahora que las
    respuestas son reales.
  - Se eliminó `findScriptedReply()` (ya no se usa, era la que buscaba
    coincidencias en las preguntas de ejemplo).
  - El token CSRF (protección de Spring Security contra envíos falsificados)
    ya se agrega automáticamente a cualquier `fetch` del sitio gracias a
    `csrf.js`, que ya existía — no hubo que tocar nada ahí.

- **`src/main/resources/application.properties`**
  Se agregó la línea `gemini.api.key=${GEMINI_API_KEY:}`, siguiendo el
  mismo patrón que ya usaban para la contraseña del correo
  (`spring.mail.password=${MAIL_PASSWORD}`): el valor real nunca va en este
  archivo (que sí se sube a GitHub), sino que se lee de una variable de
  entorno o de `secretos.properties`.

- **`src/main/resources/secretos.properties.example`**
  Se agregó la línea `GEMINI_API_KEY=tu_clave_de_gemini` como plantilla,
  para que cualquiera que clone el repo sepa que debe configurarla.

## 7. Cómo quedó guardada la clave real (importante, léanlo)

La clave real **nunca se sube a GitHub**. Vive en dos lugares, nunca en el
código:

1. **En desarrollo local (tu máquina):** archivo
   `src/main/resources/secretos.properties` (está en `.gitignore`, cada
   integrante del equipo debe crear el suyo copiando
   `secretos.properties.example` y generando su propia clave gratuita en
   https://aistudio.google.com/apikey con su cuenta de Google).
2. **En el servidor EC2 (producción):** variable de entorno
   `GEMINI_API_KEY`, exportada antes de levantar la aplicación:
   ```bash
   export GEMINI_API_KEY='tu_clave_aqui'
   nohup java -jar app.jar --spring.datasource.url="..." > app.log 2>&1 &
   ```
   (si no se exporta esta variable antes del `nohup`, el asistente
   responderá "El Asistente IA todavía no está configurado en este
   servidor" en vez de fallar feo).

## 8. Cómo conseguir tu propia clave de Gemini (gratis, sin tarjeta)

1. Entra a https://aistudio.google.com/apikey con tu cuenta de Google.
2. Acepta los términos (primera vez).
3. Clic en "Create API key" / "Crear clave de API".
4. Copia la clave que empieza con `AIza...` o `AQ...`.
5. Pégala en tu `secretos.properties` local como `GEMINI_API_KEY=...`.

## 9. Cómo probarlo

1. Asegúrate de tener `src/main/resources/secretos.properties` con tu
   `GEMINI_API_KEY`.
2. Corre la aplicación normalmente (`mvn spring-boot:run` o desde IntelliJ).
3. Entra con cualquier cuenta (Colaborador, PM, RM o Administrador) a
   **Asistente IA** en el sidebar.
4. Escribe una pregunta cualquiera y espera la respuesta — debería tardar
   1-3 segundos en contestar con un mensaje real generado por la IA.

## 10. Limitaciones actuales (para que no sorprendan en la sustentación)

- **No hay memoria de la conversación:** cada mensaje se manda solo, sin el
  historial de turnos anteriores. Si el usuario dice "y de los que
  mencionaste antes", la IA no sabrá a qué se refiere.
- **No hay historial guardado en base de datos:** el panel "Historial" de
  la izquierda y las tarjetas de "Contexto cargado" (Portal Andes, perfil de
  habilidades, etc.) siguen siendo datos de ejemplo (mock), no reales. Al
  recargar la página, la conversación se reinicia.
- **No conoce los datos reales del usuario** (sus proyectos, habilidades,
  asignaciones): solo sabe su nombre y su rol. Si le preguntan algo
  específico de sus datos, el asistente está instruido para decir
  honestamente que no tiene acceso a esa información todavía, en vez de
  inventar una respuesta.

## 11. Nuevo: Resumen con IA en los Foros

Usando la misma conexión a Gemini, se agregó un botón **"Resumir con IA"**
en el detalle de cada hilo del foro (`colaborador/foro-hilo.html` y
`project-manager/foro-hilo.html`). Al hacer clic, junta el mensaje inicial
y todas las respuestas del hilo, se lo manda a Gemini pidiendo un resumen
corto (de qué trata, qué se acordó, qué queda pendiente), y **lo guarda en
la base de datos** (columnas nuevas `resumen_ia` y `resumen_ia_generado_en`
en `foro_publicaciones`) para no gastar otra llamada a Gemini cada vez que
alguien abra el hilo — solo se vuelve a generar si alguien hace clic en
"Actualizar resumen".

Archivos nuevos/modificados para esto:

- **`config/InicializadorEsquemaV10.java`** (nuevo): agrega las dos columnas
  nuevas a `foro_publicaciones` automáticamente al arrancar la app (mismo
  patrón que la v9 para el perfil profesional). El equivalente en SQL manual
  está en la sección 6 de `db/skillbridge_db.sql` por si alguien prefiere
  aplicarlo a mano.
- **`model/ForoPublicacion.java`**: agrega los campos `resumenIa` y
  `resumenIaGeneradoEn`.
- **`dto/HiloDetalle.java`**: expone `resumenIa`, `tieneResumenIa` y
  `resumenIaFechaLabel` a la plantilla.
- **`service/AsistenteIaService.java`**: se refactorizó para compartir la
  llamada HTTP a Gemini entre el chat (`responder`) y el nuevo
  `resumirHilo(titulo, contenidoCompleto)`, cada uno con su propio prompt
  de sistema.
- **`service/ForoService.java`**: nuevo método `generarResumenIa(hiloId, ...)`
  que arma el texto completo del hilo, llama a `resumirHilo` y guarda el
  resultado.
- **`controller/ForoHiloBaseController.java`**: nuevo endpoint
  `POST /{base}/foro-hilo/{id}/resumen-ia`.
- **`static/js/foro-hilo.js`** y **`static/css/foros.css`**: lógica y estilo
  de la tarjeta/botón "Resumir con IA".

## 12. El asistente ya no responde fuera de tema

Se descubrió que el chat respondía cualquier pregunta (capitales, cuentas
matemáticas, cultura general) porque el prompt de sistema
(`AsistenteIaService.promptSistema`) no se lo prohibía explícitamente. Se
agregó una sección "ALCANCE" al prompt indicando que solo debe responder
temas de SkillBridge AI (cómo usar la plataforma) y de gestión de
proyectos/desarrollo de software; para cualquier otra cosa, responde
amablemente que no puede ayudar con eso y redirige a lo que sí sabe.

## 13. Correcciones a la vista del chat

- La fecha que aparecía arriba del chat ("Martes 18 de agosto, 2026") estaba
  fija en los datos de ejemplo; ahora `ai-assistant.js` calcula la fecha
  real del día en español.
- Se agregó la etiqueta **"(ejemplo)"** junto a "Historial" y "Contexto
  cargado" en las 4 páginas de Asistente IA, para dejar claro que esos dos
  paneles siguen siendo datos de muestra (ver limitaciones, sección 10) y
  no confundir en la sustentación.

## 14. Posibles siguientes pasos (si da tiempo)

- Guardar el historial de conversaciones en una tabla nueva
  (`asistente_mensaje`), para que el panel "Historial" sea real.
- Pasarle al `system prompt` datos reales del usuario (sus proyectos
  activos, sus habilidades) para que las respuestas sean más útiles y
  específicas — esto es lo que haría que las tarjetas de "Contexto cargado"
  dejen de ser mock.
- Reusar el mismo patrón (`RestClient` + Gemini) para generar
  automáticamente la **explicación de las recomendaciones del Talent
  Matching** (tabla `recomendaciones_ia_log`, columna `explicacion`) y el
  **resumen automático de hilos del foro** (historia US-6.2 del backlog).

## Resiliencia: varios modelos y un proveedor de respaldo

La capa gratuita de Gemini a veces responde **503** (modelo saturado) o
**429** (cuota agotada, que se cuenta por modelo). Para que el asistente y
los resúmenes no fallen al primer error, `AsistenteIaService` prueba una
cadena de proveedores:

1. Los modelos de `gemini.modelos`, en orden (por defecto
   `gemini-3.8-flash`, `gemini-flash-latest`, `gemini-flash-lite-latest`;
   se cambia con la variable `GEMINI_MODELOS`).
2. Un proveedor de respaldo compatible con la API de OpenAI
   (`POST /chat/completions` con `Authorization: Bearer`), solo si existe
   `IA_RESPALDO_API_KEY`. Por defecto Groq (`llama-3.3-70b-versatile`); se
   cambia con `IA_RESPALDO_URL` e `IA_RESPALDO_MODELO` (OpenRouter, Mistral...).

Ante 500/502/503/504 o un timeout, reintenta el mismo modelo dos veces
(1 s y 2,5 s de pausa); ante 429, 404 u otro error pasa directo al
siguiente. Cada fallo queda en el log con el código y el cuerpo de la
respuesta (`Asistente IA: Gemini <modelo> respondio <código> - ...`).
Las llamadas tienen un timeout de 10 s para conectar y 40 s para responder.
