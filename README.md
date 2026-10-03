# Desarrollo Android

## Neon Auth y Neon Data API

La app usa pantallas nativas Compose y consume los endpoints administrados de Neon por HTTPS. Las cuentas se crean con el flujo de correo y contraseña de Neon Auth; Neon Auth almacena las credenciales en su esquema administrado y la app conserva únicamente la cookie de sesión cifrada con Android Keystore. No se guarda una contraseña en el teléfono.

Para compilar con los endpoints del proyecto, copia `local.properties.example` como `local.properties` y reemplaza los dos valores por las URLs HTTPS que muestra la consola de Neon:

```properties
NEON_AUTH_BASE_URL=https://<endpoint-de-auth>/<base-de-datos>/auth
NEON_DATA_API_URL=https://<endpoint-de-data-api>/<base-de-datos>/rest/v1
```

Gradle incorpora esas URLs públicas al `BuildConfig` del APK. No agregues `DATABASE_URL`, contraseñas, API keys de Neon, tokens ni secretos a `local.properties`, variables de compilación o al repositorio. Android se comunica con Auth y Data API; no abre conexiones PostgreSQL directas.

Neon Auth y Data API se habilitan por rama en la consola de Neon. La pantalla de inicio permite registrar una cuenta desde la APK cuando Auth esté configurado. Si Neon requiere verificar el correo, el usuario deberá completar esa verificación antes de iniciar sesión. La cuenta de prueba debe crearse desde ese formulario después de configurar Auth; no se crea con SQL ni se incluye su contraseña en código.

`core/network/` contiene la configuración HTTPS, el cliente HTTP de Neon Data API y el contrato para obtener JWT. `feature/auth/data/` contiene el cliente nativo de Neon Auth y la persistencia protegida de la sesión. El cliente Data API adjunta el JWT emitido por Neon Auth; PostgreSQL/RLS aplica la autorización.

El menú inicial ya puede abrirse con una sesión Neon Auth válida. El registro, la persistencia local de movimientos con Room, las consultas financieras, RLS del modelo de negocio y la sincronización offline-first todavía deben implementarse antes de guardar información financiera real.
