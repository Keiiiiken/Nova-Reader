# Validación de Nova Reader 1.9.0

## Alcance

Paquete `io.luma.reader`, versión 1.9.0, código 12. La app principal mantiene el nombre Nova Reader y el icono original. La clave privada original no está en el proyecto recibido: el APK de distribución queda sin firma hasta configurar los secretos descritos en COMPILACION-Y-FIRMA.md.

La biblioteca migra el estado antiguo a SQLite. El guardado se ordena en segundo plano y agrupa cambios de progreso. Los capítulos se cargan fuera del hilo de interfaz, con caché limitada a 16 MiB. Los ajustes visuales actualizan el documento abierto. Estas medidas reducen trabajo repetido; no se ha medido una tasa de fotogramas ni el rendimiento en teléfonos de gama baja.

## Pruebas

Los XML de la [ejecución 14](https://github.com/Keiiiiken/Nova-Reader/actions/runs/37174230674) confirman 30 pruebas debug y 12 pruebas release instrumentadas con R8, todas aprobadas. La compilación posterior del APK normal también terminó correctamente. El workflow falló después porque el script de validación suponía una ubicación fija para la clave temporal del emulador. La corrección consulta el almacén configurado por Gradle; se ejecutó localmente esa función con Gradle y apksigner reales, comprobando el certificado contra el APK debug y la eliminación del APK temporal. La instalación en Android y las comprobaciones finales de interfaz se validan en GitHub. Las incidencias están documentadas en INCIDENCIAS-CI-1.9.0.md.

La [ejecución 15](https://github.com/Keiiiiken/Nova-Reader/actions/runs/37175830167), sobre el commit `247f3cc6e456a1dc96bf37c3187059cbc9e069a1`, aprobó el job `android-tests`: 30 pruebas debug y 12 pruebas release instrumentadas, sin errores, fallos ni pruebas omitidas. El APK normal superó después la migración de biblioteca, pulsación larga, renderizado de texto EPUB en WebView y apertura de las herramientas del lector. Se generaron 12 capturas reales: ocho de las suites y cuatro del APK normal. El job `build` también terminó correctamente: `assembleRelease`, `lintRelease` y publicación del [prerelease 1.9.0 · build 15](https://github.com/Keiiiiken/Nova-Reader/releases/tag/v1.9.0-build.15). `testReleaseUnitTest` devuelve `NO-SOURCE`, por lo que no se cuenta como otra prueba. Ambos jobs del workflow terminaron con `success`. El APK publicado está sin firma.

La batería cubre navegación y pulsación larga, lectura vertical y paginada, persistencia, selección y anotaciones, búsqueda entre capítulos, marcadores, posiciones después de cambiar tamaño de letra, diccionario, migración, restauración y rechazo de copias dañadas. Las 12 pruebas de interacción del lector se repiten con R8 activado. La comprobación posterior del APK normal usa la interfaz accesible de Android, sin el ejecutor de instrumentación ni sus reglas de conservación.

Las cuatro pruebas de Drive usan conexiones HTTPS simuladas para comprobar appDataFolder, metadatos, rangos de subida, rechazo de un servidor inesperado y limpieza después de errores. No verifican una cuenta de Google real. El propietario todavía debe crear el proyecto OAuth Android; consultar GOOGLE-DRIVE.md.

La compilación local final de `assembleRelease` y `lintRelease` terminó correctamente en 4 min 56 s. La función de firma temporal también se ejecutó con Gradle y apksigner reales; se verificó el certificado y la limpieza del APK temporal. Lint informa cero errores y cuatro advertencias: uso intencional de JavaScript en WebView, comprobación de versión de SDK y dos recomendaciones KTX. El APK sin firma ocupa aproximadamente 11,7 MiB; la versión anterior ocupaba aproximadamente 10,4 MiB. R8 compensa parte del coste del diccionario, pero el APK total es mayor.

## Diccionario y almacenamiento

El diccionario español contiene 115 421 entradas con definiciones y 460 537 referencias de formas a lemas, con crédito y licencia CC BY-SA 4.0. Los datos comprimidos ocupan aproximadamente 10,1 MiB en el APK y se extraen una sola vez a una base SQLite de aproximadamente 36,9 MiB en el almacenamiento privado. La integridad de SQLite y de los fragmentos comprimidos se comprobó durante la preparación.

Las copias incluyen EPUB, portadas, notas, marcadores, progreso y ajustes. Son manuales: no hay sincronización continua ni fusión de conflictos. Recuperar una copia restablece el estado de sus libros y los ajustes globales, y conserva los otros libros existentes.

## Límites de la validación

Las capturas corresponden a un emulador, no a un teléfono físico. La navegación del lector usa referencias al texto y al bloque, no EPUB CFI. La prueba de narración comprueba el resaltado del párrafo, no la salida de audio de un motor TTS real. La compatibilidad de actualización depende de firmar con la clave original y debe comprobarse sobre una instalación anterior.

## Artefacto publicado

`NovaReader-1.9.0-unsigned.apk`: 12 269 881 bytes. SHA-256 `eb8d928e28e28b0de3158c18f785fd18b14fca7735f628b74c011970ac000d3d`. El archivo de esta entrega se descargó del artefacto del workflow aprobado. Los 72 fragmentos del diccionario y el código local coinciden con el commit probado; el DEX normal local coincide con el DEX publicado.
