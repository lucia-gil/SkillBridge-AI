# Aplicación del ZIP de actualización

Este ZIP es un parche sobre SkillBridge-AI-nube.zip entregado por el usuario. Contiene solo archivos modificados o nuevos; no es un proyecto autónomo y no incluye target, secretos ni dependencias descargadas.

1. Haz una copia de seguridad de tu proyecto y base de datos.
2. Descomprime el ZIP de actualización en una carpeta temporal. La raíz del ZIP corresponde a la carpeta donde está tu pom.xml: copia su contenido allí conservando subcarpetas.
3. Revisa MANIFIESTO_CAMBIOS.json. Los hashes originales permiten detectar si tu proyecto cambió desde el ZIP base; si no coinciden, combina los cambios manualmente en lugar de sobrescribirlos.
4. Con Java 17, ejecuta `mvn clean test package`. Las pruebas no necesitan credenciales externas. Mockito requiere que el entorno permita adjuntar su agente Java.
5. En desarrollo, el esquema auxiliar se crea automáticamente. En producción ejecuta `src/main/resources/db/agente.sql` con la cuenta de migración antes de arrancar y define `agente.init-schema=false`. No se modifican ni eliminan tablas existentes por este script.
6. Configura las variables de MySQL y correo ya requeridas por el proyecto y ANTHROPIC_API_KEY / CLAUDE_MODELOS. Nunca copies claves dentro del ZIP. Verifica que el modelo elegido esté disponible para tu cuenta.
7. Activa el perfil prod solamente con HTTPS y las variables APP_BASE_URL, DB_URL, DB_USERNAME y DB_PASSWORD correctas. DB_URL debe verificar TLS y certificados. El perfil activa cookies Secure. Revisa también los inicializadores originales que siguen ejecutándose.
8. Reinicia la aplicación. Entra al asistente con cada rol y sigue docs/Guia_Pruebas_Agente_IA.pdf. Una escritura debe mostrar datos exactos y exigir pulsar Confirmar cambio. Un «sí» en el chat no confirma una operación.
9. Verifica auditoría, aislamiento, caducidad y doble confirmación. Las pruebas locales son automatizadas con H2 y proveedor simulado, no una certificación del entorno MySQL/Anthropic real.

## Cambios visibles
El agente funciona con el mensaje actual y permite consultas y propuestas por rol. No acepta adjuntos. El historial anterior a este parche no tiene huella de permisos y queda oculto/inaccesible sin borrarse; también se invalida historial cuando cambian las asignaciones del actor. La biografía se edita por separado del resumen IA validado. No se agregan empleos externos ni entrevistas.

## Desactivar o volver atrás
AGENTE_ENABLED=false deshabilita el agente y la confirmación; no restaura el chat antiguo. Para revertir código, restaura tu copia de seguridad anterior y recompila. Las tablas agente_* son aditivas y pueden conservarse para auditoría. No las elimines sin evaluar retención y respaldo. Las acciones de negocio ya confirmadas no se deshacen por revertir código.
