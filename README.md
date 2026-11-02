# SparkVision Stock 🇨🇴

Calculadora visual inteligente y escáner de productos de alta precisión para tiendas de barrio en Colombia y Latinoamérica.

## 🚀 Características
- **100% On-Device ($0 Costo):** Sin servidores externos ni APIs de pago. Corre íntegramente sobre el procesador del dispositivo móvil.
- **TensorFlow Lite:** Extracción de huellas dactilares visuales de 128 dimensiones (color espacial HSV, texturas locales LBP y gradientes HOG).
- **Mapeo Guiado de 20 Ángulos:** Captura 20 fotos en 40 segundos para calcular el centroide del producto y evitar falsos positivos por viento o sombras.
- **Antirrebote Estricto:** Evita cobros duplicados accidentales del mismo producto hasta que pase un artículo diferente.
- **Formateo COP:** Visualización nativa de pesos colombianos con separador de miles con punto (`$ 2.500`, `$ 10.000`).
- **Retroalimentación POS:** Sonido agudo "BEEP" de supermercado sincronizado con vibración háptica.

## 📦 Compilación y Descarga del APK (GitHub Releases)
Este repositorio cuenta con un flujo automatizado en GitHub Actions (`.github/workflows/release-apk.yml`) que compila y publica los archivos `.apk` directamente en la pestaña **Releases** de este repositorio:
1. Ve a la pestaña **Actions** en GitHub.
2. Selecciona **Build & Publish APK to GitHub Releases**.
3. Haz clic en **Run workflow** para compilar la última versión.
4. Al finalizar (2-3 minutos), descarga el APK directamente desde **Releases**.
