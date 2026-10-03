# Desarrollo Android

## Inicio de sesión temporal

La primera vez, la app permite crear una cuenta local con correo y contraseña. La contraseña se transforma en un hash con sal aleatoria; la cuenta y la sesión se cifran con una clave de Android Keystore. Al cerrar sesión, la cuenta local permanece para volver a iniciar sesión.

Esta cuenta solo existe en el teléfono donde se creó. Por ahora la app no se conecta a Neon, no sincroniza datos entre dispositivos y no autentica acceso remoto. La APK no contiene la cadena de conexión de PostgreSQL ni claves administrativas.

Antes de habilitar acceso remoto o sincronización, definir un servicio confiable que verifique la identidad y emita credenciales verificables por Neon RLS. La decisión está registrada en `../05_Decisiones_pendientes.md`.
