# Estado de las mejoras de Nova Reader

Actualizado para el desarrollo de 2.0.0. Esta lista distingue implementación de validación; las pruebas y la publicación se documentan por versión.

## Implementado en 2.0

- Copias con WorkManager, notificación, cancelación, resultado persistente y programación diaria o semanal.
- Referencias EPUB sin perder el punto de lectura, progreso ponderado por texto y tipografías incluidas con licencia.
- Detección de archivos duplicados, selección múltiple, estados manuales, metadatos, series, etiquetas, estantes editables y papelera.
- Búsqueda y filtros de notas, vocabulario con contexto, actividad diaria y objetivos.
- Entrada desde Abrir con/Compartir, columnas adaptables y pruebas de interfaz con letra grande y orientación horizontal.
- Servicio de narración con controles multimedia y temporizador.
- Lector PDF/CBZ por páginas con zoom y marcadores.
- Sincronización manual entre dispositivos con elección del avance y conservación de versiones de notas.

[Uso y límites de estas funciones](NOVEDADES-2.0.md).

## Comprobaciones pendientes fuera del emulador

- Cuenta real de Google: autorizar, actualizar la copia existente, restaurar y sincronizar entre dos dispositivos. Las pruebas de contrato HTTPS no sustituyen esta comprobación.
- Narración con motores y voces reales, auriculares y pantalla bloqueada en un teléfono físico. El motor determinista de pruebas valida el servicio y los controles.
- Revisión manual con TalkBack, tabletas y fabricantes/versiones distintas de Android; mediciones con bibliotecas grandes.

## Alcance que sigue separado

La sincronización es manual, conserva borrados encontrados en otros dispositivos para revisión y requiere importar los mismos archivos. PDF/CBZ ofrecen lectura visual; no incluyen selección de texto, diccionario ni narración. No hay soporte de DRM ni documentos protegidos con contraseña.
