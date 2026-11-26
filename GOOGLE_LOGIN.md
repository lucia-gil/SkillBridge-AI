# Acceso con Google en SkillBridge AI

## Web pública en EC2

Google exige que el retorno OAuth de una aplicación web pública use un dominio con HTTPS. La IP elástica sigue siendo la dirección del servidor. Configura un registro DNS `A` que apunte a ella y usa el dominio para abrir SkillBridge. [Reglas para la URI de retorno](https://developers.google.com/identity/protocols/oauth2/web-server#uri-validation)

1. Usa el `deploy/Caddyfile` incluido con `SKILLBRIDGE_DOMAIN` igual al dominio y el servicio Spring Boot en `127.0.0.1:8080`. Habilita TCP 80 y 443 en el grupo de seguridad y en el firewall para Caddy. El acceso público habitual será por 443. Comprueba que Caddy obtiene el certificado y redirige a HTTPS antes de retirar la entrada pública al puerto 8080.
2. Crea un cliente OAuth de tipo **Aplicación web** en Google Auth Platform. Configura la marca, el dominio y la audiencia según corresponda. Si la aplicación externa sigue en pruebas, agrega los correos que harán la prueba a la lista de usuarios de prueba.
3. Registra la URI de redirección autorizada exacta `https://TU_DOMINIO/auth/google/callback`. Este flujo usa navegación y retorno al servidor, de modo que la configuración del cliente se hace en **URI de redirección autorizados**. Spring solicita los permisos `openid` y `email`. [Configuración de Google OpenID Connect](https://developers.google.com/identity/openid-connect/openid-connect)
4. En una base existente ejecuta `db/migracion_google_login.sql` con el usuario de migraciones de MySQL. El script se puede repetir. Una instalación que importa el `db/skillbridge_db.sql` de esta entrega ya crea la tabla.
5. Configura estas variables en el servicio de la aplicación, junto con las variables de base de datos y correo del despliegue.

   | Variable | Valor |
   | --- | --- |
   | `SPRING_PROFILES_ACTIVE` | `prod` |
   | `APP_BASE_URL` | `https://TU_DOMINIO`, sin barra final |
   | `GOOGLE_CLIENT_ID` | ID de la aplicación web |
   | `GOOGLE_CLIENT_SECRET` | Secreto en el entorno del servidor o gestor de secretos |
   | `GOOGLE_LOGIN_ENABLED` | `true` |

6. Reinicia la aplicación y abre `https://TU_DOMINIO/auth/login.html`. Si necesitas desactivar Google, vuelve a `GOOGLE_LOGIN_ENABLED=false` y reinicia. El acceso por contraseña está disponible en ambos estados.

## Prueba de una cuenta existente

- Registra un correo de prueba por el flujo normal de invitación de SkillBridge e inicia sesión con contraseña. Comprueba su perfil y sus permisos. Cierra sesión.
- Pulsa **Continuar con Google** y selecciona la cuenta con el mismo correo. Con un Gmail verificado o una dirección verificada que Google Workspace administra, el primer acceso vincula el mismo usuario. La comparación es por correo completo y distingue alias como direcciones independientes.
- Si la cuenta de Google usa un correo de otro proveedor, introduce tu contraseña de SkillBridge cuando se te pida. El vínculo se confirma tras el segundo factor si está activo.
- Comprueba que el perfil, las asignaciones y el rol coinciden con el acceso por contraseña. Cierra sesión y repite el acceso con Google. Tras el primer vínculo, la búsqueda usa el identificador estable de Google.
- Prueba con una cuenta suspendida, una invitación aún sin registro y una cuenta de Google con otro correo. El acceso queda bloqueado. Prueba cancelar en Google para confirmar que puedes volver al login y entrar con contraseña.

Para desarrollo, crea un cliente de prueba con la URI `http://localhost:8080/auth/google/callback`, conserva el perfil local y abre la aplicación desde el mismo origen. Google permite HTTP para localhost. [Excepción de Google para localhost](https://developers.google.com/identity/protocols/oauth2/web-server#uri-validation)
