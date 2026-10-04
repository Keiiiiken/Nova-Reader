# Configurar Google Drive para Nova Reader

La app usa Google Identity Services para Android y el permiso `https://www.googleapis.com/auth/drive.appdata`. Guarda ZIP con libros y estado en `appDataFolder`, el espacio privado de Nova Reader en la cuenta elegida. No pide acceso al resto de Drive. La lectura y el diccionario no requieren conexión. Las copias son manuales; no hay sincronización continua ni resolución automática de conflictos entre dispositivos.

## Configuración del propietario

1. Crear un proyecto en https://console.cloud.google.com/ y activar **Google Drive API**.
2. En **Google Auth Platform**, configurar Branding (nombre, correo de soporte), Audience (externa si se distribuye al público) y Data Access. Añadir exclusivamente `drive.appdata`. Mientras esté en modo Testing, registrar las cuentas de prueba autorizadas. Antes de distribuir, completar los requisitos de publicación que indique Google.
3. Crear un cliente OAuth de tipo **Android**. Paquete: `io.luma.reader`. Registrar el SHA-1 del certificado que firma el APK instalado. Para el certificado de la app original adjunta es `9C:B4:12:00:91:89:DB:4E:64:94:3D:20:29:F8:95:31:4F:5A:83:FA`.
4. Firmar Nova Reader con esa misma clave para actualizar la instalación anterior conservando sus datos. El certificado público no permite recuperar la clave privada. Si Google Play vuelve a firmar el APK, registrar también el SHA-1 del certificado de **App signing** mostrado en Play Console. Para compilar y probar en otro ordenador, registrar otro cliente Android con el SHA-1 de su certificado debug.
5. Con la cuenta de prueba, abrir Ajustes → Copias de seguridad y Google Drive, elegir cuenta y autorizar. Guardar una copia, listarla y restaurarla en una instalación de prueba; confirmar libros, progreso y anotaciones.

No se incluye un secreto OAuth ni un token en el código. Este flujo Android identifica la app mediante paquete y certificado; no pide introducir un client ID de servidor en la app. No se deben subir keystores ni contraseñas al repositorio. Configurar el proyecto Google Cloud no necesita conceder acceso de Drive a ChatGPT.

## Comportamiento y límites

- El token se usa en memoria para una operación autorizada; no se persiste.
- Subida por bloques mediante sesión resumible HTTPS; archivos de hasta 2 GiB. Una interrupción que agote los reintentos requiere volver a iniciar la copia.
- La pantalla muestra las 100 copias más recientes. Cambiar cuenta limpia el listado anterior.
- Las copias de `appDataFolder` no aparecen como libros normales en Mi unidad. Google permite administrar/borrar los datos ocultos de la app; revocar acceso impide guardarlos o recuperarlos.
- Recuperar una copia reemplaza el estado de los libros incluidos y los ajustes globales por los guardados en ella; conserva los otros libros actuales. Se preparan y validan los EPUB antes de guardar la nueva biblioteca.
- Sin Google Play Services, sin cuenta o sin configuración OAuth válida, quedan disponibles las copias locales.

La conexión OAuth real no se ha verificado todavía: el propietario indicó que aún no tiene el proyecto configurado. Las pruebas de contrato validan rutas, metadatos, permisos, rangos y errores con conexiones simuladas; no equivalen a una subida a una cuenta real.
