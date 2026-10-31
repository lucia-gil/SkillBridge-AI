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
**API de Mistral** (un proveedor de modelos de lenguaje, como ChatGPT o
Gemini) y devuelve su respuesta real.

## 2. Por qué Mistral

Se eligió Mistral por tener una capa gratuita generosa (1 solicitud/seg,
500 000 tokens/min, hasta 1000 millones de tokens al mes), suficiente para
una demo académica, sin necesidad de tarjeta de crédito para empezar.
Fuente: https://help.mistral.ai/en/articles/225174-what-are-the-limits-of-the-free-tier

## 3. El mismo patrón visto en clase

Técnicamente es exactamente lo que se vio en "Consumo de Servicios Web con
Spring Boot" (verifica.id / api.atenea): una solicitud `POST` con la
cabecera `Authorization: Bearer {TU-API-KEY}` y un cuerpo en JSON, que
responde con JSON. Aquí el "servicio externo" es la IA de Mistral en vez de
un verificador de DNI/RUC:

```
POST https://api.mistral.ai/v1/chat/completions
Authorization: Bearer {MISTRAL_API_KEY}
Content-Type: application/json

{
  "model": "mistral-small-latest",
  "messages": [
    { "role": "system", "content": "..." },
    { "role": "user", "content": "pregunta del usuario" }
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
        │  POST a Mistral con RestClient
        ▼
API de Mistral  →  responde JSON con la respuesta generada
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
  Arma la solicitud a Mistral, la envía con `RestClient` (clase de Spring
  Boot 3.2+ para consumir servicios REST, el reemplazo moderno de
  `RestTemplate`), y procesa la respuesta JSON. Si la clave no está
  configurada, o Mistral responde con error (401 clave inválida, 429 límite
  de la capa gratuita alcanzado), devuelve un mensaje de error amigable en
  vez de romper la página.

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
  Se agregó la línea `mistral.api.key=${MISTRAL_API_KEY:}`, siguiendo el
  mismo patrón que ya usaban para la contraseña del correo
  (`spring.mail.password=${MAIL_PASSWORD}`): el valor real nunca va en este
  archivo (que sí se sube a GitHub), sino que se lee de una variable de
  entorno o de `secretos.properties`.

- **`src/main/resources/secretos.properties.example`**
  Se agregó la línea `MISTRAL_API_KEY=tu_clave_de_mistral` como plantilla,
  para que cualquiera que clone el repo sepa que debe configurarla.

## 7. Cómo quedó guardada la clave real (importante, léanlo)

La clave real **nunca se sube a GitHub**. Vive en dos lugares, nunca en el
código:

1. **En desarrollo local (tu máquina):** archivo
   `src/main/resources/secretos.properties` (está en `.gitignore`, cada
   integrante del equipo debe crear el suyo copiando
   `secretos.properties.example` y poniendo su propia clave, o la misma que
   ya se generó si la comparten por un canal privado).
2. **En el servidor EC2 (producción):** variable de entorno
   `MISTRAL_API_KEY`, exportada antes de levantar la aplicación:
   ```bash
   export MISTRAL_API_KEY='tu_clave_aqui'
   nohup java -jar app.jar --spring.datasource.url="..." > app.log 2>&1 &
   ```
   (si no se exporta esta variable antes del `nohup`, el asistente
   responderá "El Asistente IA todavía no está configurado en este
   servidor" en vez de fallar feo).

## 8. Cómo probarlo

1. Asegúrate de tener `src/main/resources/secretos.properties` con tu
   `MISTRAL_API_KEY`.
2. Corre la aplicación normalmente (`mvn spring-boot:run` o desde IntelliJ).
3. Entra con cualquier cuenta (Colaborador, PM, RM o Administrador) a
   **Asistente IA** en el sidebar.
4. Escribe una pregunta cualquiera y espera la respuesta — debería tardar
   1-3 segundos en contestar con un mensaje real generado por la IA.

## 9. Limitaciones actuales (para que no sorprendan en la sustentación)

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

## 10. Posibles siguientes pasos (si da tiempo)

- Guardar el historial de conversaciones en una tabla nueva
  (`asistente_mensaje`), para que el panel "Historial" sea real.
- Pasarle al `system prompt` datos reales del usuario (sus proyectos
  activos, sus habilidades) para que las respuestas sean más útiles y
  específicas — esto es lo que haría que las tarjetas de "Contexto cargado"
  dejen de ser mock.
- Reusar el mismo patrón (`RestClient` + Mistral) para generar
  automáticamente la **explicación de las recomendaciones del Talent
  Matching** (tabla `recomendaciones_ia_log`, columna `explicacion`) y el
  **resumen automático de hilos del foro** (historia US-6.2 del backlog).
