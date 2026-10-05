# Esquema de Neon

Las migraciones versionadas de la base se mantienen en `database/migrations/`. No incluyen la cadena de conexión, una API key ni contraseñas.

## Primera migración: espacio e integrantes

1. Habilitar Neon Auth y Neon Data API en la rama que usará la APK.
2. Ejecutar `migrations/001_household_access.sql` una vez en el SQL Editor de esa rama.
3. Confirmar que Neon Data API exponga el esquema `public` y refrescar su caché de esquema para que detecte las funciones RPC.
4. Configurar los endpoints HTTPS públicos en `local.properties`, iniciar sesión en la APK y abrir **Usuarios**. Las URLs ya están configuradas para la rama de trabajo; falta probar la conexión con una cuenta real.
5. La primera cuenta crea el espacio y queda como administradora. Desde allí crea las cuentas adicionales y asigna roles.

La eliminación de un integrante quita su pertenencia al espacio; no borra su identidad de Neon Auth. Las políticas de las tablas financieras futuras deberán comprobar que el usuario sigue perteneciendo al espacio.

## Migraciones financieras

Las migraciones `002_financial_records.sql` a `005_recurring_rules.sql` crean movimientos/categorías, marcadores de borrado, presupuestos y reglas recurrentes. En el proyecto actual, las migraciones `001`–`005` ya se aplicaron a `production`; falta probar el comportamiento con sesiones reales.

`006_debts.sql` crea **debts** y **debt_payments**, aplica RLS y valida en PostgreSQL que los abonos acumulados no superen el monto inicial. La APK calcula el saldo pendiente como monto inicial menos abonos, sin intereses ni creación de movimientos automáticos. Ejecuta la migración `006` en el SQL Editor de la misma rama Neon y refresca la caché de esquema de Data API antes de instalar una APK que incluya esta sincronización. La migración no incluye credenciales.
