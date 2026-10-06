# Instalación y firma de Nova Reader 1.9.1

La versión principal se llama **Nova Reader**, usa `io.nova.reader`, versión 1.9.1 (13), y conserva el icono. Se instala junto a la app anterior `io.luma.reader`, según la elección del propietario. Cada app tiene su biblioteca privada: instalar la nueva no copia automáticamente libros ni progreso de la anterior.

El APK de entrega está firmado con una nueva clave permanente RSA de 4096 bits. Las futuras versiones de `io.nova.reader` deben usar esta misma clave para actualizarse. La clave privada y las contraseñas se entregan en una copia privada y no se suben al repositorio. El certificado público está en `signing/release-certificate.json`.

## Firmar automáticamente en GitHub

La conexión actual puede publicar releases, pero la API de secretos responde 403. El propietario debe realizar este paso una sola vez:

1. Descargar y guardar la copia privada de firma entregada por chat.
2. Abrir https://github.com/Keiiiiken/Nova-Reader/settings/secrets/actions/new
3. En **Name**, escribir `NOVA_SIGNING_BUNDLE`.
4. En **Secret**, pegar el contenido completo de `NOVA_SIGNING_BUNDLE.txt`, incluido en esa copia privada, y guardar.
5. En Actions → Android build and release → Run workflow, iniciar una ejecución.

El secreto contiene el keystore y sus credenciales y se guarda cifrado en Actions. No pegarlo en issues, README, chats públicos ni commits. `scripts/sign_release.py` usa un almacén temporal, pasa las contraseñas por variables de entorno y borra el archivo al terminar. Verifica el certificado permanente, paquete y alineación del APK.

Sin el secreto, el workflow compila y prueba normalmente y conserva un artefacto unsigned de laboratorio; no publica ese APK como release instalable. Con el secreto, verifica también la instalación, reinstalación y arranque del APK firmado en un emulador antes de publicarlo.

## Comprobación de instalación en paralelo

El workflow Signed APK installation and coexistence descarga el APK firmado y la versión original 1.7.0 guardada como fixture privada de pruebas. Instala ambas en Android estándar, comprueba que `io.luma.reader` conserva un archivo privado, verifica el nuevo certificado, instala y reinstala `io.nova.reader`, y exige que abra su biblioteca. Los resultados y una captura se adjuntan como artefacto.

Las pruebas instrumentadas siguen ejecutándose en debug y en release con reglas de conservación exclusivas de `-PtestMinified`. La comprobación de interfaz posterior usa el APK normal con las reglas de distribución. La prueba de instalación usa el APK realmente firmado con la clave permanente.

## Instalar en el teléfono

Descargar **NovaReader-1.9.1.apk**, no un archivo `unsigned`, ZIP ni variante preview. Abrirlo y permitir la instalación desde el navegador o gestor de archivos cuando Android lo solicite. Requiere Android 8.0 o posterior. La app anterior permanece instalada; no hace falta desinstalarla.

Si Android aún rechaza el archivo, comprobar que se descargó completo y que el nombre termina en `.apk`. El SHA-256 del archivo entregado está en el informe de instalación. Un APK de una versión anterior descargado antes de crear esta firma no se corrige cambiándole el nombre: hay que descargar el nuevo.
