# Configurar Google Drive para Nova Reader

La lectura, el diccionario y las copias locales funcionan sin Internet. Drive es opcional: guarda copias ZIP de libros, progreso, notas, marcadores y ajustes en `appDataFolder`, el espacio oculto de esta app en la cuenta elegida. Desde 2.0, las copias pueden ser manuales o programadas. Hay una operación separada para sincronizar progreso, notas y marcadores; permite elegir el avance cuando difiere entre dispositivos. No hay sincronización continua.

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

Se puede cerrar el menú o la actividad durante una copia: WorkManager mantiene el trabajo con una notificación y permite cancelarlo. Android puede reanudarlo tras una interrupción del proceso; un cierre forzado desde los ajustes del sistema impide ejecutarlo hasta volver a abrir la app. La programación diaria o semanal es aproximada y depende de batería, espacio y conexión. Se permite leer y cambiar preferencias al guardar; borrar libros y cambiar portadas deben esperar para conservar los archivos que se están copiando. Durante una restauración, la edición espera a que termine. Si se agotan los reintentos o Google requiere autorizar de nuevo la cuenta, hay que abrir Copias y resolver el error. El token no se guarda en WorkManager: cada ejecución obtiene autorización cuando la necesita. Restaurar reemplaza el estado de sus libros y los ajustes globales y conserva los otros libros actuales.

La conexión con la cuenta elegida y el guardado de una segunda copia deben comprobarse en el teléfono. Las pruebas automatizadas usan HTTPS simulado y comprueban permisos, metadatos, rangos, actualización del mismo archivo, errores y limpieza; no equivalen a una conexión real con Google.

## Sincronizar dos dispositivos

Importa el mismo archivo original en ambos e inicia sesión en la misma cuenta de Drive. En **Copias → Sincronizar progreso y notas**, revisa los avances distintos y elige cuáles recuperar. Ejecuta la operación también en el otro dispositivo para recibir lo compartido. Los libros se reconocen por su contenido, aunque tengan nombres diferentes.

La sincronización usa archivos separados por dispositivo y no reemplaza la copia ZIP. Conserva versiones distintas de las notas para revisarlas; un borrado local no elimina una nota que siga en otro dispositivo. No transfiere los libros, ajustes, papelera ni historial de actividad. Consulta [la guía 2.0](NOVEDADES-2.0.md).
