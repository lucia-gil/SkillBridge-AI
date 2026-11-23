# Consulta de DNI con Consulta Perú

Este ZIP actualiza **SkillBridge-AI-nube (3)**. Extrae los archivos sobre la raíz del proyecto y conserva la estructura de carpetas.

## Instalación

1. Aplica `db/migracion_identidad_dni.sql` sobre la base MySQL existente de SkillBridge. En una instalación nueva, carga primero el esquema del proyecto y su migración administrativa.
2. En el entorno **del servidor**, configura `CONSULTA_PERU_API_KEY` con la clave de Consulta Perú y `IDENTIDAD_DNI_CLAVE` con un secreto distinto de 32 bytes en Base64. Puedes generar el segundo con `openssl rand -base64 32`. Conserva esa clave de identidad en todos los reinicios y compártela solo entre las instancias del mismo despliegue. Los vínculos y las firmas dependen de ella.
3. Configura `APP_BASE_URL` con la URL HTTPS que usan los destinatarios para abrir el portal. Comprueba el SMTP configurado, compila y reinicia la aplicación.
4. Reenvía desde **Administrador → Usuarios y roles → Invitaciones y accesos** las invitaciones pendientes que se enviaron antes de la actualización. El enlace firmado vence a los siete días. Al reenviar, se reemplaza el enlace anterior.

El servidor usa el contrato de [Consulta Perú](https://consultaperuapi.com/). En los logs solo se registran estados técnicos generales del proveedor. El registro se habilita con la configuración de identidad y consulta activa.


## Uso por rol

- **Administrador.** Consulta un DNI individual en Usuarios y roles. Asocia un DNI al invitar o desde una invitación pendiente. En Proyectos, el detalle de Equipo muestra el estado de comprobación. Para cuentas previas, usa **Vincular DNI** en la fila correspondiente. El servidor exige coincidencia entre el nombre de la cuenta y los datos recibidos.
- **Persona invitada.** Abre el enlace firmado, escribe el DNI y completa el registro. Si el administrador asoció un DNI, solamente se acepta ese documento. El sistema impide vincularlo con dos cuentas.
- **Mi cuenta.** En los cuatro roles se muestra el propio DNI enmascarado y la fecha de comprobación. Una persona con un DNI asociado puede volver a comprobarlo.
- **Resource Manager y Project Manager.** Pueden ver el estado de comprobación en la ficha, directorio o equipo de proyecto según los permisos de cada vista. El estado no modifica la elegibilidad ni la asignación de personas.
- **Auditoría.** Registra la acción, el actor y la cuenta o invitación cuando corresponde. La aplicación conserva una huella autenticada y los últimos cuatro dígitos para las cuentas vinculadas. Los listados, proyectos y auditoría no muestran el DNI completo ni el domicilio que pueda devolver el proveedor.
