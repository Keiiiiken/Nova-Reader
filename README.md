# Nova Reader

Nova Reader es una app Android para organizar y leer EPUB 2 y EPUB 3 sin conexión. Drive es opcional para guardar copias.

La versión 1.9.1 se instala en paralelo con `io.nova.reader` y usa una nueva firma permanente. Descargar el APK firmado del release. Consulta [instalación y firma](docs/COMPILACION-Y-FIRMA.md) y [configurar Drive](docs/GOOGLE-DRIVE.md).

## Funciones incluidas

- Importación múltiple mediante el selector seguro de Android.
- Biblioteca offline con portadas, búsqueda, favoritos, orden y colecciones.
- Biblioteca moderna con fondo limpio, acento azul, filtros subrayados y navegación fija: Biblioteca, Estantes y Notas.
- Acceso compacto a la lectura actual, búsqueda por título o autor e importación visible.
- Portadas tipográficas sobrias para libros sin imagen y sección local de subrayados y comentarios.
- Detección automática de portada desde la primera imagen del libro cuando el EPUB no declara una.
- Portada editable desde las opciones del libro, copiada de forma segura al almacenamiento privado.
- Progreso por capítulo y restauración de la posición de lectura.
- Lectura mediante desplazamiento vertical o páginas horizontales, con salto de capítulo mediante un nuevo gesto en el borde.
- Zoom por pellizco para ilustraciones, mapas y páginas maquetadas dentro del EPUB.
- Ajustes generales para toda la aplicación desde el menú superior derecho, separados de los ajustes de lectura.
- Ajustes independientes por novela, con opción para volver a usar los generales.
- Panel de ajustes a media pantalla dentro del lector para ver los cambios en vivo.
- Diez temas de lectura: papel, sepia, arena, menta, lavanda, rosa, cielo, pizarra, noche y OLED.
- Ocho familias tipográficas y tamaño, interlineado, márgenes, alineación y modo paginado o desplazamiento.
- Estantes como una sección independiente, con vista propia de sus libros y navegación de regreso.
- Estantes escalables en lista editorial y selector emergente desplazable con búsqueda para bibliotecas grandes.
- Vista configurable de dos o tres libros por fila tanto en Inicio como dentro de cada estante.
- Filtro persistente para incluir las novelas ya organizadas en estantes o mostrar solo las pendientes.
- Portadas escaladas y cargadas fuera del hilo principal con caché de memoria para bibliotecas grandes.
- Nombre, reseña y valoración por estrellas editables en cada novela.
- Subrayados multipárrafo y comentarios por capítulo, con selección contextual, edición, salto al texto y borrado.
- Temas generales claro/oscuro/sistema y acentos violeta, océano o bosque.
- Tipografía, tamaño, interlineado, márgenes, alineación, brillo y pantalla activa configurables.
- Texto a voz con pausa y reanudación en la frase actual, motor, idioma, voz concreta, velocidad, tono y volumen.
- Narración continua entre capítulos, salto automático de capítulos visuales y pausas configurables entre oraciones, párrafos y capítulos.
- Seguimiento visual del TTS por palabra y párrafo, desplazamiento automático y restauración de la posición al volver a la app.
- Acción “Leer desde aquí” al seleccionar una palabra o frase dentro del EPUB.
- Brillo global desde 2% y oscurecimiento adicional para lectura nocturna.
- Actividad local basada en tiempo real dentro del lector, con desglose por libro.
- Caché privada: el EPUB y su contenido se copian al almacenamiento interno de la app.
- Lectura y diccionario sin cuenta, anuncios ni conexión; copias opcionales con la cuenta Google del usuario.

## Versión 1.8.0 — Diseño B

La aplicación mantiene `io.luma.reader`. Para actualizar una instalación existente conservando su biblioteca, también debe firmarse con la clave original.
Los nuevos usuarios comienzan con acento azul y tres columnas; las preferencias guardadas se respetan.
Las notas abren el libro en su posición guardada. El lector mantiene su funcionamiento actual.

## Versión 1.8.1

Mantener pulsada una novela abre el mismo menú que los tres puntos, también en estantes y en Continuar leyendo.
GitHub Actions compila y valida release y publica el APK y los informes. Las versiones 1.8.1 y 1.9.0 se publicaron sin firma. Desde 1.9.1 el release entregable usa la nueva firma permanente; un unsigned queda solo como artefacto de laboratorio. Consulta [compilación y firma](docs/COMPILACION-Y-FIRMA.md).

## Abrir y ejecutar

1. Abre esta carpeta en Android Studio con JDK 17, SDK 36 y Build Tools 35.0.0.
2. Espera a que termine la sincronización de Gradle.
3. Selecciona un teléfono o emulador con Android 8.0 o superior.
4. Ejecuta la configuración `app`.

Para compilar desde PowerShell:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME="$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat assembleDebug
```

El APK se genera en `app/build/outputs/apk/debug/app-debug.apk`.

Para probar el rediseño junto a una instalación existente:

```sh
./gradlew assemblePreview
```

El APK está en `app/build/outputs/apk/preview/app-preview.apk`.
Esta variante se llama “Nova Reader · Prueba”, usa `io.nova.reader.preview` y guarda una biblioteca independiente.
Para actualizar la app original conservando sus datos se debe usar su misma clave de firma.

## Compatibilidad y pruebas

Las pruebas instrumentadas en Android 15 cubren:

- EPUB 3 con `nav.xhtml` y referencias entre carpetas.
- EPUB 2 con índice NCX.
- ZIP creados en Windows con separadores `\`.
- Uso de la primera imagen del contenido como portada.
- Lectura y guardado de avance mediante desplazamiento.
- Lectura paginada horizontal y guardado de avance.
- Zoom del lector y avance al siguiente capítulo desde el final mediante deslizamiento.
- Normalización de frases divididas en etiquetas HTML para lectura visual y TTS natural.
- Copia y validación de portadas personalizadas.
- Persistencia separada de ajustes globales y ajustes por novela.
- Acceso a los ajustes generales desde el menú superior.
- Altura del panel de ajustes por novela para conservar la vista en vivo.
- Persistencia de la densidad de biblioteca y del tiempo real de lectura.
- Persistencia del filtro de estantería, oscurecimiento nocturno y ritmo detallado de TTS.
- Resaltado y desplazamiento del párrafo activo durante la narración.
- Disponibilidad de “Leer desde aquí” junto a la acción de subrayado.

Puedes usar `sample-book/El-jardin-de-la-luz.epub` para una prueba rápida.

## Versión 1.9.0

Diccionario español incluido para uso sin conexión; búsqueda por capítulos de todo el libro, marcadores con posiciones de texto, acceso desde notas al fragmento y exportación de notas a Markdown. Copias locales ZIP con restauración de EPUB, portadas, progreso, notas, marcadores, estantes y ajustes; soporte opcional de Google Drive appDataFolder.

La biblioteca migra de SharedPreferences a SQLite. Las escrituras se realizan ordenadas fuera del hilo principal; el progreso se agrupa y se guarda también al salir. Los capítulos se preparan en segundo plano con caché acotada y precarga del siguiente. Cambiar tipografía actualiza el estilo del documento sin volver a cargarlo. Release utiliza R8 y reducción de recursos.

- [Configurar Google Drive](docs/GOOGLE-DRIVE.md)
- [Licencia y datos del diccionario](docs/DICCIONARIO-LICENCIA.md)
- [Compilación y firma](docs/COMPILACION-Y-FIRMA.md)

Las copias de Drive son manuales y necesitan configurar un cliente OAuth Android para la firma instalada. El APK principal mantiene el paquete y el icono originales.
