# Validación de Nova Reader 1.9.5

Fecha: 10 de octubre de 2026. Versión 1.9.5 (17), paquete `io.nova.reader`. Código: `5ea6206459c8b52de21df587a4d0b13c61b23f6c`.

## Cambios

- Los ajustes dentro del lector abren a media pantalla. Un deslizamiento hacia arriba dentro de los controles amplía el panel antes de desplazar la lista, mediante el desplazamiento anidado de Material.
- Un botón en la cabecera permite ampliar o reducir el panel. Cambiar entre Apariencia, Lectura y Voz conserva su altura; el cierre devuelve al libro.
- Cabecera, pestañas, muestras de temas, tarjetas y espaciado más compactos. «Usar generales» comparte la cabecera para dejar más espacio a los controles. Los botones de ampliar y cerrar conservan sus áreas táctiles de 48 dp.

## Pruebas y capturas

[GitHub Actions](https://github.com/Keiiiiken/Nova-Reader/actions/runs/38091176973): resultado completo **success**.

| Comprobación | Resultado |
|---|---|
| Pruebas debug en Android 15 | 35 aprobadas; 0 fallos, errores u omisiones |
| Pruebas release con R8 en Android 15 | 12 aprobadas; 0 fallos, errores u omisiones |
| Panel del lector | Apertura a media altura, expansión con gesto dentro de los controles, acceso a interlineado, cambio de pestaña, reducción, ampliación y cierre aprobados en debug y release |
| APK normal de producción | Migración, pulsación larga, lectura EPUB, herramientas, Ajustes y aviso de Drive aprobados |
| Revisión visual | Capturas a media altura y ampliada revisadas; cabecera y controles visibles |
| Compilación y lint de producción | Aprobados |
| Instalación, reinstalación y arranque del APK firmado | Aprobados en Android 15 |
| Certificado y contenido del APK | Firma permanente verificada; contenido coincide con la producción compilada, excluyendo metadatos de firma |

Capturas obtenidas en el emulador durante las pruebas release, sin editar:

- [Apertura a media pantalla](capturas-1.9.5/ajustes-lector-mitad.png).
- [Panel ampliado después del gesto](capturas-1.9.5/ajustes-lector-ampliados.png).

## APK publicado

[Release 1.9.5](https://github.com/Keiiiiken/Nova-Reader/releases/tag/v1.9.5-build.27).

- Archivo: `NovaReader-1.9.5.apk`.
- SHA-256: `a8020a06b956697fa2064c759f21f1c5c1948505c2f810246481f0df0202892b`.
- SHA-1 del certificado: `61:E8:F8:A7:C3:AB:79:69:E7:76:6D:19:D7:33:2D:43:1F:D5:E8:FA`.
- Instalar como actualización de Nova Reader sin desinstalarla.

## Alcance

No se probó en un teléfono físico ni con una cuenta real de Google. Las pruebas de Drive usan HTTPS simulado. No se volvió a comprobar la biblioteca de la app antigua `io.luma.reader` en esta ejecución. Esta versión cambia los ajustes del lector; las [prioridades pendientes](PRIORIDADES-APP.md) siguen documentadas por separado.
