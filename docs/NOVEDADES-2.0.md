# Nova Reader 2.0

Esta versión reúne las mejoras de organización, lectura, recuperación y comodidad solicitadas. Las comprobaciones y sus resultados se registran por separado en el informe de validación; esta guía describe el comportamiento previsto.

## Biblioteca y recuperación

- El botón **Organizar biblioteca**, junto a Importar, abre la selección múltiple. Permite mover libros de estante, cambiar su estado, marcar/quitar favoritos y enviarlos a la papelera.
- Los estados **Pendiente, Leyendo, Terminado y Abandonado** pueden elegirse manualmente. Los libros antiguos conservan el estado deducido del progreso hasta que se elija uno.
- El lápiz de cada libro permite corregir título y autor, añadir saga, volumen y etiquetas. La búsqueda de biblioteca incluye estos campos.
- La pestaña **Estantes** permite cambiar nombre y color. Los libros permanecen en su colección.
- La **Papelera** conserva durante 30 días archivo, portada, progreso, notas y marcadores. Permite restaurar o eliminar definitivamente con confirmación. Los vencidos se eliminan al abrir la app; también forman parte de las copias mientras sigan en la papelera.
- Importar el mismo archivo otra vez lo omite con un aviso. Se compara el contenido completo, no solo el nombre; una edición distinta del mismo título sigue siendo otro archivo. Los duplicados que ya existían no se eliminan automáticamente.

## Lectura y archivos

- El porcentaje del EPUB pondera los capítulos por cantidad de palabras. Las ilustraciones y capítulos sin texto tienen un peso mínimo; sigue siendo una estimación, no una numeración impresa.
- Los enlaces internos y las notas al pie abren una referencia local sin perder la posición. Se puede cerrar la ventana o ir al fragmento si pertenece al índice del libro.
- Se incluyen siete fuentes con licencia SIL OFL. La opción **Sistema** usa la fuente del dispositivo. Véase [tipografías y licencias](TIPOGRAFIAS.md).
- **Abrir con** y **Compartir** reciben EPUB, PDF y CBZ desde otras aplicaciones que faciliten acceso al archivo. También se pueden elegir desde Importar.
- PDF y CBZ se muestran por páginas, con deslizamiento, zoom, selector de página y marcadores. Guardan posición y tiempo; se incluyen en copias ZIP. El PDF no se convierte a texto: búsqueda textual, selección, diccionario y narración corresponden al lector EPUB. No se admite DRM ni PDF protegido con contraseña. CBZ admite JPEG, PNG y WebP.
- La biblioteca adapta las columnas al ancho y tamaño de letra. Puede desactivarse en Ajustes → Biblioteca. Los botones mantienen etiquetas accesibles y las pantallas de organización permiten desplazamiento.

## Notas, palabras y actividad

- **Notas** permite buscar dentro de citas/comentarios y filtrar por libro o color.
- El diccionario permite **Guardar en mi vocabulario**, con la selección y título del libro cuando se abre desde el lector. El vocabulario se consulta y elimina desde Organizar → Vocabulario o Ajustes → Herramientas.
- **Actividad** muestra minutos del día y calendario de 28 días, con meta opcional de 10, 20, 30 o 60 minutos. El tiempo histórico por libro se conserva; el detalle diario comienza al actualizar. Se cuenta el tiempo con el lector visible, no la escucha en segundo plano.

## Copias y sincronización

- Guardar una copia inicia una tarea persistente de Android con notificación y cancelación. Los errores de conexión recuperables se reintentan con espera creciente. La fecha de la última copia completada permanece visible tras reiniciar.
- Después de guardar una copia se puede programar la misma ubicación cada día o semana, y limitar Drive a Wi-Fi. Android decide la hora según sus restricciones de batería y conexión. Forzar la detención desde los ajustes de Android suspende las tareas hasta volver a abrir la app; no puede garantizarse ejecución a una hora exacta.
- Drive mantiene una sola copia de seguridad. La programación usa la cuenta/destino de la última copia solicitada; guardar en otro destino actualiza también la tarea programada. La copia local se actualiza en el mismo archivo; **Elegir otro archivo** permite cambiarlo. Un proveedor local debe conservar el permiso de escritura para funcionar sin abrir su selector.
- **Sincronizar ahora** intercambia progreso, estado, notas y marcadores de archivos idénticos importados en los otros dispositivos de la misma cuenta. No sube los libros ni configura sincronización continua. Las preferencias, estantes y papelera permanecen locales.
- Si hay posiciones distintas se elige cuál conservar; por defecto queda la local. Las notas diferentes se conservan como versiones separadas. Una nota eliminada que todavía exista en otro dispositivo puede volver para revisión: la sincronización no propaga borrados destructivos.
- Cada dispositivo mantiene sus propios datos de sincronización en el espacio privado de Nova Reader, separados de la copia ZIP. No se accede a los demás archivos de Drive y no se guardan tokens OAuth en la app.
- Las pruebas automatizadas de Drive emplean HTTPS simulado. Autorizar y probar dos dispositivos reales requiere la cuenta del propietario en sus teléfonos.

## Narración

- La voz se reproduce mediante un servicio en primer plano. Continúa al salir del lector y ofrece pausa/reanudación, siguiente capítulo y detener en la notificación y pantalla bloqueada. La biblioteca muestra un reproductor compacto.
- La narración se pausa si pierde el audio o se desconectan los auriculares. La reanudación es manual.
- Ajustes del lector → Voz permite programar una pausa en 10, 20, 30 o 60 minutos, o desactivar el temporizador. Se conserva el punto de narración para volver a leer.
- Motor, idioma, voz, velocidad, tono, volumen y pausas siguen siendo configurables. La disponibilidad y calidad dependen de las voces instaladas; las pruebas con voz simulada comprueban el servicio y sus controles, no la calidad de un motor real.

## Actualización

Versión 2.0.0 (18), paquete `io.nova.reader`. Mantiene la clave de firma permanente. Instalar encima de Nova Reader sin desinstalarla. Los nuevos campos se añaden al estado existente y las copias antiguas siguen siendo legibles; la app antigua `io.luma.reader` continúa teniendo datos independientes.
