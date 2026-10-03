# Luma Reader

Luma Reader es una app Android local-first para organizar y leer EPUB 2 y EPUB 3.

## Funciones incluidas

- Importación múltiple mediante el selector seguro de Android.
- Biblioteca offline con portadas, búsqueda, favoritos, orden y colecciones.
- Interfaz editorial contemporánea con navegación flotante, portadas generativas y paneles visuales de actividad.
- Inicio dinámico con acceso directo a la lectura actual, filtros y una biblioteca adaptable al contenido.
- Detección automática de portada desde la primera imagen del libro cuando el EPUB no declara una.
- Portada editable desde las opciones del libro, copiada de forma segura al almacenamiento privado.
- Progreso por capítulo y restauración de la posición de lectura.
- Lectura mediante desplazamiento vertical o páginas horizontales, con salto de capítulo mediante un nuevo gesto en el borde.
- Zoom por pellizco para ilustraciones, mapas y páginas maquetadas dentro del EPUB.
- Ajustes generales para toda la aplicación desde el botón superior derecho, separados de los ajustes de lectura.
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
- Sin cuenta, anuncios, analítica ni conexión a Internet.

## Abrir y ejecutar

1. Abre esta carpeta en Android Studio.
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
- Acceso a los ajustes generales desde el logotipo superior.
- Altura del panel de ajustes por novela para conservar la vista en vivo.
- Persistencia de la densidad de biblioteca y del tiempo real de lectura.
- Persistencia del filtro de estantería, oscurecimiento nocturno y ritmo detallado de TTS.
- Resaltado y desplazamiento del párrafo activo durante la narración.
- Disponibilidad de “Leer desde aquí” junto a la acción de subrayado.

Puedes usar `sample-book/El-jardin-de-la-luz.epub` para una prueba rápida.
