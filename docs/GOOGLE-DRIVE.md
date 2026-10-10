# Configurar Google Drive para Nova Reader

La lectura, el diccionario y las copias locales funcionan sin Internet. Drive es opcional: guarda copias ZIP de libros, progreso, notas, marcadores y ajustes en `appDataFolder`, el espacio oculto de esta app en la cuenta elegida. Las copias son manuales; no hay sincronización continua ni fusión de conflictos.

## Pasos en Google Cloud

1. Abrir https://console.cloud.google.com/ y crear un proyecto llamado **Nova Reader** (o usar uno propio).
2. Entrar en **APIs y servicios → Biblioteca**, buscar **Google Drive API** y pulsar **Habilitar**.
3. Abrir **Google Auth Platform**. En **Branding**, completar nombre Nova Reader y correos de soporte/contacto. En **Audience**, seleccionar **External**, mantener **Testing** para comenzar y añadir el correo Google que se usará en el teléfono como usuario de prueba. En **Data Access**, añadir el permiso `https://www.googleapis.com/auth/drive.appdata`.
4. En **Clients → Create client**, elegir **Android** e introducir exactamente:

   - Nombre: Nova Reader Android.
   - Package name: `io.nova.reader`.
   - SHA-1: `61:E8:F8:A7:C3:AB:79:69:E7:76:6D:19:D7:33:2D:43:1F:D5:E8:FA`.

5. Guardar el cliente. En el teléfono, instalar el APK firmado más reciente, abrir **Ajustes → Copias de seguridad y Google Drive**, leer la explicación de acceso, elegir esa cuenta de prueba y autorizar. Guardar una copia, volver a listar y comprobar que aparece. Probar la restauración en una instalación sin datos importantes.

Este flujo Android usa paquete y certificado; no necesita pegar el client ID ni un secreto OAuth en el código. No se necesita `google-services.json`, Firebase, cuenta de servicio ni un servidor para esta función. La configuración no concede acceso a ChatGPT.

La nueva firma SHA-1 sustituye a la de la versión antigua. Registrar `io.luma.reader` o el SHA-1 anterior no autoriza la app nueva. Una build debug de otro ordenador necesita su propio cliente Android con el SHA-1 debug. Si Google Play vuelve a firmar la app, añadir también un cliente con el certificado de App signing mostrado en Play Console.

Guía oficial del flujo: https://developers.google.com/identity/authorization/android

## Si aparece un error al autorizar

- Confirmar Google Drive API habilitada y cliente Android en el mismo proyecto.
- Revisar paquete y SHA-1 carácter por carácter contra los datos anteriores.
- Confirmar que la cuenta elegida está en Test users mientras el proyecto esté en Testing.
- Usar un dispositivo con Google Play Services actualizado y conexión a Internet para guardar o recuperar la copia.

Antes de abrir la app a todos, cambiar la audiencia a Production y completar los requisitos de marca, dominios, política de privacidad y términos que indique la consola. El permiso appData no da acceso al resto de Mi unidad; las copias no aparecen como libros normales en Drive.

## Límites

El token se usa en memoria por operación y no se persiste. Se admiten copias de hasta 2 GiB. Desde 1.9.3, guardar en Drive actualiza el archivo más reciente de esa cuenta. Las copias antiguas de Nova Reader se eliminan solo cuando termina la subida nueva; si la limpieza falla, se informa y puede completarse al volver a guardar. Las copias locales se guardan en el archivo elegido por el usuario.

Se puede cerrar el menú durante una copia: la operación continúa al navegar dentro de Nova Reader, con progreso visible en la biblioteca. No es una tarea persistente que sobreviva al cierre forzado de la app. Se permite leer y cambiar preferencias al guardar; borrar libros y cambiar portadas deben esperar para conservar los archivos que se están copiando. Durante una restauración, la edición espera a que termine. Una interrupción que agote los reintentos requiere iniciar otra copia. Restaurar reemplaza el estado de sus libros y los ajustes globales y conserva los otros libros actuales.

La conexión con la cuenta elegida y el guardado de una segunda copia deben comprobarse en el teléfono. Las pruebas automatizadas usan HTTPS simulado y comprueban permisos, metadatos, rangos, actualización del mismo archivo, errores y limpieza; no equivalen a una conexión real con Google.
