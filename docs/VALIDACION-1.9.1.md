# Validación de Nova Reader 1.9.1

Paquete io.nova.reader, versión 1.9.1 (13). Firma permanente nueva RSA-4096, con SHA-1 61:E8:F8:A7:C3:AB:79:69:E7:76:6D:19:D7:33:2D:43:1F:D5:E8:FA. Se instala en paralelo con io.luma.reader, conforme a la elección del propietario.

La compilación local assembleRelease y lintRelease terminó correctamente en 4 min 56 s. Se ejecutó el firmador real, se verificaron las firmas v2 y v3 para Android 8.0 o posterior, el certificado, identificador, versión, actividad principal y alineación de 16 KiB. APK firmado: SHA-256 f268e4f13800d4ed6d641629294a2a84a4c302df2facd277a562ec03d0e271b2.

La prueba de instalación en Android estándar y la pasada de regresión están pendientes al preparar este documento. El APK no se da por comprobado en un teléfono físico. La prueba de coexistencia verifica que la app original mantiene un archivo privado al instalar y reinstalar la nueva; no copia automáticamente su biblioteca.

La conexión real con Google Drive requiere la configuración de Google Cloud por el propietario y sigue pendiente. Las pruebas de contrato HTTPS simulado no sustituyen una autorización con una cuenta real.
