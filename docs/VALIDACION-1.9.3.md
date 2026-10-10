# Validación de Nova Reader 1.9.3

Fecha: 10 de octubre de 2026. Versión 1.9.3 (15), paquete `io.nova.reader`. Código validado: `026c77d67a3203d168737cbcae0f5dd4072e75d6`.

## Cambios

- Ajustes generales organizados en tarjetas, con selección visual de tema, colores de acento y accesos a lectura, diccionario y copias. Cabeceras y botones de cierre consistentes, y controles de lectura y voz con tarjetas y valores más claros.
- Explicación del permiso privado de Drive antes de abrir la selección de cuenta y la autorización de Google.
- Cerrar el menú durante una copia elimina su ventana modal y permite volver a la navegación. La operación sigue en el ViewModel, muestra progreso en la biblioteca y permite leer y cambiar preferencias mientras se guarda. La cuenta elegida se conserva en memoria al reabrir el menú. Borrar libros y cambiar portadas esperan para proteger los archivos que se están copiando; restaurar bloquea la edición hasta terminar.
- Drive actualiza el archivo de copia más reciente de la cuenta. Consulta todas las páginas de copias anteriores y elimina los duplicados solo después de completar la subida. Usa el método de actualización mediante POST con `X-HTTP-Method-Override: PATCH`, admitido por Google para transportes que no soportan PATCH directamente. Las copias locales se guardan en el archivo elegido por el usuario.

## Comprobaciones

[GitHub Actions](https://github.com/Keiiiiken/Nova-Reader/actions/runs/38067520914): resultado completo **success**.

| Comprobación | Resultado |
|---|---|
| Pruebas debug en Android 15 | 35 aprobadas; 0 fallos, errores u omisiones |
| Pruebas release con R8 en Android 15 | 12 aprobadas; 0 fallos, errores u omisiones |
| APK normal de producción | Migración de biblioteca, pulsación larga, lectura EPUB, herramientas, nuevos Ajustes, Copias y aviso de Drive aprobados |
| Compilación y lint de producción | Aprobados |
| Firma, paquete y alineación | Certificado permanente y `io.nova.reader` verificados |
| Instalación, reinstalación y arranque del APK firmado | Aprobados en el emulador Android 15 |
| Contenido del APK firmado | Coincide con el APK de producción de su compilación, excluyendo metadatos de firma |

La regresión de la copia mantiene una exportación real en curso, cierra por gesto, reabre, cierra con Atrás, cambia el tema y cierra con el botón. Comprueba que la navegación responde, el ZIP termina y los ajustes quedan guardados. Las pruebas de Drive comprueban la primera creación, la actualización del mismo archivo, paginación y eliminación de duplicados después del éxito; una subida fallida no elimina las copias anteriores.

## APK publicado

[Release 1.9.3](https://github.com/Keiiiiken/Nova-Reader/releases/tag/v1.9.3-build.22).

- Archivo: `NovaReader-1.9.3.apk`.
- SHA-256 del APK: `34cc84aca25ee2d23b83c7d9d69b30f5d4cd5844dfbb2cdae153f368080c9581`.
- SHA-1 del certificado: `61:E8:F8:A7:C3:AB:79:69:E7:76:6D:19:D7:33:2D:43:1F:D5:E8:FA`.

Se conserva la firma permanente. No se cambiaron la clave privada, los secretos de GitHub ni el cliente OAuth Android.

## Alcance

Las pruebas de Drive usan HTTPS simulado; la actualización en una cuenta real debe comprobarse desde el teléfono guardando dos veces y verificando que queda una sola copia. No se probó en un teléfono físico ni se volvió a comprobar la biblioteca de la app antigua `io.luma.reader` en esta ejecución. La copia continúa al cerrar el menú y navegar dentro de Nova Reader, pero no es una tarea persistente que sobreviva al cierre forzado de la app.
