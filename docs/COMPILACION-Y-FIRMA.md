# Compilación y actualización

GitHub Actions ejecuta primero la batería de pruebas instrumentadas en un emulador Android 15 estándar y repite las interacciones del lector sobre la variante release con R8. Después recompila el APK con las reglas normales de distribución y comprueba, desde la interfaz de Android, la migración de una biblioteca de ejemplo, la pulsación larga, el texto del EPUB y las herramientas del lector. Solo si pasan todas estas etapas, compila `assembleRelease`, ejecuta `lintRelease` y publica el release. Adjunta informes y capturas de la app. `testReleaseUnitTest` también se ejecuta, aunque actualmente no hay pruebas JVM en ese directorio; la validación funcional está en `androidTest`.

Las pruebas instrumentadas de release usan una firma debug temporal y conservan las APIs de datos y SDK que comparte el ejecutor con la app mediante `-PtestMinified`. Esa conservación evita que las pruebas intenten llamar a métodos eliminados; el lector y su WebView siguen minificados. La comprobación posterior de la interfaz se hace sobre el APK compilado sin esa propiedad, firmado temporalmente con la clave del emulador. El APK publicado se recompila sin esas reglas y solo se firma con la clave original si los secretos están configurados. No sustituye pruebas en teléfonos físicos ni una conexión OAuth real con Drive.

Sin clave de firma, publica un prerelease con APK `unsigned`, que no se puede instalar. La app principal conserva `io.luma.reader`, nombre Nova Reader y versión 1.9.0 (12). No usa el sufijo de la variante preview.

Android solo permite actualizar LumaReader 1.7.0 conservando sus datos cuando el certificado coincide. El certificado público de esa versión es SHA-256 `a25061d76d480e6e68eeb75d2fd823eb3657e6d8c50793ffcd090f6a4e1f8727`. El archivo de clave privada original no está entre los archivos del proyecto recibidos. No desinstalar la app anterior: sus datos locales podrían perderse.

Para firmar, configurar en GitHub Settings → Secrets and variables → Actions estos secretos (no subirlos al repositorio):

- `ANDROID_KEYSTORE_BASE64`: contenido del keystore original codificado en base64.
- `ANDROID_KEYSTORE_PASSWORD`: contraseña del almacén.
- `ANDROID_KEY_ALIAS`: alias de la clave.
- `ANDROID_KEY_PASSWORD`: contraseña de la clave.

El workflow verifica la firma contra el certificado original antes de publicar un APK instalable. Si la versión antigua fue compilada en modo debug, la clave suele estar en `.android/debug.keystore` en el ordenador donde se compiló; hay que confirmar su certificado. No compartir contraseñas por chat ni generar otra clave para actualizar instalaciones existentes.

La variante opcional `assemblePreview` permanece para probar en paralelo y tiene biblioteca independiente. No es la versión principal ni puede actualizarla.
