# Prioridades de rendimiento y lector

Revisión del código; todavía no es un perfil de rendimiento en un teléfono real.

## Fluidez

1. `MainViewModel.updateReadingPosition` termina en `mutateLibrary` y `LibraryRepository.save` en cada actualización del desplazamiento. Aunque SharedPreferences usa `apply`, la serialización completa a JSON se hace antes en el hilo que llama. Mantener el progreso en memoria, agrupar escrituras y serializar fuera del hilo principal; guardar también al cambiar de capítulo y cerrar/pausar. Para bibliotecas grandes, migrar a Room con actualización por libro y migración de los datos actuales.
2. `ReaderScreen` lee y transforma el HTML del capítulo dentro de `AndroidView.update`. Mover lectura y preparación a IO/Default, precargar el siguiente capítulo y aplicar cambios de estilo sin recargar todo el documento cuando sea posible.
3. Reducir ordenaciones y filtrados repetidos con listas derivadas que dependan solo de libros/filtro/búsqueda. Evitar que cada cambio de progreso regenere todo el estado de la biblioteca.

Ya existen carga de portadas fuera del hilo principal, reducción de resolución, caché y listas perezosas; conviene conservarlas.

## Tamaño

Release sigue con `isMinifyEnabled = false`. Activar R8 y `isShrinkResources`, revisar los puentes JavaScript del WebView y validar TTS, selección, notas e importación antes de publicar. `material-icons-extended` incluye una colección amplia: R8 puede quitar lo que no se usa; sustituirla por los vectores necesarios es una alternativa. Medir el APK release, memoria y tiempos en un móvil de gama baja antes/después.

## Lector de novelas

- Búsqueda de texto dentro de todo el libro, con resultados por capítulo y salto al fragmento.
- Marcadores independientes de los subrayados y posiciones resistentes a cambios de fuente/tamaño (anclas de texto/EPUB CFI).
- Desde Notas globales, saltar a la anotación exacta: actualmente se abre el libro en su última posición, aunque se indica el capítulo de la nota.
- Copia de seguridad y restauración local de libros, progreso, estantes y notas; exportación legible de las notas.
- Validación con novelas largas, HTML irregular, imágenes grandes, Android WebView distintos, paginación y TTS. Ya hay temas, tipografías, anotaciones y TTS: las prioridades son fiabilidad y navegación.

## Entrega 1.8.1

Se añadió pulsación larga en biblioteca, estantes y Continuar leyendo, invocando el mismo callback de los tres puntos. Se restauró el icono original. Se preparó CI con compilación release, lint, pruebas unitarias, artefactos e informe de firma. Estas prioridades de optimización no se han implementado en esta entrega.
