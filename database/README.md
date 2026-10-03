# Esquema de Neon

Las migraciones versionadas de la base se mantienen en `database/migrations/`. No incluyen la cadena de conexión, una API key ni contraseñas.

## Primera migración: espacio e integrantes

1. Habilitar Neon Auth y Neon Data API en la rama que usará la APK.
2. Ejecutar `migrations/001_household_access.sql` una vez en el SQL Editor de esa rama.
3. Confirmar que Neon Data API exponga el esquema `public` y refrescar su caché de esquema para que detecte las funciones RPC.
4. Configurar los endpoints HTTPS públicos en `local.properties`, iniciar sesión en la APK y abrir **Usuarios**.
5. La primera cuenta crea el espacio y queda como administradora. Desde allí crea las cuentas adicionales y asigna roles.

La eliminación de un integrante quita su pertenencia al espacio; no borra su identidad de Neon Auth. Las políticas de las tablas financieras futuras deberán comprobar que el usuario sigue perteneciendo al espacio.
