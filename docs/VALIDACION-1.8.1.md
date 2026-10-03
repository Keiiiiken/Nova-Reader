# Validación 1.8.1

- Compilación local: `assembleRelease assembleDebug assembleDebugAndroidTest lintRelease testReleaseUnitTest` correcta. La tarea de pruebas unitarias informa NO-SOURCE: el proyecto no tiene pruebas unitarias locales que ejecutar.
- Lint release: 0 errores, 3 avisos. Compilación final de debug y release después de restaurar el icono: correcta.
- Emulador Android 8: `LibraryDesignTest` pasó sus 3 pruebas. Verifican biblioteca vacía, filtros, búsqueda, notas, estantes y equivalencia entre pulsación larga y botón de opciones; también pulsación larga en Continuar leyendo y estantes.
- APK principal: `io.luma.reader`, versión 1.8.1, código 11, etiqueta Nova Reader. Icono original comprobado en los recursos del APK. Release local 10,42 MiB, sin firma.
- Captura real de opciones obtenida del emulador con libros ficticios.
- La clave original no está en el proyecto. La actualización de la app antigua exige esa clave; no desinstalar la app anterior para forzar la instalación.
- El emulador local no tiene un proveedor WebView autorizado. Estas pruebas validan las pantallas de biblioteca; no constituyen validación completa del lector, TTS o paginación. Es necesaria una revisión en dispositivos con WebView funcional.
- GitHub Actions: SDK corregido para evitar el paquete obsoleto `tools`. El workflow compila, analiza release, adjunta artefactos y publica el resultado. El estado y logs públicos para el propietario están en la pestaña Actions del repositorio privado.
