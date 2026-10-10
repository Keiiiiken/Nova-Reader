# Validación de Nova Reader 1.9.4

Fecha: 10 de octubre de 2026. Versión 1.9.4 (16), paquete `io.nova.reader`. Código: `6cf1d3fd43e923a06834b0f5f7dbd544e7b99e46`.

## Cambios

- Ajustes separados en Estilo, Biblioteca y Herramientas, con navegación fija y cierre visible. Vistas previas del tema y de dos/tres columnas; los cuatro acentos quedan visibles. El control para incluir libros de estantes también está disponible en Ajustes → Biblioteca.
- Pestañas coherentes en los ajustes de lectura, controles en superficies claras y oscuras, y contraste de los iconos del sistema acorde al tema elegido dentro de los paneles.
- Biblioteca con tarjeta destacada de lectura actual, portadas geométricas, progreso visible y opciones sobre la portada para dar espacio al título y al autor. Estantes muestran su color elegido; notas usan tarjetas con citas.
- Cabeceras con cierre en diccionario y herramientas del lector. Las opciones de libro permiten desplazarse cuando no caben.
- La comprobación del APK normal espera a que Android produzca una captura válida de accesibilidad, manteniendo su plazo y las aserciones. Descarta el archivo anterior para evitar validar una pantalla antigua.

## Pruebas y capturas

[GitHub Actions](https://github.com/Keiiiiken/Nova-Reader/actions/runs/38083944217): resultado completo **success**.

| Comprobación | Resultado |
|---|---|
| Pruebas debug en Android 15 | 35 aprobadas; 0 fallos, errores u omisiones |
| Pruebas release con R8 en Android 15 | 12 aprobadas; 0 fallos, errores u omisiones |
| APK normal de producción | Migración, pulsación larga, lectura EPUB, herramientas, Ajustes, Copias y aviso de Drive aprobados |
| Revisión visual | Biblioteca, estantes, notas y Ajustes en claro/oscuro; corregidos el recorte de iniciales pequeñas y el contraste de las barras del sistema |
| Compilación y lint de producción | Aprobados |
| Instalación, reinstalación y arranque del APK firmado | Aprobados en Android 15 |
| Certificado y contenido del APK | Firma permanente verificada; contenido coincide con la producción compilada, excluyendo metadatos de firma |

Capturas obtenidas en el emulador, sin editar:

- [Estilo, APK normal de producción](capturas-1.9.4/ajustes-estilo.png).
- [Biblioteca en modo oscuro, prueba debug](capturas-1.9.4/ajustes-biblioteca.png).
- [Herramientas, prueba debug](capturas-1.9.4/ajustes-herramientas.png).

## APK publicado

[Release 1.9.4](https://github.com/Keiiiiken/Nova-Reader/releases/tag/v1.9.4-build.25).

- Archivo: `NovaReader-1.9.4.apk`.
- SHA-256: `febd9c9b1516233aada795c46a57f64d3024c7f3d027f2828be5a369c699c19d`.
- SHA-1 del certificado: `61:E8:F8:A7:C3:AB:79:69:E7:76:6D:19:D7:33:2D:43:1F:D5:E8:FA`.

## Alcance

No se probó en un teléfono físico ni con una cuenta real de Google. No se volvió a comprobar la biblioteca de la app antigua `io.luma.reader` en esta ejecución. Las pruebas de Drive siguen usando HTTPS simulado. Esta versión mejora la interfaz; las copias siguen siendo manuales y no sobreviven al cierre forzado del proceso. La [revisión de pendientes](PRIORIDADES-APP.md) detalla fiabilidad, organización, accesibilidad, narración y entrada de archivos. No se cambiaron la clave de firma, los secretos ni el cliente OAuth Android.
