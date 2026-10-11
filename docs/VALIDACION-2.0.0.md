# Validación de Nova Reader 2.0.0

Fecha: 11 de octubre de 2026. Versión 2.0.0 (18), paquete `io.nova.reader`. Código: `a911ff12c3ee89c890812ad4ea1d9f042ee3b522`.

Las funciones y sus límites se describen en [Novedades de 2.0](NOVEDADES-2.0.md). Compilación, lint, pruebas de Android y firma realizados en GitHub Actions; no se compiló localmente.

## Resultado

[GitHub Actions](https://github.com/Keiiiiken/Nova-Reader/actions/runs/38143542555): resultado completo **success**.

| Comprobación | Resultado |
|---|---|
| Pruebas debug en Android 15 | 53 aprobadas; 0 fallos, errores u omisiones |
| Pruebas release con R8 en Android 15 | 13 aprobadas; 0 fallos, errores u omisiones |
| APK normal de producción | Migración, biblioteca, pulsación larga, EPUB, herramientas, Ajustes y explicación de Drive aprobados |
| Narración con motor simulado | Cierre de actividad, notificación, pausa/reanudación, temporizador, velocidad y aviso de audio aprobados |
| Compilación, lint y firma de producción | Aprobados; certificado permanente, paquete y alineación verificados |
| Instalación, reinstalación y arranque del APK firmado | Aprobados en Android 15 |
| Contenido del APK firmado | Coincide con la producción compilada, excluyendo metadatos de firma |

Las pruebas release instrumentadas conservan los puntos de entrada que utiliza el entorno de pruebas. La comprobación posterior del APK normal usa las reglas de optimización de producción, sin esas excepciones. Las 66 pruebas corresponden a 53 casos debug y 13 repeticiones del lector en release.

Lint terminó sin errores y con 32 advertencias: versiones de dependencias/AGP, JavaScript del lector, una comprobación de versión de Android y sugerencias KTX. La tarea de pruebas unitarias indicó `NO-SOURCE`; las pruebas contabilizadas arriba son instrumentadas en Android.

## Capturas

Capturas sin editar obtenidas en esta ejecución:

- [Organización de biblioteca](capturas-2.0.0/organizar-biblioteca.png) y [horizontal con letra ampliada](capturas-2.0.0/organizar-horizontal-letra-grande.png), de las pruebas debug.
- [Notas filtradas](capturas-2.0.0/buscar-notas.png) y [actividad](capturas-2.0.0/actividad.png), de las pruebas debug.
- Lectores [PDF](capturas-2.0.0/lector-pdf.png) y [CBZ](capturas-2.0.0/lector-cbz.png), de las pruebas debug.
- [Referencia EPUB](capturas-2.0.0/referencia-epub.png), ajustes [a media altura](capturas-2.0.0/ajustes-lector-mitad.png) y [ampliados](capturas-2.0.0/ajustes-lector-ampliados.png), de las pruebas release instrumentadas.
- [Ajustes generales](capturas-2.0.0/release-ajustes.png) y [copias](capturas-2.0.0/release-copias.png), del APK normal de producción.

En horizontal y con letra ampliada, la lista de organización dispone de menos altura y requiere desplazamiento; la prueba comprueba que búsqueda, selección y acciones siguen accesibles. Esta revisión no sustituye pruebas de accesibilidad con TalkBack.

## APK publicado

[Descargar Nova Reader 2.0.0](https://github.com/Keiiiiken/Nova-Reader/releases/tag/v2.0.0-build.32).

- Archivo: `NovaReader-2.0.0.apk`, 15,128,558 bytes.
- SHA-256: `b5973776bf34823f2438d102b66df06cbd616daeb35fb196534bf2a98e298895`. Coincide entre el artefacto descargado, el informe de instalación y el archivo publicado en GitHub.
- SHA-1 del certificado: `61:E8:F8:A7:C3:AB:79:69:E7:76:6D:19:D7:33:2D:43:1F:D5:E8:FA`.
- Se conserva la clave permanente; instalar encima de Nova Reader sin desinstalarla.
- [Biblioteca del APK firmado después de reinstalar](capturas-2.0.0/release-firmado-biblioteca.png).

## Alcance de las comprobaciones

- Biblioteca: selección por lotes, estados, papelera y recuperación; búsqueda de notas; actividad y vocabulario; uso en horizontal con letra ampliada.
- Lectura: EPUB 2/3, posición tras cambios de fuente, avance vertical y horizontal, selección y subrayado, búsqueda, marcadores, referencias internas y panel de ajustes expansible.
- PDF/CBZ: importación, representación, cambio de página, zoom, marcadores y recuperación de posición al reabrir; inclusión en copias.
- Datos: migración desde preferencias, ajustes por libro, metadatos nuevos, detección de duplicados, restauración de libros, protección frente a rutas inseguras y limpieza al fallar una restauración.
- Copias: navegación mientras hay una operación activa, trabajo programado único y desactivable, ZIP local válido, actualización del mismo archivo y fecha persistente.
- Drive y sincronización: permisos, metadatos, subida por rangos, reemplazo de copia, conservación de la copia anterior ante errores, limpieza de descargas parciales, separación de archivos de sincronización, elección explícita de posición y conservación de versiones de notas. HTTPS simulado.
- Narración: motor determinista de pruebas; servicio activo al cerrar la actividad, notificación, pausa/reanudación, temporizador, cambios efectivos de velocidad y pausa por desconexión de audio simulada.

No se probó un teléfono físico, TalkBack, una cuenta real de Google, sincronización entre dos teléfonos ni calidad de voces reales. No se volvió a comprobar la biblioteca de la app antigua `io.luma.reader`. La reinstalación del mismo APK no equivale a una prueba de actualización desde todas las versiones anteriores; la migración y restauración de datos tienen pruebas separadas.
