# Qué le falta a Nova Reader

Revisión del código del 10 de octubre de 2026, después del rediseño 1.9.4. Son propuestas pendientes, no funciones incluidas ni errores confirmados en un teléfono.

## 1. Fiabilidad de las copias y recuperación

Las copias continúan al cerrar su menú, pero `MainViewModel.backupOperation` usa una tarea ligada al proceso de la app. Falta trabajo persistente con notificación, cancelación y recuperación tras interrupciones, además de copias programadas opcionales. Drive usa copias manuales, no sincronización del progreso entre dispositivos. Una sincronización futura necesitaría reglas de conflicto para no perder notas ni avances.

La actualización de una sola copia de Drive tiene pruebas de contrato con HTTPS simulado. Falta comprobar autorización, guardar dos veces, restaurar y recuperación de errores con una cuenta real. También conviene mostrar fecha y resultado de la última copia completada aunque se cierre y abra la app.

## 2. Comodidad con bibliotecas grandes

La biblioteca tiene búsqueda, filtros, favoritos y estantes. Falta selección múltiple para mover o eliminar varios libros, renombrar estantes y una ficha que permita corregir el autor. En Notas, convendría buscar texto y filtrar por libro. Son mejoras más útiles ahora que añadir más colores.

## 3. Accesibilidad y pantallas distintas

Se han mejorado contraste, indicación de selección y espacio para títulos. Falta una revisión completa con TalkBack, fuentes grandes, orientación horizontal y tabletas; la biblioteca todavía ofrece dos o tres columnas en lugar de adaptar la densidad al ancho. Las pruebas actuales de CI usan un emulador Android 15; faltan teléfonos físicos, otras versiones y mediciones de memoria/fluidez con bibliotecas grandes.

## 4. Narración como reproductor

La voz tiene selección de motor, idioma, velocidad y seguimiento del texto. Sin embargo, `ReaderScreen` administra el motor y lo detiene al salir de su composición. Falta un servicio de reproducción y controles multimedia en la notificación, pantalla bloqueada y auriculares. También sería útil un temporizador para dormir. Hay que probar el comportamiento con motores y voces reales del dispositivo.

## 5. Entrada de archivos y alcance del lector

Hoy se importan EPUB desde el selector dentro de la app. El manifiesto no registra “Abrir con Nova Reader” ni recepción desde “Compartir”; sería una mejora práctica para archivos recibidos o descargados. PDF y CBZ serían ampliaciones opcionales, con renderizadores y pruebas propios, no una prioridad sobre estabilizar EPUB.

## Lo que ya existe

No hay que volver a implementar búsqueda dentro del libro, marcadores, salto a notas, diccionario español local, SQLite, exportación de notas a Markdown, copias ZIP ni reducción con R8: están presentes. `REVISION-RENDIMIENTO-Y-LECTOR.md` es un diagnóstico histórico de 1.8.1; parte de sus pendientes se resolvió en 1.9.0.

## Orden recomendado

Primero validar Drive real y fortalecer las copias; después accesibilidad y herramientas para organizar muchos libros; luego narración en segundo plano y entrada desde otras apps. Nuevos formatos y sincronización automática requieren un alcance separado.
