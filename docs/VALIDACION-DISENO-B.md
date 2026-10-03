# Validación del diseño B — Nova Reader 1.8.0

## Cambios
Biblioteca blanca con acento azul, filtros subrayados, búsqueda e importación visibles, continuación de lectura compacta y navegación fija. Estantes en lista y una sección que reúne subrayados y comentarios. Las miniportadas sin imagen usan una inicial; las portadas grandes conservan título y autor.

Los nuevos usuarios comienzan con tres columnas y acento azul. Las preferencias existentes se conservan. La variante preview usa io.luma.reader.preview y una biblioteca independiente.

## Comprobaciones
- Compilaciones debug, APK de pruebas y preview: aprobadas.
- Android Lint: sin errores, con 16 advertencias, principalmente de versiones de dependencias.
- Nueve pruebas instrumentadas en Android 8 (API 26): ocho aprobadas.
- Las dos pruebas de diseño se repitieron después del ajuste final de miniportadas: ambas aprobadas.
- Cobertura aprobada: filtros, búsqueda, navegación, notas, estantes, ajustes, copia de portadas, EPUB 2/3 y normalización de texto.
- La prueba de lectura vertical falló al crear WebView porque el emulador no dispone de un proveedor WebView permitido. La imagen no permite seleccionar el paquete instalado. El lector requiere validación adicional en un dispositivo con WebView operativo.

Las capturas usan libros y notas ficticios sin imágenes de portada. No se ha validado esta versión en un teléfono físico ni en Android reciente.

## Instalación de prueba
Generar con ./gradlew assemblePreview. El APK puede instalarse junto a la app original sin compartir ni reemplazar su biblioteca. Una actualización de la app original necesita su misma clave de firma.
