# SkillBridge AI — Frontend (HTML / CSS / JS)

Frontend estático y navegable de SkillBridge AI, construido a partir de los 36 mockups
generados en Claude Design. Sirve para presentar el proyecto (TEL137 – Gestión de
Servicios de TICS) y como base para migrar después a Spring Boot + Thymeleaf.

## Cómo abrirlo

No requiere instalación. Dos formas de verlo:

1. **Directo**: abre `index.html` con doble clic en tu navegador.
2. **Con servidor local** (recomendado, evita cualquier restricción de `file://`):

   ```bash
   cd skillbridge-ai-frontend
   python3 -m http.server 8000
   # abre http://localhost:8000
   ```

## Flujo de navegación

`index.html` es la pantalla de inicio: presenta la marca y **4 botones para elegir el
rol** (Colaborador, Project Manager, Resource Manager, Administrador) como atajo directo
a cada dashboard, además de los enlaces reales **"Iniciar sesión"** y **"Crear cuenta"**.
Desde el dashboard, la barra lateral (sidebar) de cada rol navega entre sus propias
pantallas; el buscador de la barra superior filtra en vivo; la campana y el avatar
llevan a notificaciones y "Mi cuenta"; "Cerrar sesión" limpia la sesión y vuelve a
`index.html`.

`pages/auth/login.html`, `registro.html` y `restablecer-password.html` son un
**frontend hardcodeado**: no hay backend, pero el login valida de verdad contra una
lista fija de usuarios de prueba (ver tabla de credenciales abajo), el registro valida
todos sus campos, y "Olvidé mi contraseña" simula el envío y cambio de clave. Todo
corre en el navegador vía `assets/js/auth.js`.

### Credenciales de demostración (login funcional)

| Correo | Rol | Contraseña |
|---|---|---|
| `mariana.ruiz@nexacorp.com` | Colaborador | `Nexa2026*` |
| `javier.molina@nexacorp.com` | Project Manager | `Nexa2026*` |
| `paula.vega@nexacorp.com` | Resource Manager | `Nexa2026*` |
| `ana.villalba@nexacorp.com` | Administrador | `Nexa2026*` |

La misma contraseña sirve para las 4 cuentas. La pantalla de login también incluye un
panel plegable "Cuentas de demostración" con un botón **Usar** por cuenta que autocompleta
el formulario, para no tener que escribirlas a mano en cada demo. Un correo o contraseña
que no coincidan muestran un error real (`#login-alert`) en vez de dejar pasar cualquier
cosa.

## Estructura del proyecto

```
skillbridge-ai-frontend/
├── index.html                     ← landing / selector de rol
├── assets/
│   ├── css/base.css                ← estilos compartidos (paleta azul marino)
│   ├── fonts/                      ← Inter + JetBrains Mono autohospedadas (.woff2)
│   └── js/
│       ├── app.js                  ← menú, logout, sidebar móvil, toasts, botones simulados
│       ├── auth.js                 ← login/registro/reset "hardcodeados" (sin backend)
│       ├── profile.js              ← "Mi cuenta": guardar datos, cambiar contraseña
│       ├── search.js               ← buscador en vivo de la barra superior
│       └── vendor/lucide.min.js    ← íconos, autohospedados (sin CDN)
└── pages/
    ├── auth/                       ← login, registro, restablecer contraseña
    ├── colaborador/                ← 7 pantallas
    ├── project-manager/            ← 10 pantallas
    ├── resource-manager/           ← 7 pantallas
    └── administrador/              ← 9 pantallas
```

En total son **36 pantallas** (todas las que salieron del canvas de Claude Design),
más `index.html`. Cada rol usa una persona fija para que los datos de ejemplo sean
coherentes entre pantallas:

| Rol | Persona | Home |
|---|---|---|
| Colaborador | Mariana Ruiz | `pages/colaborador/dashboard.html` |
| Project Manager | Javier Molina | `pages/project-manager/dashboard.html` |
| Resource Manager | Paula Vega | `pages/resource-manager/dashboard.html` |
| Administrador | Ana Villalba | `pages/administrador/inicio.html` |

## Qué está realmente interactivo

- **Login** (`pages/auth/login.html`): campos reales, mostrar/ocultar contraseña,
  checkbox "Mantener sesión activa", validación de campo vacío / correo mal escrito,
  validación contra la lista de usuarios de demo, mensaje de error real cuando la
  cuenta o la contraseña no coinciden, y redirección al dashboard del rol correcto al
  autenticar (guardando el rol en `localStorage`).
- **Registro** (`pages/auth/registro.html`): todos los campos son editables (nombres,
  apellidos, correo, área, cargo, contraseña, confirmar contraseña, términos), el
  badge "dominio válido" reacciona mientras escribes el correo, el medidor de
  seguridad de la contraseña se recalcula en vivo (barras, texto y checks reales según
  longitud, mayúsculas+número y símbolos), y el botón de envío valida todo antes de
  simular la creación de cuenta.
- **Restablecer contraseña** (`restablecer-password.html`): "Enviar enlace" valida el
  correo y muestra el estado de "enviado"; "Guardar contraseña" valida longitud y
  coincidencia antes de simular el cambio.
- **Mi cuenta** (las 4 variantes de rol): formulario de datos personales
  totalmente editable (nombre, cargo, teléfono, área), correo y rol bloqueados
  visualmente como "no editable", cambio de contraseña con el mismo medidor de
  fuerza en vivo del registro, 3 interruptores de seguridad que funcionan de verdad
  (encienden/apagan al click), y "Guardar cambios" actualiza en el momento el nombre
  visible en el sidebar y en el chip de usuario de la barra superior — sin recargar
  la página. "Cancelar" restaura los valores originales.
- **Buscador de la barra superior**: en las 33 pantallas de aplicación es un `<input>`
  real (`search.js`) que filtra en vivo sobre datos de ejemplo (colaboradores,
  proyectos, habilidades) y muestra un dropdown categorizado por resultado; `Ctrl/⌘+K`
  lo enfoca y `Esc` lo cierra — igual que el patrón de QuintaOla que sirvió de
  referencia, pero con datos y estilo propios de SkillBridge AI.
- **Scroll real**: el layout ya no fuerza `height:100vh` sobre el contenido — el
  sidebar y la barra superior quedan fijos (`position: sticky`) mientras el contenido
  central se desplaza de forma normal en pantallas donde el contenido excede el alto
  visible (se validó con Playwright en varias resoluciones).
- **Botones de acción que antes eran solo texto/imagen** ("Rebalancear", "Aprobar
  excepción", "Continuar con SSO corporativo", filtros tipo "Estado: todos ▾",
  "+ Nueva asignación", "Publicar", etc. — 40+ elementos en total) ahora son
  `<button>` reales: se pueden enfocar con teclado, tienen estado `:hover`/`:active`,
  y al hacer click muestran una notificación ("toast") confirmando la acción, ya que
  todavía no existe backend real. Las 5 tarjetas de acceso rápido del panel de
  Administrador ("Gestionar usuarios →", "Abrir catálogo →", etc.) navegan de verdad
  a su pantalla correspondiente. El botón "▲ Útil (N)" de los hilos de foro suma un
  voto real al hacer click.
- La barra lateral y la barra superior (campana, menú de usuario) de las 33 pantallas
  de aplicación están completamente cableadas: cada link apunta a un archivo real del
  mismo rol. En pantallas angostas (< 960px) aparece un botón de menú (☰) que abre el
  sidebar como panel deslizante con fondo oscurecido — se cierra tocando fuera de él.
- Dos recorridos "maestro-detalle" quedaron conectados como ejemplo del patrón: en
  **Proyectos** (Project Manager), la fila "Núcleo Retail" abre su detalle; en
  **Foros**, la fila "Migración a Java 21" abre el hilo. El breadcrumb de esas
  pantallas de detalle regresa al listado.
- El resto de tarjetas/filas dentro del contenido (otros proyectos, otros hilos,
  candidatos de AI Talent Matching, etc.) se dejaron como referencia visual estática:
  cablear cada una habría significado inventar destinos que no estaban en los
  mockups originales. Es el siguiente paso natural una vez este frontend se conecte
  a datos reales.

## Responsive

El layout se adapta a pantallas más angostas (tablet y celular), no solo a escritorio:

- Por debajo de 960px el sidebar deja de ocupar espacio fijo y se convierte en un
  panel deslizante (`position: fixed`) que se abre con el botón de menú de la barra
  superior; las tarjetas de estadísticas pasan de 3–4 columnas a 2.
- Por debajo de 640px las tarjetas y formularios pasan a una sola columna, el
  buscador ocupa el ancho completo debajo del título, y el panel de "Mi cuenta" apila
  la columna lateral sobre el contenido principal en vez de ponerlas una junto a otra.
- Las tablas anchas (mapa de carga por semana, asignaciones) conservan su propio
  scroll horizontal en vez de romper el diseño de la página.

## Tipografía y assets: sin dependencias externas

**Inter** (texto general) y **JetBrains Mono** (datos técnicos, etiquetas en
mayúscula) están autohospedadas como archivos `.woff2` dentro de `assets/fonts/`, y
los íconos (librería **Lucide**) están autohospedados en
`assets/js/vendor/lucide.min.js`. Nada del frontend depende de Google Fonts ni de un
CDN: el sitio se ve exactamente igual con o sin conexión a internet, incluso abierto
directo con `file://`.

## Huecos conocidos (heredados del set de mockups)

- **Colaborador → "Mis proyectos"**: el mockup original no incluyó una pantalla propia
  para este ítem del menú; hoy apunta al dashboard, que ya muestra los proyectos del
  colaborador. Sugerido: diseñar una vista de listado dedicada si el curso la pide
  como pantalla separada.
- **Resource Manager → "Colaboradores"**: mismo caso; apunta a "Ocupación del equipo"
  porque no existe una pantalla de directorio de colaboradores independiente en el
  set generado.

Ninguno de los dos rompe la navegación — simplemente reutilizan la pantalla más
cercana en contenido hasta que se diseñe la definitiva.

## Ruta sugerida hacia Spring Boot

La carpeta ya está organizada para que la migración sea mecánica:

1. Mueve todo `assets/` a `src/main/resources/static/assets/`.
2. Convierte cada archivo de `pages/<rol>/*.html` en una plantilla Thymeleaf dentro de
   `src/main/resources/templates/<rol>/`. El sidebar y el topbar de cada página son
   bloques casi idénticos entre sí (solo cambian el rol activo, el título y las
   migas de pan) — son buenos candidatos para extraer como fragmentos Thymeleaf
   (`th:fragment`) reutilizables en vez de repetirlos en cada plantilla.
3. Reemplaza los datos de ejemplo hardcodeados (nombres, porcentajes, tablas) por
   `th:text` / `th:each` alimentados desde los controladores.
4. `assets/js/auth.js` es el simulacro completo de autenticación (usuarios y
   contraseña hardcodeados, `localStorage.skillbridge_role` como "sesión"); en Spring
   Security se reemplaza el bloque de `USERS`/`DEMO_PASSWORD` por el
   `AuthenticationManager` real, el rol se lee del `Authentication` en cada request, y
   `sec:authorize` en las plantillas Thymeleaf muestra/oculta secciones del menú según
   el rol. El formulario de login ya está listo para apuntar su `action` a un
   `/login` real de Spring Security en vez de a la validación en JS.
5. `assets/js/search.js` filtra sobre arreglos hardcodeados
   (`COLABORADORES`, `PROYECTOS`, `HABILIDADES`); al migrar, ese filtrado pasa a un
   endpoint `/api/buscar?q=...` consultado con `fetch` (o `th:each` si se resuelve
   server-side), manteniendo el mismo marcado del dropdown.
6. Las rutas relativas (`../../assets/...`, `dashboard.html`, etc.) deberán pasar a
   rutas de la aplicación (`/colaborador/dashboard`, `@{/assets/...}`) al montarlas
   en un `@Controller`.

## Paleta

Azul marino como color dominante (`#0B1F3A` sidebar / fondo de login, `#12294D` y
`#1B355C` como variantes), acento en azul cielo (`#5B8DEF`) y azul medio (`#2C5BB0`)
para links y estados activos, con tipografía **Inter** (texto general) y
**JetBrains Mono** (etiquetas técnicas), autohospedadas — ver sección anterior.
