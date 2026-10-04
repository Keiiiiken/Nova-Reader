# Compilación y actualización

GitHub Actions compila `assembleRelease`, ejecuta `lintRelease` y `testReleaseUnitTest`, adjunta informes y publica un release de cada push a main. Esto verifica compilación y pruebas unitarias; no sustituye pruebas del lector en dispositivos reales.

Sin clave de firma, publica un prerelease con APK `unsigned`, que no se puede instalar. La app principal conserva `io.luma.reader`, nombre Nova Reader y versión 1.9.0 (12). No usa el sufijo de la variante preview.

Android solo permite actualizar LumaReader 1.7.0 conservando sus datos cuando el certificado coincide. El certificado público de esa versión es SHA-256 `a25061d76d480e6e68eeb75d2fd823eb3657e6d8c50793ffcd090f6a4e1f8727`. El archivo de clave privada original no está entre los archivos del proyecto recibidos. No desinstalar la app anterior: sus datos locales podrían perderse.

Para firmar, configurar en GitHub Settings → Secrets and variables → Actions estos secretos (no subirlos al repositorio):

- `ANDROID_KEYSTORE_BASE64`: contenido del keystore original codificado en base64.
- `ANDROID_KEYSTORE_PASSWORD`: contraseña del almacén.
- `ANDROID_KEY_ALIAS`: alias de la clave.
- `ANDROID_KEY_PASSWORD`: contraseña de la clave.

El workflow verifica la firma contra el certificado original antes de publicar un APK instalable. Si la versión antigua fue compilada en modo debug, la clave suele estar en `.android/debug.keystore` en el ordenador donde se compiló; hay que confirmar su certificado. No compartir contraseñas por chat ni generar otra clave para actualizar instalaciones existentes.

La variante opcional `assemblePreview` permanece para probar en paralelo y tiene biblioteca independiente. No es la versión principal ni puede actualizarla.
